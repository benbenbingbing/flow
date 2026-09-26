package com.workflow.admin.identity.position.application;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.admin.identity.position.api.error.PositionErrorCode;
import com.workflow.admin.identity.position.api.error.PositionManagementException;
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

    /**
     * 分页查询位置分配查询；查询结果供调用方展示或继续处理。
     *
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @param keyword 关键字，供本方法分页查询位置分配查询时使用
     * @param positionCode 位置编码，后续用于分页查询位置分配查询时定位或关联目标
     * @param organizationUnitId 组织单元ID，后续用于分页查询位置分配查询时定位或关联目标
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param activeOnly 活动仅，供本方法分页查询位置分配查询时使用
     * @return 符合条件的位置视图结果，供调用方继续处理
     */
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

    /**
     * 处理组织，并将结果传给后续步骤。
     *
     * @param organizationUnitId 组织单元ID，后续用于处理组织时定位或关联目标
     * @return 处理后的组织结果，供调用方继续处理
     */
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

    /**
     * 处理用户，并将结果传给后续步骤。
     *
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @return 处理后的用户结果，供调用方继续处理
     */
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
     *
     * @param userIds 用户ID 集合，作为 {@code assignmentMapper.selectCurrentRowsByUsers} 的输入影响后续处理
     * @param asOf {@code as}，作为 {@code assignmentMapper.selectCurrentRowsByUsers} 的输入影响后续处理
     * @return 位置分配视图行集合，供调用方遍历或展示
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

    /**
     * 转换为视图；输出作为后续校验或处理的输入。
     *
     * @param row 行，作为 {@code PositionViews.AssignmentView} 的输入影响后续处理
     * @param asOf {@code as}，作为 {@code isAfter} 的输入影响后续处理
     * @return 转换为后的视图结果，供调用方继续处理
     */
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

    /**
     * 生成展示名称文本，供后续匹配或展示。
     *
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @return 处理后的展示名称文本，供调用方比较或展示
     */
    private String displayName(SysUser user) {
        return StringUtils.hasText(user.getNickname())
                ? user.getNickname() : user.getUsername();
    }

    /**
     * 规范化编码；输出作为后续校验或处理的输入。
     *
     * @param value 待规范化编码的原始输入，结果供调用方继续使用
     * @return 规范化后的编码文本，供调用方比较或展示
     */
    private String normalizeCode(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(java.util.Locale.ROOT) : null;
    }

    /**
     * 去除文本首尾空白，并将空白结果转为 null 供后续缺失值判断。
     *
     * @param value 待清理截止空值的原始输入，结果供调用方继续使用
     * @return 清理后的截止空值文本，供调用方比较或展示
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 转换为绝对时间；输出作为后续校验或处理的输入。
     *
     * @param value 待转换为绝对时间的原始输入，结果供调用方继续使用
     * @return 转换为后的绝对时间结果，供调用方继续处理
     */
    private Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
