package com.workflow.admin.identity.position.application;

import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.admin.identity.position.api.PositionErrorCode;
import com.workflow.admin.identity.position.api.PositionManagementException;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.admin.organization.infrastructure.persistence.record.SysOrganization;
import com.workflow.admin.security.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 职务任职的临时组织数据范围策略。
 *
 * <p>V1 尚无正式 {@code OrganizationDataScopePort}：超级管理员可访问全域；
 * 普通用户若有 deptId 仅访问该部门子树，否则访问 orgId 子树，两者均无
 * 则拒绝。查询、预检和写事务都必须调用本服务，避免只在前端过滤。</p>
 */
@Service
@RequiredArgsConstructor
public class PositionOrganizationScopeService {

    private static final int MAX_DEPTH = 32;
    private static final int MAX_SCOPE_UNITS = 10_000;

    private final CurrentUserRoleService currentUserRoleService;
    private final SysUserMapper userMapper;
    private final SysOrganizationMapper organizationMapper;

    /**
     * 返回当前用户可见组织 ID；超级管理员返回 null 表示不附加 SQL 范围。
     */
    public List<String> visibleUnitIds() {
        if (currentUserRoleService.isSuperAdmin()) {
            return null;
        }
        String actorId = UserContext.getUserId();
        SysUser actor = StringUtils.hasText(actorId)
                ? userMapper.selectById(actorId) : null;
        if (actor == null || !SysUser.Status.ENABLED.getValue().equals(actor.getStatus())) {
            throw forbidden("当前用户不存在或已禁用，不能读取组织任职");
        }
        String rootId = StringUtils.hasText(actor.getDeptId())
                ? actor.getDeptId() : actor.getOrgId();
        if (!StringUtils.hasText(rootId)) {
            throw forbidden("当前用户未配置部门或组织数据范围");
        }
        SysOrganization root = organizationMapper.selectById(rootId);
        if (root == null || !SysOrganization.Status.ENABLED.getValue().equals(root.getStatus())) {
            throw forbidden("当前用户的数据范围根节点不存在或已禁用");
        }

        Set<String> visited = new LinkedHashSet<>();
        ArrayDeque<ScopeNode> queue = new ArrayDeque<>();
        queue.add(new ScopeNode(root.getId(), 0));
        while (!queue.isEmpty()) {
            ScopeNode current = queue.removeFirst();
            if (!visited.add(current.id())) {
                continue;
            }
            if (current.depth() >= MAX_DEPTH || visited.size() > MAX_SCOPE_UNITS) {
                throw new PositionManagementException(
                        409,
                        PositionErrorCode.ORGANIZATION_HIERARCHY_INVALID,
                        "组织数据范围超过最大 32 层或 10000 个节点");
            }
            for (SysOrganization child : organizationMapper.selectChildren(current.id())) {
                queue.addLast(new ScopeNode(child.getId(), current.depth() + 1));
            }
        }
        return new ArrayList<>(visited);
    }

    /**
     * 服务端对象级校验；任何写操作和指定组织查询都必须重复执行。
     */
    public void requireVisible(String organizationUnitId) {
        if (!StringUtils.hasText(organizationUnitId)) {
            throw forbidden("组织节点不能为空");
        }
        List<String> visible = visibleUnitIds();
        if (visible != null && !visible.contains(organizationUnitId)) {
            throw forbidden("目标组织节点超出当前用户可管理范围");
        }
    }

    public boolean isVisible(String organizationUnitId) {
        List<String> visible = visibleUnitIds();
        return visible == null || visible.contains(organizationUnitId);
    }

    private PositionManagementException forbidden(String message) {
        return new PositionManagementException(
                403,
                PositionErrorCode.ORGANIZATION_SCOPE_FORBIDDEN,
                message);
    }

    private record ScopeNode(String id, int depth) {
    }
}
