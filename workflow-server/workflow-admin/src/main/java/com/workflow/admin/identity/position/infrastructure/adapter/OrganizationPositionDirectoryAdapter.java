package com.workflow.admin.identity.position.infrastructure.adapter;

import com.workflow.admin.dictionary.infrastructure.persistence.mapper.SysDictItemMapper;
import com.workflow.admin.dictionary.infrastructure.persistence.record.SysDictItem;
import com.workflow.admin.identity.position.infrastructure.persistence.mapper.SysPositionAssignmentMapper;
import com.workflow.admin.identity.position.infrastructure.persistence.mapper.SysPositionMapper;
import com.workflow.admin.identity.position.infrastructure.persistence.record.PositionAssignmentViewRow;
import com.workflow.admin.identity.position.infrastructure.persistence.record.SysPosition;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.admin.organization.infrastructure.persistence.record.SysOrganization;
import com.workflow.contracts.identity.position.model.InitiatorOrganizationSnapshot;
import com.workflow.contracts.identity.position.model.OrganizationBusinessLevelView;
import com.workflow.contracts.identity.position.error.OrganizationPositionDirectoryException;
import com.workflow.contracts.identity.position.port.OrganizationPositionDirectoryPort;
import com.workflow.contracts.identity.position.error.OrganizationPositionErrorCode;
import com.workflow.contracts.identity.position.model.OrganizationUnitSnapshot;
import com.workflow.contracts.identity.position.model.OrganizationUnitStateView;
import com.workflow.contracts.identity.position.model.PositionDefinitionView;
import com.workflow.contracts.identity.position.model.PositionDirectoryResultCode;
import com.workflow.contracts.identity.position.model.PositionHolderResolution;
import com.workflow.contracts.identity.position.model.PositionHolderView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * workflow-admin 对组织职务跨模块查询端口的唯一实现。
 *
 * <p>适配器只返回 contracts 中的轻量值对象，不向流程模块泄漏 Mapper
 * 或 MyBatis Record；组织链捕获始终沿 parent_id 且最多 32 层。</p>
 */
