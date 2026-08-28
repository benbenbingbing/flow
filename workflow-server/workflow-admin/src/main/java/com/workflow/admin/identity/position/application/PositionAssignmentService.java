package com.workflow.admin.identity.position.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.identity.position.api.PositionErrorCode;
import com.workflow.admin.identity.position.api.PositionManagementException;
import com.workflow.admin.identity.position.api.request.PositionRequests;
import com.workflow.admin.identity.position.api.response.PositionViews;
import com.workflow.admin.identity.position.infrastructure.persistence.mapper.SysPositionAssignmentBatchMapper;
import com.workflow.admin.identity.position.infrastructure.persistence.mapper.SysPositionAssignmentMapper;
import com.workflow.admin.identity.position.infrastructure.persistence.mapper.SysPositionMapper;
import com.workflow.admin.identity.position.infrastructure.persistence.record.PositionAssignmentViewRow;
import com.workflow.admin.identity.position.infrastructure.persistence.record.SysPosition;
import com.workflow.admin.identity.position.infrastructure.persistence.record.SysPositionAssignment;
import com.workflow.admin.identity.position.infrastructure.persistence.record.SysPositionAssignmentBatch;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.admin.organization.infrastructure.persistence.record.SysOrganization;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditRiskLevel;
import com.workflow.contracts.audit.SystemAudit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 组织任职的唯一写入口。
 *
 * <p>所有写操作遵循“职务 ID、组织 ID、用户 ID”的固定锁顺序，随后查询
 * 区间重叠并写入。首条任职也会锁定稳定存在的职务和组织行，因此在
 * READ_COMMITTED 下不依赖空结果间隙锁。</p>
 */
@Service
@RequiredArgsConstructor
public class PositionAssignmentService {

    public static final String UNIT_LEADER = "UNIT_LEADER";
    private static final int MAX_BATCH_SIZE = 200;

    private final SysPositionMapper positionMapper;
    private final SysPositionAssignmentMapper assignmentMapper;
    private final SysPositionAssignmentBatchMapper batchMapper;
    private final SysOrganizationMapper organizationMapper;
    private final SysUserMapper userMapper;
    private final PositionOrganizationScopeService scopeService;
    private final ObjectMapper objectMapper;

