package com.workflow.admin.identity.position.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.admin.identity.position.api.PositionErrorCode;
import com.workflow.admin.identity.position.api.PositionManagementException;
import com.workflow.admin.identity.position.api.request.PositionRequests;
import com.workflow.admin.identity.position.api.response.PositionViews;
import com.workflow.admin.identity.position.infrastructure.persistence.mapper.SysPositionMapper;
import com.workflow.admin.identity.position.infrastructure.persistence.record.SysPosition;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditRiskLevel;
import com.workflow.contracts.audit.SystemAudit;
import com.workflow.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

/**
 * 全局职务定义管理；职务与角色权限严格解耦。
 */
@Service
@RequiredArgsConstructor
public class PositionDefinitionService {

    private final SysPositionMapper positionMapper;
    private final PositionOrganizationScopeService scopeService;

    public PageResult<PositionViews.PositionView> page(
            int pageNum,
            int pageSize,
            String keyword,
            String applicableUnitType,
            String holderMode,
            String status) {
        int safePage = Math.max(pageNum, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        String normalizedUnitType = optionalEnum(
                applicableUnitType, SysPosition.ApplicableUnitType.class,
                "适用单位类型");
        String normalizedHolderMode = optionalEnum(
                holderMode, SysPosition.HolderMode.class, "任职模式");
        String normalizedStatus = optionalEnum(
                status, SysPosition.Status.class, "职务状态");
        LambdaQueryWrapper<SysPosition> query =
                new LambdaQueryWrapper<SysPosition>()
                        .eq(StringUtils.hasText(normalizedUnitType),
                                SysPosition::getApplicableUnitType,
                                normalizedUnitType)
                        .eq(StringUtils.hasText(normalizedHolderMode),
                                SysPosition::getHolderMode,
                                normalizedHolderMode)
                        .eq(StringUtils.hasText(normalizedStatus),
                                SysPosition::getStatus,
                                normalizedStatus)
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                .like(SysPosition::getPositionCode, keyword.trim())
                                .or()
                                .like(SysPosition::getPositionName, keyword.trim()))
                        .orderByAsc(SysPosition::getSortOrder)
                        .orderByAsc(SysPosition::getPositionCode);
        Page<SysPosition> result = positionMapper.selectPage(
                new Page<>(safePage, safeSize), query);
        List<String> visibleUnitIds = scopeService.visibleUnitIds();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<PositionViews.PositionView> records = result.getRecords().stream()
                .map(position -> toView(position, visibleUnitIds, now))
                .toList();
        return new PageResult<>(records, result.getTotal(),
                result.getCurrent(), result.getSize());
    }