@Component
@RequiredArgsConstructor
public class OrganizationPositionDirectoryAdapter
        implements OrganizationPositionDirectoryPort {

    private static final String BUSINESS_LEVEL_DICT =
            "organization_business_level";
    private static final int MAX_HIERARCHY_DEPTH = 32;

    private final SysUserMapper userMapper;
    private final SysOrganizationMapper organizationMapper;
    private final SysPositionMapper positionMapper;
    private final SysPositionAssignmentMapper assignmentMapper;
    private final SysDictItemMapper dictItemMapper;

    /**
     * 捕获{@code initiator}快照；结果供调用方的后续步骤使用。
     *
     * @param idOrUsername ID或用户名，后续用于捕获{@code initiator}快照时匹配或展示
     * @return 捕获后的{@code initiator}快照结果，供调用方继续处理
     */
    @Override
    public InitiatorOrganizationSnapshot captureInitiatorSnapshot(
            String idOrUsername) {
        SysUser user = null;
        if (StringUtils.hasText(idOrUsername)) {
            user = userMapper.selectById(idOrUsername.trim());
            if (user == null) {
                user = userMapper.selectByUsername(idOrUsername.trim());
            }
        }
        if (user == null
                || !SysUser.Status.ENABLED.getValue().equals(user.getStatus())) {
            throw directoryFailure(
                    OrganizationPositionErrorCode.INITIATOR_NOT_FOUND,
                    "流程发起人不存在或已禁用");
        }
        if (!StringUtils.hasText(user.getOrgId())) {
            throw directoryFailure(
                    OrganizationPositionErrorCode.INITIATOR_ORGANIZATION_MISSING,
                    "流程发起人未配置所属组织");
        }
        SysOrganization organization = organizationMapper.selectById(
                user.getOrgId());
        requireSnapshotAnchor(
                organization,
                SysOrganization.Type.ORG.getValue(),
                OrganizationPositionErrorCode.INITIATOR_ORGANIZATION_MISSING,
                "流程发起人所属组织不存在、已禁用或类型错误");

        SysOrganization start = organization;
        if (StringUtils.hasText(user.getDeptId())) {
            SysOrganization department = organizationMapper.selectById(
                    user.getDeptId());
            requireSnapshotAnchor(
                    department,
                    SysOrganization.Type.DEPT.getValue(),
                    OrganizationPositionErrorCode.INITIATOR_DEPARTMENT_MISSING,
                    "流程发起人所属部门不存在、已禁用或类型错误");
            start = department;
        }

        List<OrganizationUnitSnapshot> units = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        SysOrganization current = start;
        for (int depth = 0; depth < MAX_HIERARCHY_DEPTH; depth++) {
            if (current == null) {
                throw directoryFailure(
                        OrganizationPositionErrorCode.ORG_SNAPSHOT_INVALID,
                        "组织父链存在悬空节点");
            }
            if (!visited.add(current.getId())) {
                throw directoryFailure(
                        OrganizationPositionErrorCode.HIERARCHY_CYCLE,
                        "组织父链存在环: " + current.getId());
            }
            if (!SysOrganization.Status.ENABLED.getValue().equals(
                    current.getStatus())) {
                throw directoryFailure(
                        OrganizationPositionErrorCode.ORG_SNAPSHOT_INVALID,
                        "组织父链包含已禁用节点: " + current.getId());
            }
            units.add(new OrganizationUnitSnapshot(
                    current.getId(), current.getOrgName(), current.getType(),
                    current.getBusinessLevelCode()));
            String parentId = current.getParentId();
            if (!StringUtils.hasText(parentId) || "0".equals(parentId)) {
                if (!visited.contains(organization.getId())) {
                    throw directoryFailure(
                            OrganizationPositionErrorCode.ORG_SNAPSHOT_INVALID,
                            "发起人部门不在所属组织范围内");
                }
                return new InitiatorOrganizationSnapshot(
                        InitiatorOrganizationSnapshot.VERSION,
                        user.getId(), user.getUsername(), organization.getId(),
                        StringUtils.hasText(user.getDeptId())
                                ? user.getDeptId() : null,
                        units, Instant.now());
            }
            current = organizationMapper.selectById(parentId);
        }
        throw directoryFailure(
                OrganizationPositionErrorCode.HIERARCHY_EXHAUSTED,
                "组织父链超过最大 32 层");
    }

    /**
     * 校验并获取活动组织单元；不满足约束时阻止后续处理。
     *
     * @param organizationUnitId 组织单元ID，后续用于校验并获取活动组织单元时定位或关联目标
     * @return 校验并获取后的活动组织单元结果，供调用方继续处理
     */
    @Override
    public OrganizationUnitStateView requireActiveOrganizationUnit(
            String organizationUnitId) {
        SysOrganization unit = StringUtils.hasText(organizationUnitId)
                ? organizationMapper.selectById(organizationUnitId) : null;
        if (unit == null) {
            throw directoryFailure(
                    OrganizationPositionErrorCode.ORGANIZATION_UNIT_NOT_FOUND,
                    "冻结组织节点已不存在: " + organizationUnitId);
        }
        if (!SysOrganization.Status.ENABLED.getValue().equals(unit.getStatus())) {
            throw directoryFailure(
                    OrganizationPositionErrorCode.ORGANIZATION_UNIT_DISABLED,
                    "冻结组织节点已停用: " + organizationUnitId);
        }
        return new OrganizationUnitStateView(
                unit.getId(), unit.getType(),
                unit.getUpdateTime() == null
                        ? "0" : unit.getUpdateTime().toString());
    }

    /**
     * 校验并获取启用位置；不满足约束时阻止后续处理。
     *
     * @param positionCode 位置编码，后续用于校验并获取启用位置时定位或关联目标
     * @return 校验并获取后的启用位置结果，供调用方继续处理
     */
    @Override
    public PositionDefinitionView requireEnabledPosition(String positionCode) {
        SysPosition position = findPosition(positionCode);
        if (!SysPosition.Status.ENABLED.name().equals(position.getStatus())) {
            throw directoryFailure(
                    OrganizationPositionErrorCode.POSITION_DISABLED,
                    "职务已停用: " + position.getPositionCode());
        }
        return definition(position);
    }

    /**
     * 列出启用{@code positions}；查询结果供调用方展示或继续处理。
     *
     * @param applicableUnitType 适用单元类型标识，决定后续启用{@code positions}采用的处理分支
     * @return 位置定义视图集合，供调用方遍历或展示
     */
    @Override
    public List<PositionDefinitionView> listEnabledPositions(
            String applicableUnitType) {
        String normalizedType = normalizeUnitType(applicableUnitType);
        return positionMapper.selectEnabled(normalizedType).stream()
                .map(this::definition)
                .toList();
    }

    /**
     * 查询有效持有者集合；查询结果供调用方展示或继续处理。
     *
     * @param positionCode 位置编码，后续用于查询有效持有者集合时定位或关联目标
     * @param organizationUnitId 组织单元ID，后续用于查询有效持有者集合时定位或关联目标
     * @param asOf {@code as}，供本方法查询有效持有者集合时使用
     * @return 符合条件的位置持有者解析结果，供调用方继续处理
     */
    @Override
    public PositionHolderResolution findEffectiveHolders(
            String positionCode,
            String organizationUnitId,
            Instant asOf) {
        String normalizedCode = normalizePositionCode(positionCode);
        SysPosition position = positionMapper.selectByCode(normalizedCode);
        if (position == null) {
            return empty(PositionDirectoryResultCode.POSITION_NOT_FOUND,
                    normalizedCode, organizationUnitId, "0");
        }
        if (!SysPosition.Status.ENABLED.name().equals(position.getStatus())) {
            return empty(PositionDirectoryResultCode.POSITION_DISABLED,
                    normalizedCode, organizationUnitId,
                    String.valueOf(position.getRevision()));
        }
        SysOrganization unit = organizationMapper.selectById(organizationUnitId);
        if (unit == null) {
            return empty(PositionDirectoryResultCode.ORGANIZATION_UNIT_NOT_FOUND,
                    normalizedCode, organizationUnitId,
                    String.valueOf(position.getRevision()));
        }
        String revision = assignmentMapper.selectDirectoryRevision(
                position.getId(), unit.getId());
        if (!SysOrganization.Status.ENABLED.getValue().equals(unit.getStatus())) {
            return empty(PositionDirectoryResultCode.ORGANIZATION_UNIT_DISABLED,
                    normalizedCode, organizationUnitId, revision);
        }
        if (!SysPosition.ApplicableUnitType.ANY.name().equals(
                position.getApplicableUnitType())
                && !position.getApplicableUnitType().equals(
                    unit.getType().toUpperCase(Locale.ROOT))) {
            return empty(PositionDirectoryResultCode.POSITION_UNIT_TYPE_MISMATCH,
                    normalizedCode, organizationUnitId, revision);
        }
        Instant effectiveAt = asOf == null ? Instant.now() : asOf;
        List<PositionHolderView> holders = assignmentMapper.selectEffectiveRows(
                        normalizedCode,
                        organizationUnitId,
                        LocalDateTime.ofInstant(effectiveAt, ZoneOffset.UTC))
                .stream()
                .map(this::holder)
                .toList();
        if (holders.isEmpty()) {
            return empty(PositionDirectoryResultCode.NO_ACTIVE_HOLDER,
                    normalizedCode, organizationUnitId, revision);
        }
        return new PositionHolderResolution(
                PositionDirectoryResultCode.RESOLVED,
                normalizedCode,
                organizationUnitId,
                holders,
                revision);
    }

    /**
     * 判断是否组织业务层级启用；判断结果决定调用方的后续分支。
     *
     * @param businessLevelCode 业务层级编码，后续用于判断是否组织业务层级启用时定位或关联目标
     * @return 组织业务层级启用条件成立时为 true，否则为 false
     */
    @Override
    public boolean isOrganizationBusinessLevelEnabled(
            String businessLevelCode) {
        return StringUtils.hasText(businessLevelCode)
                && dictItemMapper.selectEnabledByCode(
                        BUSINESS_LEVEL_DICT,
                        businessLevelCode.trim().toUpperCase(Locale.ROOT)) != null;
    }

    /**
     * 列出启用组织业务{@code levels}；查询结果供调用方展示或继续处理。
     *
     * @return 组织业务层级视图集合，供调用方遍历或展示
     */
    @Override
    public List<OrganizationBusinessLevelView>
            listEnabledOrganizationBusinessLevels() {
        return dictItemMapper.selectEnabledByDictCode(BUSINESS_LEVEL_DICT)
                .stream()
                .map(item -> new OrganizationBusinessLevelView(
                        item.getItemCode(), item.getItemLabel(),
                        item.getSort() == null ? 0 : item.getSort()))
                .toList();
    }

    /**
     * 处理定义，并将结果传给后续步骤。
     *
     * @param position 位置，作为 {@code PositionDefinitionView} 的输入影响后续处理
     * @return 处理后的定义结果，供调用方继续处理
     */
    private PositionDefinitionView definition(SysPosition position) {
        return new PositionDefinitionView(
                position.getPositionCode(), position.getPositionName(),
                position.getApplicableUnitType(), position.getHolderMode(),
                position.getRevision() == null ? 1 : position.getRevision());
    }

    /**
     * 处理持有者，并将结果传给后续步骤。
     *
     * @param row 行，作为 {@code PositionHolderView} 的输入影响后续处理
     * @return 处理后的持有者结果，供调用方继续处理
     */
    private PositionHolderView holder(PositionAssignmentViewRow row) {
        String displayName = StringUtils.hasText(row.getNickname())
                ? row.getNickname() : row.getUsername();
        return new PositionHolderView(
                row.getUserId(), row.getUsername(), displayName,
                Boolean.TRUE.equals(row.getIsPrimary()),
                row.getSortOrder() == null ? 0 : row.getSortOrder(),
                row.getEffectiveFrom().toInstant(ZoneOffset.UTC));
    }

    /**
     * 处理空，并将结果传给后续步骤。
     *
     * @param code 编码，后续用于处理空时定位或关联目标
     * @param positionCode 位置编码，后续用于处理空时定位或关联目标
     * @param unitId 单元ID，后续用于处理空时定位或关联目标
     * @param revision 修订版本，供本方法处理空时使用
     * @return 处理后的空结果，供调用方继续处理
     */
    private PositionHolderResolution empty(
            PositionDirectoryResultCode code,
            String positionCode,
            String unitId,
            String revision) {
        return new PositionHolderResolution(
                code, positionCode, unitId, List.of(),
                revision == null ? "0" : revision);
    }

    /**
     * 查询位置；查询结果供调用方展示或继续处理。
     *
     * @param code 编码，后续用于查询位置时定位或关联目标
     * @return 符合条件的系统位置结果，供调用方继续处理
     */
    private SysPosition findPosition(String code) {
        String normalizedCode = normalizePositionCode(code);
        SysPosition position = positionMapper.selectByCode(normalizedCode);
        if (position == null) {
            throw directoryFailure(
                    OrganizationPositionErrorCode.POSITION_NOT_FOUND,
                    "职务不存在: " + normalizedCode);
        }
        return position;
    }

    /**
     * 规范化位置编码；输出作为后续校验或处理的输入。
     *
     * @param code 编码，后续用于规范化位置编码时定位或关联目标
     * @return 规范化后的位置编码文本，供调用方比较或展示
     */
    private String normalizePositionCode(String code) {
        return StringUtils.hasText(code)
                ? code.trim().toUpperCase(Locale.ROOT) : "";
    }

    /**
     * 规范化单元类型；输出作为后续校验或处理的输入。
     *
     * @param unitType 单元类型标识，决定后续单元类型采用的处理分支
     * @return 规范化后的单元类型文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private String normalizeUnitType(String unitType) {
        if (!StringUtils.hasText(unitType)
                || SysPosition.ApplicableUnitType.ANY.name()
                    .equalsIgnoreCase(unitType.trim())) {
            return null;
        }
        String normalized = unitType.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ORG", "DEPT").contains(normalized)) {
            throw new IllegalArgumentException(
                    "适用单位类型只支持 ORG/DEPT/ANY");
        }
        return normalized;
    }

    /**
     * 校验并获取快照锚点；不满足约束时阻止后续处理。
     *
     * @param unit 单元，供本方法校验并获取快照锚点时使用
     * @param expectedType 预期类型标识，决定后续快照锚点采用的处理分支
     * @param errorCode 错误编码，后续用于校验并获取快照锚点时定位或关联目标
     * @param message 消息，作为 {@code directoryFailure} 的输入影响后续处理
     */
    private void requireSnapshotAnchor(
            SysOrganization unit,
            String expectedType,
            OrganizationPositionErrorCode errorCode,
            String message) {
        if (unit == null
                || !expectedType.equals(unit.getType())
                || !SysOrganization.Status.ENABLED.getValue().equals(
                    unit.getStatus())) {
            throw directoryFailure(errorCode, message);
        }
    }

    /**
     * 构造目录失败异常，供调用方区分失败原因。
     *
     * @param code 编码，后续用于处理目录失败时定位或关联目标
     * @param message 消息，作为 {@code OrganizationPositionDirectoryException} 的输入影响后续处理
     * @return 处理后的目录失败结果，供调用方继续处理
     */
    private OrganizationPositionDirectoryException directoryFailure(
            OrganizationPositionErrorCode code,
            String message) {
        return new OrganizationPositionDirectoryException(code, message);
    }
}
