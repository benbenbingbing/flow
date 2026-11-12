package com.workflow.admin.identity.position.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.admin.identity.position.api.error.PositionErrorCode;
import com.workflow.admin.identity.position.api.error.PositionManagementException;
import com.workflow.admin.identity.position.api.request.PositionRequests;
import com.workflow.admin.identity.position.infrastructure.persistence.mapper.SysPositionAssignmentMapper;
import com.workflow.admin.identity.position.infrastructure.persistence.mapper.SysPositionMapper;
import com.workflow.admin.identity.position.infrastructure.persistence.record.SysPosition;
import com.workflow.admin.identity.position.infrastructure.persistence.record.SysPositionAssignment;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.admin.organization.infrastructure.persistence.record.SysOrganization;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.audit.annotation.SystemAudit;
import com.workflow.contracts.audit.model.AuditAction;
import com.workflow.contracts.audit.model.AuditModule;
import com.workflow.contracts.audit.model.AuditRiskLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** 当前组织职务关系的唯一写入口；不维护任命批次、预约或历史记录。 */
@Service
@RequiredArgsConstructor
public class PositionAssignmentService {
    public static final String UNIT_LEADER = "UNIT_LEADER";
    private final SysPositionMapper positionMapper;
    private final SysPositionAssignmentMapper assignmentMapper;
    private final SysOrganizationMapper organizationMapper;
    private final SysUserMapper userMapper;
    private final PositionOrganizationScopeService scopeService;

