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
import com.workflow.contracts.identity.position.InitiatorOrganizationSnapshot;
import com.workflow.contracts.identity.position.OrganizationBusinessLevelView;
import com.workflow.contracts.identity.position.OrganizationPositionDirectoryException;
import com.workflow.contracts.identity.position.OrganizationPositionDirectoryPort;
import com.workflow.contracts.identity.position.OrganizationPositionErrorCode;
import com.workflow.contracts.identity.position.OrganizationUnitSnapshot;
import com.workflow.contracts.identity.position.OrganizationUnitStateView;
import com.workflow.contracts.identity.position.PositionDefinitionView;
import com.workflow.contracts.identity.position.PositionDirectoryResultCode;
import com.workflow.contracts.identity.position.PositionHolderResolution;
import com.workflow.contracts.identity.position.PositionHolderView;
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

    @Override
    public List<PositionDefinitionView> listEnabledPositions(
            String applicableUnitType) {
        String normalizedType = normalizeUnitType(applicableUnitType);
        return positionMapper.selectEnabled(normalizedType).stream()
                .map(this::definition)
                .toList();
    }

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

    @Override
    public boolean isOrganizationBusinessLevelEnabled(
            String businessLevelCode) {
        return StringUtils.hasText(businessLevelCode)
                && dictItemMapper.selectEnabledByCode(
                        BUSINESS_LEVEL_DICT,
                        businessLevelCode.trim().toUpperCase(Locale.ROOT)) != null;
    }

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

    private PositionDefinitionView definition(SysPosition position) {
        return new PositionDefinitionView(
                position.getPositionCode(), position.getPositionName(),
                position.getApplicableUnitType(), position.getHolderMode(),
                position.getRevision() == null ? 1 : position.getRevision());
    }

    private PositionHolderView holder(PositionAssignmentViewRow row) {
        String displayName = StringUtils.hasText(row.getNickname())
                ? row.getNickname() : row.getUsername();
        return new PositionHolderView(
                row.getUserId(), row.getUsername(), displayName,
                Boolean.TRUE.equals(row.getIsPrimary()),
                row.getSortOrder() == null ? 0 : row.getSortOrder(),
                row.getEffectiveFrom().toInstant(ZoneOffset.UTC));
    }

    private PositionHolderResolution empty(
            PositionDirectoryResultCode code,
            String positionCode,
            String unitId,
            String revision) {
        return new PositionHolderResolution(
                code, positionCode, unitId, List.of(),
                revision == null ? "0" : revision);
    }

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

    private String normalizePositionCode(String code) {
        return StringUtils.hasText(code)
                ? code.trim().toUpperCase(Locale.ROOT) : "";
    }

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

    private OrganizationPositionDirectoryException directoryFailure(
            OrganizationPositionErrorCode code,
            String message) {
        return new OrganizationPositionDirectoryException(code, message);
    }
}
