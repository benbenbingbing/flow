package com.workflow.admin.identity.position.application;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.admin.identity.position.api.PositionErrorCode;
import com.workflow.admin.identity.position.api.PositionManagementException;
import com.workflow.admin.identity.position.api.response.PositionViews;
import com.workflow.admin.identity.position.infrastructure.persistence.mapper.SysPositionAssignmentMapper;
import com.workflow.admin.identity.position.infrastructure.persistence.record.PositionAssignmentViewRow;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.admin.organization.infrastructure.persistence.record.SysOrganization;
import com.workflow.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 任职历史与当前任职的范围安全查询服务。
 */
@Service
@RequiredArgsConstructor
public class PositionAssignmentQueryService {

    private final SysPositionAssignmentMapper assignmentMapper;
    private final SysOrganizationMapper organizationMapper;
    private final SysUserMapper userMapper;
    private final PositionOrganizationScopeService scopeService;

    public PageResult<PositionViews.AssignmentView> page(
            int pageNum,
            int pageSize,
            String keyword,
            String positionCode,
            String organizationUnitId,
            String userId,
            Boolean activeOnly) {
        if (StringUtils.hasText(organizationUnitId)) {
            scopeService.requireVisible(organizationUnitId);
        }
        int safePage = Math.max(pageNum, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Page<PositionAssignmentViewRow> result =
                assignmentMapper.selectAssignmentPage(
                        new Page<>(safePage, safeSize),
                        trimToNull(keyword),
                        normalizeCode(positionCode),
                        trimToNull(organizationUnitId),
                        trimToNull(userId),
                        activeOnly,
                        now,
                        scopeService.visibleUnitIds());
        return new PageResult<>(
                result.getRecords().stream()
                        .map(row -> toView(row, now)).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    public PositionViews.OrganizationAssignmentMatrix byOrganization(
            String organizationUnitId) {
        scopeService.requireVisible(organizationUnitId);
        SysOrganization unit = organizationMapper.selectById(organizationUnitId);
        if (unit == null) {
            throw new PositionManagementException(
                    404,
                    PositionErrorCode.ORGANIZATION_UNIT_NOT_FOUND,
                    "组织节点不存在");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return new PositionViews.OrganizationAssignmentMatrix(
                unit.getId(), unit.getOrgName(), unit.getType(),
                unit.getBusinessLevelCode(),
                assignmentMapper.selectRowsByUnit(unit.getId()).stream()
                        .map(row -> toView(row, now)).toList());
    }

    public PositionViews.UserAssignments byUser(String userId) {
        List<String> visibleUnitIds = scopeService.visibleUnitIds();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new PositionManagementException(
                    404, PositionErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        String userScopeAnchor = StringUtils.hasText(user.getDeptId())
                ? user.getDeptId() : user.getOrgId();
        if (visibleUnitIds != null
                && (!StringUtils.hasText(userScopeAnchor)
                    || !visibleUnitIds.contains(userScopeAnchor))) {
            // 不返回用户名或空任职集合，避免通过对象 ID 枚举范围外用户身份。
            throw new PositionManagementException(
                    403,
                    PositionErrorCode.ORGANIZATION_SCOPE_FORBIDDEN,
                    "目标用户超出当前用户可查看的组织范围");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return new PositionViews.UserAssignments(
                user.getId(), user.getUsername(), displayName(user),
                assignmentMapper.selectRowsByUser(
                                userId, visibleUnitIds).stream()
                        .map(row -> toView(row, now)).toList());
    }

    /**
     * 为用户分页回填当前任职摘要；返回值已按当前管理者组织范围过滤。
     */
    public List<PositionAssignmentViewRow> currentRowsByUsers(
            List<String> userIds,
            LocalDateTime asOf) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return assignmentMapper.selectCurrentRowsByUsers(
                userIds, asOf, scopeService.visibleUnitIds());
    }

    public PositionViews.AssignmentView toView(
            PositionAssignmentViewRow row,
            LocalDateTime asOf) {
        boolean active = row.getRevokedAt() == null
                && !row.getEffectiveFrom().isAfter(asOf)
                && (row.getEffectiveTo() == null
                    || row.getEffectiveTo().isAfter(asOf));
        String displayName = StringUtils.hasText(row.getNickname())
                ? row.getNickname() : row.getUsername();
        return new PositionViews.AssignmentView(
                row.getId(), row.getPositionId(), row.getPositionCode(),
                row.getPositionName(), row.getOrganizationUnitId(),
                row.getOrganizationUnitName(), row.getOrganizationUnitType(),
                row.getBusinessLevelCode(), row.getUserId(), row.getUsername(),
                displayName, Boolean.TRUE.equals(row.getIsPrimary()),
                row.getSortOrder() == null ? 0 : row.getSortOrder(),
                toInstant(row.getEffectiveFrom()),
                toInstant(row.getEffectiveTo()),
                toInstant(row.getRevokedAt()), row.getRevokedBy(),
                row.getRevokeReason(),
                row.getRevision() == null ? 1 : row.getRevision(), active,
                toInstant(row.getCreateTime()), toInstant(row.getUpdateTime()));
    }

    private String displayName(SysUser user) {
        return StringUtils.hasText(user.getNickname())
                ? user.getNickname() : user.getUsername();
    }

    private String normalizeCode(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(java.util.Locale.ROOT) : null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