    /**
     * 原子替换一个组织内某职务的当前人员集合，保存后立即生效。
     * 按职务、组织、用户顺序加锁，防止单人职务被并发写入多人。
     * expectedUserIds 必须是编辑时读取的集合；同一目标集合的重复提交直接成功。
     *
     * @param request 目标职务、组织及完整人员集合；空集合清空当前人员
     * @throws PositionManagementException 范围越权、对象停用、人数不符或集合已变化时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(module = AuditModule.SYSTEM, action = AuditAction.CONFIGURE,
            operation = "设置职务人员", risk = AuditRiskLevel.HIGH,
            targetType = "SYS_POSITION_ASSIGNMENT", captureArguments = true)
    public void setMembers(PositionRequests.SetMembers request) {
        if (request == null || !StringUtils.hasText(request.positionCode())
                || !StringUtils.hasText(request.organizationUnitId())) {
            throw error(400, PositionErrorCode.ASSIGNMENT_INVALID, "职务和组织不能为空");
        }
        Set<String> desired = normalizedUsers(request.userIds());
        Set<String> expected = normalizedUsers(request.expectedUserIds());
        String code = request.positionCode().trim().toUpperCase(Locale.ROOT);
        SysPosition found = positionMapper.selectByCode(code);
        if (found == null) throw error(404, PositionErrorCode.POSITION_NOT_FOUND, "职务不存在");
        SysPosition position = positionMapper.selectForUpdate(found.getId());
        SysOrganization unit = organizationMapper.selectForUpdate(request.organizationUnitId());
        scopeService.requireVisible(request.organizationUnitId());
        if (position == null) throw error(404, PositionErrorCode.POSITION_NOT_FOUND, "职务不存在");
        if (unit == null) throw error(404, PositionErrorCode.ORGANIZATION_UNIT_NOT_FOUND, "组织不存在");
        List<SysPositionAssignment> current = current(position.getId(), unit.getId());
        Set<String> currentUsers = new TreeSet<>(current.stream().map(SysPositionAssignment::getUserId).toList());
        // 比较完整集合而非仅靠页面按钮禁用，防止过期页面覆盖其他管理员的新设置。
        if (!currentUsers.equals(desired) && !currentUsers.equals(expected)) {
            throw error(409, PositionErrorCode.ASSIGNMENT_REVISION_CONFLICT, "职务人员已变化，请刷新后重试");
        }
        if (desired.isEmpty()) {
            assignmentMapper.delete(Wrappers.<SysPositionAssignment>lambdaQuery()
                    .eq(SysPositionAssignment::getPositionId, position.getId())
                    .eq(SysPositionAssignment::getOrganizationUnitId, unit.getId()));
        } else {
            validateTarget(position, unit, desired);
            if (currentUsers.equals(desired)) return;
            String actor = UserContext.getUserId();
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            for (SysPositionAssignment relation : current) {
                if (!desired.contains(relation.getUserId())) assignmentMapper.deleteById(relation.getId());
            }
            for (String userId : desired) {
                if (currentUsers.contains(userId)) continue;
                SysPositionAssignment relation = new SysPositionAssignment();
                relation.setPositionId(position.getId());
                relation.setOrganizationUnitId(unit.getId());
                relation.setUserId(userId);
                relation.setRevision(1);
                relation.setCreatedBy(actor);
                relation.setUpdatedBy(actor);
                relation.setCreateTime(now);
                relation.setUpdateTime(now);
                assignmentMapper.insert(relation);
            }
        }
        if (UNIT_LEADER.equals(position.getPositionCode())) refreshLeaderProjection(unit.getId());
    }

    /**
     * 删除一条当前关系，不生成撤销记录；已创建的审批任务不会因此改派。
     * 与集合保存遵循相同锁顺序，revision 防止删除已被替换的数据。
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(module = AuditModule.SYSTEM, action = AuditAction.DELETE,
            operation = "移除职务人员", risk = AuditRiskLevel.HIGH,
            targetType = "SYS_POSITION_ASSIGNMENT", targetIdArg = 0)
    public void remove(String id, PositionRequests.RemoveAssignment request) {
        SysPositionAssignment relation = assignmentMapper.selectById(id);
        if (relation == null) return; // DELETE 可重复执行，删除后没有任职墓碑。
        SysPosition position = positionMapper.selectForUpdate(relation.getPositionId());
        organizationMapper.selectForUpdate(relation.getOrganizationUnitId());
        scopeService.requireVisible(relation.getOrganizationUnitId());
        SysPositionAssignment current = assignmentMapper.selectMemberForUpdate(id);
        if (current == null) return;
        if (request == null || request.revision() == null || !request.revision().equals(current.getRevision())) {
            throw error(409, PositionErrorCode.ASSIGNMENT_REVISION_CONFLICT, "职务人员已变化，请刷新后重试");
        }
        assignmentMapper.deleteById(id);
        if (position != null && UNIT_LEADER.equals(position.getPositionCode())) {
            refreshLeaderProjection(relation.getOrganizationUnitId());
        }
    }

    /** 当前负责人关系是唯一权威来源；在写事务内刷新兼容字段，无需定时生效对账。 */
    private void refreshLeaderProjection(String unitId) {
        // 投影显示当前设置的人员；用户或单位停用由审批查询单独过滤，避免保留定时同步任务。
        List<SysPositionAssignment> holders = current(positionMapper.selectByCode(UNIT_LEADER).getId(), unitId);
        SysUser user = holders.isEmpty() ? null : userMapper.selectById(holders.get(0).getUserId());
        organizationMapper.updateLeaderProjection(unitId, user == null ? null : user.getId(),
                user == null ? null : StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
    }

    /** 当前集合包含停用人员，便于管理员移除；审批查询另行过滤启用状态。 */
    private List<SysPositionAssignment> current(String positionId, String unitId) {
        return assignmentMapper.selectMembersForUpdate(positionId, unitId);
    }

    /** 保存集合前校验对象状态、人数及人员归属；清空关系允许处理已停用对象。 */
    private void validateTarget(SysPosition position, SysOrganization unit, Set<String> users) {
        if (!SysPosition.Status.ENABLED.name().equals(position.getStatus())) {
            throw error(409, PositionErrorCode.POSITION_STATUS_INVALID, "职务已停用");
        }
        if (!SysOrganization.Status.ENABLED.getValue().equals(unit.getStatus())) {
            throw error(409, PositionErrorCode.ORGANIZATION_UNIT_DISABLED, "组织已停用");
        }
        if (!"ANY".equals(position.getApplicableUnitType())
                && !position.getApplicableUnitType().equalsIgnoreCase(unit.getType())) {
            throw error(409, PositionErrorCode.POSITION_UNIT_TYPE_MISMATCH, "职务不适用于该组织类型");
        }
        if ("SINGLE".equals(position.getHolderMode()) && users.size() > 1) {
            throw error(409, PositionErrorCode.SINGLE_POSITION_OCCUPIED, "该职务只能设置一名人员");
        }
        for (String userId : users) {
            SysUser user = userMapper.selectForUpdate(userId);
            if (user == null) throw error(404, PositionErrorCode.USER_NOT_FOUND, "职务人员不存在");
            if (!SysUser.Status.ENABLED.getValue().equals(user.getStatus())) {
                throw error(409, PositionErrorCode.USER_DISABLED, "职务人员已停用");
            }
            requireUserWithinTargetUnit(user, unit);
        }
    }

    /** 不开放跨单位兼任：用户当前归属必须位于目标单位子树内，同时遵守管理员数据范围。 */
    private void requireUserWithinTargetUnit(SysUser user, SysOrganization target) {
        String currentId = StringUtils.hasText(user.getDeptId()) ? user.getDeptId() : user.getOrgId();
        Set<String> visited = new HashSet<>();
        for (int depth = 0; depth < 32 && StringUtils.hasText(currentId); depth++) {
            if (!visited.add(currentId)) {
                throw error(409, PositionErrorCode.ORGANIZATION_HIERARCHY_INVALID, "人员组织父链存在环");
            }
            if (target.getId().equals(currentId)) return;
            SysOrganization current = organizationMapper.selectById(currentId);
            if (current == null) break;
            currentId = current.getParentId();
            if ("0".equals(currentId)) break;
        }
        throw error(403, PositionErrorCode.ORGANIZATION_SCOPE_FORBIDDEN, "职务人员不属于目标组织范围");
    }

    /** 对输入去重并稳定排序，重复保存不会新增关系，同时保证用户行锁的固定顺序。 */
    private Set<String> normalizedUsers(List<String> users) {
        if (users == null || users.size() > 200 || users.stream().anyMatch(value -> !StringUtils.hasText(value))) {
            throw error(400, PositionErrorCode.ASSIGNMENT_INVALID, "人员集合必填且最多包含 200 人");
        }
        return new TreeSet<>(users.stream().map(String::trim).toList());
    }

    private PositionManagementException error(int status, PositionErrorCode code, String message) {
        return new PositionManagementException(status, code, message);
    }
}