    /**
     * 使用正式提交同一校验器执行只读预检；结果只具提示意义，提交仍会
     * 在加锁后重新验证全部条件。
     */
    @Transactional(readOnly = true)
    public PositionViews.PrecheckResult precheck(
            PositionRequests.AssignmentBatch request) {
        PreparedBatch prepared = prepare(request);
        Map<String, SysPosition> positions = mapPositions(
                positionMapper.selectByCodes(prepared.positionCodes()));
        List<PositionViews.PrecheckItem> results = new ArrayList<>();
        List<PreparedItem> accepted = new ArrayList<>();
        for (PreparedItem item : prepared.items()) {
            try {
                scopeService.requireVisible(item.organizationUnitId());
                SysPosition position = requirePosition(
                        positions, item.positionCode());
                SysOrganization organization = organizationMapper.selectById(
                        item.organizationUnitId());
                SysUser user = userMapper.selectById(item.userId());
                validateTarget(position, organization, user, item);
                validatePairwise(position, accepted, item);
                validateDatabaseOverlap(position, item, null, false,
                        prepared.reason());
                accepted.add(item);
                results.add(new PositionViews.PrecheckItem(
                        item.index(), true, null, "预检通过"));
            } catch (PositionManagementException exception) {
                results.add(new PositionViews.PrecheckItem(
                        item.index(), false, exception.errorCode().name(),
                        exception.getMessage()));
            }
        }
        return new PositionViews.PrecheckResult(
                results.stream().allMatch(PositionViews.PrecheckItem::valid),
                List.copyOf(results));
    }

    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.SYSTEM,
            action = AuditAction.CONFIGURE,
            operation = "批量任命或转任组织职务",
            risk = AuditRiskLevel.CRITICAL,
            required = true,
            targetType = "SYS_POSITION_ASSIGNMENT_BATCH",
            captureArguments = true,
            captureResult = true)
    public PositionViews.BatchAssignmentResult batchAssign(
            PositionRequests.AssignmentBatch request,
            String idempotencyKey) {
        PreparedBatch prepared = prepare(request);
        String normalizedKey = requireIdempotencyKey(idempotencyKey);
        String actorId = requireActorId();

        // 锁顺序是跨单笔、批量、转任和负责人快捷入口共享的并发契约。
        Map<String, SysPosition> positions = mapPositions(
                positionMapper.selectForUpdateByCodes(
                        prepared.positionCodes()));
        Map<String, SysOrganization> organizations = mapOrganizations(
                organizationMapper.selectForUpdateByIds(
                        prepared.organizationUnitIds()));
        List<String> lockedUserIds = new ArrayList<>(prepared.userIds());
        if (!lockedUserIds.contains(actorId)) {
            lockedUserIds.add(actorId);
        }
        lockedUserIds.sort(String::compareTo);
        Map<String, SysUser> users = mapUsers(
                userMapper.selectForUpdateByIds(lockedUserIds));
        if (!users.containsKey(actorId)) {
            throw forbidden("当前操作人不存在");
        }

        String requestHash = hash(prepared);
        SysPositionAssignmentBatch existing = batchMapper.selectByKey(
                actorId, normalizedKey);
        if (existing != null) {
            if (!requestHash.equals(existing.getRequestHash())) {
                throw conflict(
                        PositionErrorCode.IDEMPOTENCY_KEY_REUSED,
                        "Idempotency-Key 已用于不同批量请求");
            }
            return new PositionViews.BatchAssignmentResult(
                    normalizedKey, true,
                    readAssignmentIds(existing.getAssignmentIdsJson()));
        }

        List<PreparedItem> accepted = new ArrayList<>();
        for (PreparedItem item : prepared.items()) {
            // 数据范围在事务锁获取后再次校验，不能信任预检或锁前结果。
            scopeService.requireVisible(item.organizationUnitId());
            SysPosition position = requirePosition(
                    positions, item.positionCode());
            SysOrganization organization = organizations.get(
                    item.organizationUnitId());
            SysUser user = users.get(item.userId());
            validateTarget(position, organization, user, item);
            validatePairwise(position, accepted, item);
            accepted.add(item);
        }

        List<String> assignmentIds = new ArrayList<>();
        Set<String> leaderUnits = new LinkedHashSet<>();
        for (PreparedItem item : prepared.items()) {
            SysPosition position = positions.get(item.positionCode());
            validateDatabaseOverlap(
                    position, item, null, true, prepared.reason());
            SysPositionAssignment assignment = insertAssignment(
                    position, item, actorId);
            assignmentIds.add(assignment.getId());
            if (UNIT_LEADER.equals(position.getPositionCode())) {
                leaderUnits.add(item.organizationUnitId());
            }
        }
        LocalDateTime now = utcNow();
        leaderUnits.forEach(unitId -> refreshLeaderProjection(unitId, now));

        SysPositionAssignmentBatch batch = new SysPositionAssignmentBatch();
        batch.setIdempotencyKey(normalizedKey);
        batch.setRequestHash(requestHash);
        batch.setAssignmentIdsJson(writeAssignmentIds(assignmentIds));
        batch.setCreatedBy(actorId);
        batch.setCreateTime(now);
        batchMapper.insert(batch);
        return new PositionViews.BatchAssignmentResult(
                normalizedKey, false, List.copyOf(assignmentIds));
    }

    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.SYSTEM,
            action = AuditAction.DELETE,
            operation = "撤销组织职务任职",
            risk = AuditRiskLevel.CRITICAL,
            required = true,
            targetType = "SYS_POSITION_ASSIGNMENT",
            targetIdArg = 0,
            captureArguments = true)
    public void revoke(
            String assignmentId,
            PositionRequests.RevokeAssignment request) {
        if (request == null) {
            throw invalid(PositionErrorCode.BATCH_ASSIGNMENT_INVALID,
                    "请求不能为空");
        }
        String reason = requiredReason(request.reason());
        SysPositionAssignment snapshot = assignmentMapper.selectById(assignmentId);
        if (snapshot == null) {
            throw assignmentNotFound();
        }
        SysPosition position = positionMapper.selectForUpdate(
                snapshot.getPositionId());
        SysOrganization organization = organizationMapper.selectForUpdate(
                snapshot.getOrganizationUnitId());
        SysPositionAssignment current = assignmentMapper.selectForUpdate(
                assignmentId);
        if (position == null || organization == null || current == null) {
            throw assignmentNotFound();
        }
        scopeService.requireVisible(current.getOrganizationUnitId());
        requireAssignmentRevision(request.revision(), current);
        if (current.getRevokedAt() != null) {
            throw conflict(
                    PositionErrorCode.ASSIGNMENT_REVISION_CONFLICT,
                    "任职已撤销，请刷新后重试");
        }
        LocalDateTime now = utcNow();
        LocalDateTime revokedAt = now.isBefore(current.getEffectiveFrom())
                ? current.getEffectiveFrom() : now;
        LocalDateTime effectiveTo = current.getEffectiveTo();
        if (current.getEffectiveFrom().isBefore(now)
                && (effectiveTo == null || effectiveTo.isAfter(now))) {
            effectiveTo = now;
        }
        int changed = assignmentMapper.revoke(
                current.getId(), revokedAt, effectiveTo, reason,
                requireActorId(), current.getRevision());
        requireAssignmentChanged(changed);
        if (UNIT_LEADER.equals(position.getPositionCode())) {
            refreshLeaderProjection(current.getOrganizationUnitId(), now);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.SYSTEM,
            action = AuditAction.UPDATE,
            operation = "调整组织职务任职有效期",
            risk = AuditRiskLevel.CRITICAL,
            required = true,
            targetType = "SYS_POSITION_ASSIGNMENT",
            targetIdArg = 0,
            captureArguments = true)
    public void updatePeriod(
            String assignmentId,
            PositionRequests.UpdateAssignmentPeriod request) {
        if (request == null) {
            throw invalid(PositionErrorCode.ASSIGNMENT_PERIOD_INVALID,
                    "请求不能为空");
        }
        requiredReason(request.reason());
        SysPositionAssignment snapshot = assignmentMapper.selectById(assignmentId);
        if (snapshot == null) {
            throw assignmentNotFound();
        }
        SysPosition position = positionMapper.selectForUpdate(
                snapshot.getPositionId());
        SysOrganization organization = organizationMapper.selectForUpdate(
                snapshot.getOrganizationUnitId());
        SysPositionAssignment current = assignmentMapper.selectForUpdate(
                assignmentId);
        if (position == null || organization == null || current == null) {
            throw assignmentNotFound();
        }
        scopeService.requireVisible(current.getOrganizationUnitId());
        requireAssignmentRevision(request.revision(), current);
        if (current.getRevokedAt() != null) {
            throw conflict(
                    PositionErrorCode.ASSIGNMENT_REVISION_CONFLICT,
                    "已撤销任职不可调整有效期");
        }
        LocalDateTime from = request.effectiveFrom() == null
                ? current.getEffectiveFrom() : utc(request.effectiveFrom());
        LocalDateTime to = request.effectiveTo() == null
                ? null : utc(request.effectiveTo());
        validatePeriod(from, to);
        LocalDateTime now = utcNow();
        if (!current.getEffectiveFrom().isAfter(now)
                && !current.getEffectiveFrom().equals(from)) {
            throw invalid(
                    PositionErrorCode.ASSIGNMENT_PERIOD_INVALID,
                    "已生效任职不能修改开始时间");
        }
        if (current.getEffectiveTo() != null
                && !current.getEffectiveTo().isAfter(now)) {
            throw invalid(
                    PositionErrorCode.ASSIGNMENT_PERIOD_INVALID,
                    "已结束任职不能修改历史有效期");
        }
        if (to != null && !to.isAfter(now)) {
            throw invalid(
                    PositionErrorCode.ASSIGNMENT_PERIOD_INVALID,
                    "新的结束时间必须晚于当前时间");
        }
        PreparedItem changedItem = new PreparedItem(
                0, position.getPositionCode(), current.getOrganizationUnitId(),
                current.getUserId(), from, to,
                Boolean.TRUE.equals(current.getIsPrimary()),
                current.getSortOrder() == null ? 0 : current.getSortOrder(),
                false);
        validateDatabaseOverlap(
                position, changedItem, current.getId(), false,
                request.reason());
        requireAssignmentChanged(assignmentMapper.updatePeriod(
                current.getId(), from, to, requireActorId(),
                current.getRevision()));
        if (UNIT_LEADER.equals(position.getPositionCode())) {
            refreshLeaderProjection(current.getOrganizationUnitId(), now);
        }
    }

    /**
     * 清空组织负责人等价于撤销当前有效 UNIT_LEADER 任职，旧 leader 字段
     * 仅由本方法的投影刷新，不再作为权威写入口。
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.SYSTEM,
            action = AuditAction.DELETE,
            operation = "清空组织负责人",
            risk = AuditRiskLevel.CRITICAL,
            required = true,
            targetType = "SYS_ORGANIZATION_LEADER",
            targetIdArg = 0,
            captureArguments = true)
    public void clearCurrentLeader(String unitId, String reason) {
        String normalizedReason = requiredReason(reason);
        scopeService.requireVisible(unitId);
        SysPosition position = positionMapper.selectByCode(UNIT_LEADER);
        if (position == null) {
            throw new PositionManagementException(
                    404, PositionErrorCode.POSITION_NOT_FOUND,
                    "内置负责人职务不存在");
        }
        position = positionMapper.selectForUpdate(position.getId());
        SysOrganization organization = organizationMapper.selectForUpdate(unitId);
        if (organization == null) {
            throw new PositionManagementException(
                    404, PositionErrorCode.ORGANIZATION_UNIT_NOT_FOUND,
                    "组织节点不存在");
        }
        LocalDateTime now = utcNow();
        for (PositionAssignmentViewRow row : assignmentMapper.selectEffectiveRows(
                UNIT_LEADER, unitId, now)) {
            SysPositionAssignment assignment = assignmentMapper.selectForUpdate(
                    row.getId());
            LocalDateTime revokedAt = now.isBefore(assignment.getEffectiveFrom())
                    ? assignment.getEffectiveFrom() : now;
            LocalDateTime effectiveTo = assignment.getEffectiveFrom().isBefore(now)
                    ? now : assignment.getEffectiveTo();
            requireAssignmentChanged(assignmentMapper.revoke(
                    assignment.getId(), revokedAt, effectiveTo,
                    normalizedReason, requireActorId(), assignment.getRevision()));
        }
        refreshLeaderProjection(unitId, now);
    }

    /** 根据权威的当前 UNIT_LEADER 任职单向刷新旧负责人字段。 */
    public void refreshLeaderProjection(String unitId, LocalDateTime asOf) {
        List<PositionAssignmentViewRow> holders =
                assignmentMapper.selectEffectiveRows(UNIT_LEADER, unitId, asOf);
        if (holders.isEmpty()) {
            organizationMapper.updateLeaderProjection(unitId, null, null);
            return;
        }
        PositionAssignmentViewRow leader = holders.get(0);
        String displayName = StringUtils.hasText(leader.getNickname())
                ? leader.getNickname() : leader.getUsername();
        organizationMapper.updateLeaderProjection(
                unitId, leader.getUserId(), displayName);
    }

    private void validateDatabaseOverlap(
            SysPosition position,
            PreparedItem item,
            String excludeAssignmentId,
            boolean applyReplacement,
            String reason) {
        List<SysPositionAssignment> overlaps = assignmentMapper.selectOverlaps(
                position.getId(), item.organizationUnitId(),
                item.effectiveFrom(), item.effectiveTo(), excludeAssignmentId);
        SysPositionAssignment sameUser = overlaps.stream()
                .filter(value -> item.userId().equals(value.getUserId()))
                .findFirst().orElse(null);
        if (sameUser != null) {
            throw conflict(
                    PositionErrorCode.ASSIGNMENT_PERIOD_OVERLAPPED,
                    "同一用户已有重叠任职区间");
        }
        if (SysPosition.HolderMode.SINGLE.name().equals(position.getHolderMode())) {
            if (!overlaps.isEmpty() && !item.replaceExisting()) {
                throw conflict(
                        PositionErrorCode.SINGLE_POSITION_OCCUPIED,
                        "单人职务已有重叠任职人，必须显式选择转任");
            }
            if (applyReplacement && !overlaps.isEmpty()) {
                closeReplacedAssignments(overlaps, item.effectiveFrom(), reason);
            }
            return;
        }
        if (item.primary() && overlaps.stream()
                .anyMatch(value -> Boolean.TRUE.equals(value.getIsPrimary()))) {
            throw conflict(
                    PositionErrorCode.PRIMARY_HOLDER_CONFLICT,
                    "多人职务在同一时点只能有一名主职人员");
        }
    }

    /**
     * 转任时，已开始事实缩短到新任职开始；尚未开始的预约事实使用撤销
     * 标记保留。这样不会生成 effectiveTo <= effectiveFrom 的非法区间。
     */
    private void closeReplacedAssignments(
            List<SysPositionAssignment> overlaps,
            LocalDateTime replacementFrom,
            String reason) {
        String actor = requireActorId();
        for (SysPositionAssignment existing : overlaps) {
            int changed;
            if (existing.getEffectiveFrom().isBefore(replacementFrom)) {
                changed = assignmentMapper.closeAt(
                        existing.getId(), replacementFrom, actor,
                        existing.getRevision());
            } else {
                changed = assignmentMapper.revoke(
                        existing.getId(), existing.getEffectiveFrom(),
                        existing.getEffectiveTo(), "转任：" + reason,
                        actor, existing.getRevision());
            }
            requireAssignmentChanged(changed);
        }
    }

    private SysPositionAssignment insertAssignment(
            SysPosition position,
            PreparedItem item,
            String actor) {
        LocalDateTime now = utcNow();
        SysPositionAssignment assignment = new SysPositionAssignment();
        assignment.setPositionId(position.getId());
        assignment.setOrganizationUnitId(item.organizationUnitId());
        assignment.setUserId(item.userId());
        assignment.setIsPrimary(item.primary());
        assignment.setSortOrder(item.sortOrder());
        assignment.setEffectiveFrom(item.effectiveFrom());
        assignment.setEffectiveTo(item.effectiveTo());
        assignment.setRevision(1);
        assignment.setCreatedBy(actor);
        assignment.setUpdatedBy(actor);
        assignment.setCreateTime(now);
        assignment.setUpdateTime(now);
        assignmentMapper.insert(assignment);
        return assignment;
    }

    private void validatePairwise(
            SysPosition position,
            List<PreparedItem> accepted,
            PreparedItem candidate) {
        for (PreparedItem other : accepted) {
            if (!candidate.positionCode().equals(other.positionCode())
                    || !candidate.organizationUnitId().equals(
                            other.organizationUnitId())
                    || !overlaps(candidate, other)) {
                continue;
            }
            if (candidate.userId().equals(other.userId())) {
                throw conflict(
                        PositionErrorCode.ASSIGNMENT_PERIOD_OVERLAPPED,
                        "批量请求中同一用户存在重叠任职区间");
            }
            if (SysPosition.HolderMode.SINGLE.name().equals(position.getHolderMode())) {
                throw conflict(
                        PositionErrorCode.SINGLE_POSITION_OCCUPIED,
                        "批量请求中单人职务存在重叠任职人");
            }
            if (candidate.primary() && other.primary()) {
                throw conflict(
                        PositionErrorCode.PRIMARY_HOLDER_CONFLICT,
                        "批量请求中多人职务存在重叠主职");
            }
        }
    }

    private boolean overlaps(PreparedItem first, PreparedItem second) {
        return (second.effectiveTo() == null
                || first.effectiveFrom().isBefore(second.effectiveTo()))
                && (first.effectiveTo() == null
                    || second.effectiveFrom().isBefore(first.effectiveTo()));
    }

    private void validateTarget(
            SysPosition position,
            SysOrganization organization,
            SysUser user,
            PreparedItem item) {
        if (!SysPosition.Status.ENABLED.name().equals(position.getStatus())) {
            throw conflict(PositionErrorCode.POSITION_STATUS_INVALID,
                    "职务已停用");
        }
        if (organization == null) {
            throw new PositionManagementException(
                    404, PositionErrorCode.ORGANIZATION_UNIT_NOT_FOUND,
                    "组织节点不存在");
        }
        if (!SysOrganization.Status.ENABLED.getValue().equals(
                organization.getStatus())) {
            throw conflict(PositionErrorCode.ORGANIZATION_UNIT_DISABLED,
                    "组织节点已禁用");
        }
        String organizationType = organization.getType().toUpperCase(Locale.ROOT);
        if (!SysPosition.ApplicableUnitType.ANY.name().equals(
                position.getApplicableUnitType())
                && !position.getApplicableUnitType().equals(organizationType)) {
            throw conflict(
                    PositionErrorCode.POSITION_UNIT_TYPE_MISMATCH,
                    "职务不适用于目标组织节点类型");
        }
        if (user == null) {
            throw new PositionManagementException(
                    404, PositionErrorCode.USER_NOT_FOUND, "任职用户不存在");
        }
        if (!SysUser.Status.ENABLED.getValue().equals(user.getStatus())) {
            throw conflict(PositionErrorCode.USER_DISABLED, "任职用户已禁用");
        }
        requireUserWithinTargetUnit(user, organization);
        validatePeriod(item.effectiveFrom(), item.effectiveTo());
        if (SysPosition.HolderMode.SINGLE.name().equals(position.getHolderMode())
                && !item.primary()) {
            throw invalid(
                    PositionErrorCode.BATCH_ASSIGNMENT_INVALID,
                    "单人职务必须标记为主职");
        }
    }

    /**
     * 保守 V1 不开放跨单位兼任：用户的部门（无部门时使用组织）必须位于
     * 目标单位子树内。该规则与管理员自身数据范围同时生效，防止仅凭功能
     * 权限把范围外用户任命到本范围单位。
     */
    private void requireUserWithinTargetUnit(
            SysUser user,
            SysOrganization target) {
        String currentId = StringUtils.hasText(user.getDeptId())
                ? user.getDeptId() : user.getOrgId();
        if (!StringUtils.hasText(currentId)) {
            throw forbidden("任职用户未配置组织或部门归属");
        }
        Set<String> visited = new LinkedHashSet<>();
        for (int depth = 0; depth < 32; depth++) {
            if (!visited.add(currentId)) {
                throw conflict(
                        PositionErrorCode.ORGANIZATION_HIERARCHY_INVALID,
                        "任职用户组织父链存在环");
            }
            if (target.getId().equals(currentId)) {
                return;
            }
            SysOrganization current = organizationMapper.selectById(currentId);
            if (current == null) {
                throw conflict(
                        PositionErrorCode.ORGANIZATION_HIERARCHY_INVALID,
                        "任职用户组织父链存在悬空节点");
            }
            String parentId = current.getParentId();
            if (!StringUtils.hasText(parentId) || "0".equals(parentId)) {
                break;
            }
            currentId = parentId;
        }
        throw forbidden("任职用户不属于目标组织节点子树");
    }

    private PreparedBatch prepare(PositionRequests.AssignmentBatch request) {
        if (request == null || request.items() == null
                || request.items().isEmpty()) {
            throw invalid(PositionErrorCode.BATCH_ASSIGNMENT_INVALID,
                    "至少需要一条任职记录");
        }
        if (request.items().size() > MAX_BATCH_SIZE) {
            throw invalid(PositionErrorCode.BATCH_ASSIGNMENT_INVALID,
                    "单次最多处理 200 条任职记录");
        }
        if (Boolean.FALSE.equals(request.atomic())) {
            throw invalid(PositionErrorCode.BATCH_ASSIGNMENT_INVALID,
                    "V1 批量任命只支持全量原子提交");
        }
        String reason = requiredReason(request.reason());
        List<PreparedItem> items = new ArrayList<>();
        for (int index = 0; index < request.items().size(); index++) {
            PositionRequests.AssignmentItem source = request.items().get(index);
            if (source == null) {
                throw invalid(PositionErrorCode.BATCH_ASSIGNMENT_INVALID,
                        "第 " + index + " 条任职不能为空");
            }
            String positionCode = requiredText(
                    source.positionCode(), "职务编码")
                    .toUpperCase(Locale.ROOT);
            LocalDateTime from = source.effectiveFrom() == null
                    ? null : utc(source.effectiveFrom());
            LocalDateTime to = source.effectiveTo() == null
                    ? null : utc(source.effectiveTo());
            validatePeriod(from, to);
            items.add(new PreparedItem(
                    index,
                    positionCode,
                    requiredText(source.organizationUnitId(), "组织节点"),
                    requiredText(source.userId(), "任职用户"),
                    from,
                    to,
                    Boolean.TRUE.equals(source.isPrimary()),
                    nonNegative(source.sortOrder()),
                    Boolean.TRUE.equals(source.replaceExisting())));
        }
        List<String> codes = items.stream().map(PreparedItem::positionCode)
                .distinct().sorted().toList();
        List<String> units = items.stream().map(PreparedItem::organizationUnitId)
                .distinct().sorted().toList();
        List<String> users = items.stream().map(PreparedItem::userId)
                .distinct().sorted().toList();
        return new PreparedBatch(
                reason, List.copyOf(items), codes, units, users);
    }

    private Map<String, SysPosition> mapPositions(List<SysPosition> values) {
        Map<String, SysPosition> result = new HashMap<>();
        values.forEach(value -> result.put(value.getPositionCode(), value));
        return result;
    }

    private Map<String, SysOrganization> mapOrganizations(
            List<SysOrganization> values) {
        Map<String, SysOrganization> result = new HashMap<>();
        values.forEach(value -> result.put(value.getId(), value));
        return result;
    }

    private Map<String, SysUser> mapUsers(List<SysUser> values) {
        Map<String, SysUser> result = new HashMap<>();
        values.forEach(value -> result.put(value.getId(), value));
        return result;
    }

    private SysPosition requirePosition(
            Map<String, SysPosition> positions,
            String code) {
        SysPosition position = positions.get(code);
        if (position == null) {
            throw new PositionManagementException(
                    404, PositionErrorCode.POSITION_NOT_FOUND,
                    "职务不存在: " + code);
        }
        return position;
    }

    private void validatePeriod(LocalDateTime from, LocalDateTime to) {
        if (from == null || (to != null && !to.isAfter(from))) {
            throw invalid(
                    PositionErrorCode.ASSIGNMENT_PERIOD_INVALID,
                    "任职结束时间必须晚于开始时间");
        }
    }

    private int requireAssignmentRevision(
            Integer requested,
            SysPositionAssignment current) {
        if (requested == null || !requested.equals(current.getRevision())) {
            throw conflict(
                    PositionErrorCode.ASSIGNMENT_REVISION_CONFLICT,
                    "任职已被其他管理员修改，请刷新后重试");
        }
        return requested;
    }

    private void requireAssignmentChanged(int changed) {
        if (changed != 1) {
            throw conflict(
                    PositionErrorCode.ASSIGNMENT_REVISION_CONFLICT,
                    "任职已被其他管理员修改，请刷新后重试");
        }
    }

    private PositionManagementException assignmentNotFound() {
        return new PositionManagementException(
                404, PositionErrorCode.ASSIGNMENT_NOT_FOUND, "任职不存在");
    }

    private String requireIdempotencyKey(String value) {
        if (!StringUtils.hasText(value)) {
            throw invalid(PositionErrorCode.IDEMPOTENCY_KEY_REQUIRED,
                    "Idempotency-Key 不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > 128) {
            throw invalid(PositionErrorCode.IDEMPOTENCY_KEY_REQUIRED,
                    "Idempotency-Key 不能超过 128 个字符");
        }
        return normalized;
    }

    private String requiredReason(String value) {
        String reason = requiredText(value, "变更原因");
        if (reason.length() > 500) {
            throw invalid(PositionErrorCode.BATCH_ASSIGNMENT_INVALID,
                    "变更原因不能超过 500 个字符");
        }
        return reason;
    }

    private String requiredText(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw invalid(PositionErrorCode.BATCH_ASSIGNMENT_INVALID,
                    label + "不能为空");
        }
        return value.trim();
    }

    private int nonNegative(Integer value) {
        int normalized = value == null ? 0 : value;
        if (normalized < 0) {
            throw invalid(PositionErrorCode.BATCH_ASSIGNMENT_INVALID,
                    "排序不能小于 0");
        }
        return normalized;
    }

    private String requireActorId() {
        String actorId = UserContext.getUserId();
        if (!StringUtils.hasText(actorId)) {
            throw forbidden("用户未登录");
        }
        return actorId;
    }

    private String hash(PreparedBatch request) {
        StringBuilder canonical = new StringBuilder(request.reason());
        request.items().forEach(item -> canonical.append('|')
                .append(item.positionCode()).append('|')
                .append(item.organizationUnitId()).append('|')
                .append(item.userId()).append('|')
                .append(item.effectiveFrom()).append('|')
                .append(item.effectiveTo()).append('|')
                .append(item.primary()).append('|')
                .append(item.sortOrder()).append('|')
                .append(item.replaceExisting()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支持 SHA-256", exception);
        }
    }

    private String writeAssignmentIds(List<String> assignmentIds) {
        try {
            return objectMapper.writeValueAsString(assignmentIds);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法保存批量任命幂等结果", exception);
        }
    }

    private List<String> readAssignmentIds(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("批量任命幂等结果损坏", exception);
        }
    }

    private LocalDateTime utc(OffsetDateTime value) {
        return LocalDateTime.ofInstant(value.toInstant(), ZoneOffset.UTC);
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private PositionManagementException invalid(
            PositionErrorCode code,
            String message) {
        return new PositionManagementException(400, code, message);
    }

    private PositionManagementException conflict(
            PositionErrorCode code,
            String message) {
        return new PositionManagementException(409, code, message);
    }

    private PositionManagementException forbidden(String message) {
        return new PositionManagementException(
                403, PositionErrorCode.ORGANIZATION_SCOPE_FORBIDDEN, message);
    }

    private record PreparedBatch(
            String reason,
            List<PreparedItem> items,
            List<String> positionCodes,
            List<String> organizationUnitIds,
            List<String> userIds) {
    }

    private record PreparedItem(
            int index,
            String positionCode,
            String organizationUnitId,
            String userId,
            LocalDateTime effectiveFrom,
            LocalDateTime effectiveTo,
            boolean primary,
            int sortOrder,
            boolean replaceExisting) {
    }
}