    /** 返回任职表单与流程设计器可选择的启用职务。 */
    public List<PositionViews.PositionView> enabled(String applicableUnitType) {
        String unitType = optionalEnum(
                applicableUnitType,
                SysPosition.ApplicableUnitType.class,
                "适用单位类型");
        if (SysPosition.ApplicableUnitType.ANY.name().equals(unitType)) {
            unitType = null;
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return positionMapper.selectEnabled(unitType).stream()
                // 选项接口不暴露跨范围任职数量，也不为每行扫描流程历史。
                .map(position -> basicView(position, 0, 0, now))
                .toList();
    }

    public PositionViews.PositionView get(String id) {
        SysPosition position = positionMapper.selectById(id);
        if (position == null) {
            throw notFound();
        }
        return toView(
                position,
                scopeService.visibleUnitIds(),
                LocalDateTime.now(ZoneOffset.UTC));
    }

    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.SYSTEM,
            action = AuditAction.CREATE,
            operation = "新增职务定义",
            risk = AuditRiskLevel.HIGH,
            required = true,
            targetType = "SYS_POSITION",
            captureArguments = true,
            captureResult = true)
    public PositionViews.PositionView create(
            PositionRequests.CreatePosition request) {
        if (request == null) {
            throw invalid("请求不能为空");
        }
        String code = normalizeCode(request.positionCode());
        if (positionMapper.selectAnyByCode(code) != null) {
            throw new PositionManagementException(
                    409,
                    PositionErrorCode.POSITION_CODE_DUPLICATED,
                    "职务编码已存在且不可复用: " + code);
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String actor = requireActor();
        SysPosition position = new SysPosition();
        position.setPositionCode(code);
        position.setPositionName(requiredText(request.positionName(), "职务名称"));
        position.setApplicableUnitType(requiredEnum(
                request.applicableUnitType(),
                SysPosition.ApplicableUnitType.class,
                "适用单位类型"));
        position.setHolderMode(requiredEnum(
                request.holderMode(), SysPosition.HolderMode.class, "任职模式"));
        position.setBuiltIn(false);
        position.setStatus(SysPosition.Status.ENABLED.name());
        position.setSortOrder(nonNegative(request.sortOrder(), 0, "排序"));
        position.setDescription(trimToNull(request.description()));
        position.setRevision(1);
        position.setCreatedBy(actor);
        position.setUpdatedBy(actor);
        position.setCreateTime(now);
        position.setUpdateTime(now);
        position.setDeleted(0);
        positionMapper.insert(position);
        return basicView(position, 0, 0, now);
    }

    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.SYSTEM,
            action = AuditAction.UPDATE,
            operation = "修改职务定义",
            risk = AuditRiskLevel.HIGH,
            required = true,
            targetType = "SYS_POSITION",
            targetIdArg = 0,
            captureArguments = true,
            captureResult = true)
    public PositionViews.PositionView update(
            String id,
            PositionRequests.UpdatePosition request) {
        if (request == null) {
            throw invalid("请求不能为空");
        }
        SysPosition current = requireLocked(id);
        int expectedRevision = requireRevision(request.revision(), current);
        String unitType = requiredEnum(
                request.applicableUnitType(),
                SysPosition.ApplicableUnitType.class,
                "适用单位类型");
        String holderMode = requiredEnum(
                request.holderMode(), SysPosition.HolderMode.class, "任职模式");

        if (Boolean.TRUE.equals(current.getBuiltIn())
                && (!current.getApplicableUnitType().equals(unitType)
                    || !current.getHolderMode().equals(holderMode))) {
            throw new PositionManagementException(
                    409,
                    PositionErrorCode.POSITION_REFERENCED,
                    "内置负责人职务的适用单位类型和单人模式不可修改");
        }

        if (!SysPosition.ApplicableUnitType.ANY.name().equals(unitType)
                && !unitType.equals(current.getApplicableUnitType())
                && positionMapper.countAssignmentsOutsideUnitType(
                        id, unitType.toLowerCase(Locale.ROOT)) > 0) {
            throw new PositionManagementException(
                    409,
                    PositionErrorCode.POSITION_UNIT_TYPE_MISMATCH,
                    "现有任职包含不适用于新单位类型的组织节点");
        }
        if (SysPosition.HolderMode.MULTIPLE.name().equals(current.getHolderMode())
                && SysPosition.HolderMode.SINGLE.name().equals(holderMode)
                && positionMapper.countOverlapsPreventingSingleMode(id) > 0) {
            throw new PositionManagementException(
                    409,
                    PositionErrorCode.POSITION_HOLDER_MODE_CONFLICT,
                    "现有任职区间存在多人重叠，不能切换为单人职务");
        }
        int changed = positionMapper.updateDefinition(
                id,
                requiredText(request.positionName(), "职务名称"),
                unitType,
                holderMode,
                nonNegative(request.sortOrder(), 0, "排序"),
                trimToNull(request.description()),
                requireActor(),
                expectedRevision);
        requireChanged(changed);
        return getWithoutLock(id);
    }

    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.SYSTEM,
            action = AuditAction.CONFIGURE,
            operation = "启停职务定义",
            risk = AuditRiskLevel.HIGH,
            required = true,
            targetType = "SYS_POSITION",
            targetIdArg = 0,
            captureArguments = true)
    public void changeStatus(
            String id,
            PositionRequests.ChangePositionStatus request) {
        if (request == null) {
            throw invalid("请求不能为空");
        }
        SysPosition current = requireLocked(id);
        int expectedRevision = requireRevision(request.revision(), current);
        String status = requiredEnum(
                request.status(), SysPosition.Status.class, "职务状态");
        if (Boolean.TRUE.equals(current.getBuiltIn())
                && SysPosition.Status.DISABLED.name().equals(status)) {
            throw new PositionManagementException(
                    409,
                    PositionErrorCode.POSITION_REFERENCED,
                    "内置负责人职务必须保持启用");
        }
        requireChanged(positionMapper.updateStatus(
                id, status, requireActor(), expectedRevision));
    }

    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.SYSTEM,
            action = AuditAction.DELETE,
            operation = "删除未使用职务定义",
            risk = AuditRiskLevel.CRITICAL,
            required = true,
            targetType = "SYS_POSITION",
            targetIdArg = 0,
            captureArguments = true)
    public void delete(
            String id,
            PositionRequests.DeletePosition request) {
        SysPosition current = requireLocked(id);
        int expectedRevision = requireRevision(
                request == null ? null : request.revision(), current);
        if (Boolean.TRUE.equals(current.getBuiltIn())) {
            throw new PositionManagementException(
                    409,
                    PositionErrorCode.POSITION_REFERENCED,
                    "内置职务不可删除，只能保持启用");
        }
        if (positionMapper.countAssignments(id) > 0
                || positionMapper.countProcessReferences(
                        current.getPositionCode()) > 0) {
            throw new PositionManagementException(
                    409,
                    PositionErrorCode.POSITION_REFERENCED,
                    "职务已被任职或流程引用，只能停用");
        }
        requireChanged(positionMapper.softDelete(
                id, requireActor(), expectedRevision));
    }

    private PositionViews.PositionView toView(
            SysPosition position,
            List<String> visibleUnitIds,
            LocalDateTime now) {
        return basicView(
                position,
                positionMapper.countCurrentAssignments(
                        position.getId(), now, visibleUnitIds),
                positionMapper.countProcessReferences(position.getPositionCode()),
                now);
    }

    private PositionViews.PositionView basicView(
            SysPosition position,
            long currentAssignments,
            long processReferences,
            LocalDateTime now) {
        return new PositionViews.PositionView(
                position.getId(),
                position.getPositionCode(),
                position.getPositionName(),
                position.getApplicableUnitType(),
                position.getHolderMode(),
                Boolean.TRUE.equals(position.getBuiltIn()),
                position.getStatus(),
                position.getSortOrder() == null ? 0 : position.getSortOrder(),
                position.getDescription(),
                position.getRevision() == null ? 1 : position.getRevision(),
                currentAssignments,
                processReferences,
                position.getCreatedBy(),
                position.getUpdatedBy(),
                toInstant(position.getCreateTime()),
                toInstant(position.getUpdateTime()));
    }

    private PositionViews.PositionView getWithoutLock(String id) {
        SysPosition value = positionMapper.selectById(id);
        if (value == null) {
            throw notFound();
        }
        return basicView(value, 0, 0, LocalDateTime.now(ZoneOffset.UTC));
    }

    private SysPosition requireLocked(String id) {
        SysPosition position = StringUtils.hasText(id)
                ? positionMapper.selectForUpdate(id) : null;
        if (position == null) {
            throw notFound();
        }
        return position;
    }

    private int requireRevision(Integer requested, SysPosition current) {
        if (requested == null || !requested.equals(current.getRevision())) {
            throw new PositionManagementException(
                    409,
                    PositionErrorCode.POSITION_REVISION_CONFLICT,
                    "职务定义已被其他管理员修改，请刷新后重试");
        }
        return requested;
    }

    private void requireChanged(int changed) {
        if (changed != 1) {
            throw new PositionManagementException(
                    409,
                    PositionErrorCode.POSITION_REVISION_CONFLICT,
                    "职务定义已被其他管理员修改，请刷新后重试");
        }
    }

    private PositionManagementException notFound() {
        return new PositionManagementException(
                404, PositionErrorCode.POSITION_NOT_FOUND, "职务不存在");
    }

    private PositionManagementException invalid(String message) {
        return new PositionManagementException(
                400, PositionErrorCode.BATCH_ASSIGNMENT_INVALID, message);
    }

    private String requireActor() {
        String actor = UserContext.getUserId();
        if (!StringUtils.hasText(actor)) {
            throw new PositionManagementException(
                    403,
                    PositionErrorCode.ORGANIZATION_SCOPE_FORBIDDEN,
                    "用户未登录");
        }
        return actor;
    }

    private String normalizeCode(String code) {
        String normalized = requiredText(code, "职务编码")
                .toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z][A-Z0-9_-]{0,99}")) {
            throw invalid("职务编码必须以字母开头，且只能包含字母、数字、下划线或连字符");
        }
        return normalized;
    }

    private <E extends Enum<E>> String requiredEnum(
            String value, Class<E> type, String label) {
        String normalized = requiredText(value, label).toUpperCase(Locale.ROOT);
        try {
            return Enum.valueOf(type, normalized).name();
        } catch (IllegalArgumentException exception) {
            throw invalid(label + "不支持: " + value);
        }
    }

    private <E extends Enum<E>> String optionalEnum(
            String value, Class<E> type, String label) {
        return StringUtils.hasText(value)
                ? requiredEnum(value, type, label) : null;
    }

    private String requiredText(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw invalid(label + "不能为空");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private int nonNegative(Integer value, int defaultValue, String label) {
        int result = value == null ? defaultValue : value;
        if (result < 0) {
            throw invalid(label + "不能小于 0");
        }
        return result;
    }

    private java.time.Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
