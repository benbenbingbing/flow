package com.workflow.admin.identity.infrastructure.adapter;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.admin.authorization.role.application.SysRoleService;
import com.workflow.admin.authorization.role.infrastructure.persistence.record.SysRole;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.organization.application.SysOrganizationService;
import com.workflow.admin.organization.infrastructure.persistence.record.SysOrganization;
import com.workflow.port.system.identity.model.IdentityDirectoryModels.*;
import com.workflow.port.system.identity.model.IdentityGroup;
import com.workflow.port.system.identity.model.IdentityUser;
import com.workflow.port.system.CurrentActorPort;
import com.workflow.core.error.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 使用与目录 HTTP 入口相同的权限，并将存储对象投影为最小业务模型。 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IdentityDirectoryQueryAdapter {
    private static final String USER_VIEW = "system:user:view";
    private static final String ORGANIZATION_VIEW = "system:organization:view";
    private static final String ROLE_VIEW = "system:role:view";

    private final CurrentActorPort authorization;
    private final SysUserService users;
    private final SysUserMapper userMapper;
    private final SysGroupMapper groups;
    private final SysRoleService roles;
    private final SysOrganizationService organizations;

    public UserPage pageUsers(UserQuery query) {
        require(USER_VIEW);
        var page = users.getUserPage(query.pageNumber(), query.pageSize(), query.keyword(), query.status(),
                query.organizationId(), query.departmentId(), query.roleId(), query.positionCode());
        return new UserPage(page.getRecords().stream().map(this::user).toList(),
                page.getTotal(), page.getPageNum(), page.getPageSize());
    }

    public List<IdentityUser> findUsersByIds(List<String> userIds) {
        require(USER_VIEW);
        var keys = userKeys(userIds);
        if (keys.isEmpty()) return List.of();
        var byId = userMapper.selectBatchIds(keys).stream().map(this::user)
                .collect(Collectors.toMap(IdentityUser::id, Function.identity()));
        return keys.stream().filter(byId::containsKey).map(byId::get).toList();
    }

    public List<IdentityUser> findUsersByUsernames(List<String> usernames) {
        require(USER_VIEW);
        var keys = userKeys(usernames);
        if (keys.isEmpty()) return List.of();
        var byUsername = userMapper.selectList(Wrappers.<SysUser>lambdaQuery().in(SysUser::getUsername, keys))
                .stream().map(this::user).collect(Collectors.toMap(IdentityUser::username, Function.identity()));
        return keys.stream().filter(byUsername::containsKey).map(byUsername::get).toList();
    }

    public Optional<Organization> findOrganization(String organizationId) {
        require(ORGANIZATION_VIEW);
        return Optional.ofNullable(organizations.getById(organizationId)).filter(SysOrganization::isHierarchyReady)
                .map(this::organization);
    }

    public List<Organization> listOrganizationChildren(String parentId, String type) {
        require(ORGANIZATION_VIEW);
        return organizations.getChildren(parentId, type, null).stream().map(this::organization).toList();
    }

    public List<OrganizationNode> listOrganizationTree(String type) {
        require(ORGANIZATION_VIEW);
        return organizations.getOrgTree(type).stream().map(this::organizationNode).toList();
    }

    public List<Organization> listOrganizationAncestors(String organizationId) {
        require(ORGANIZATION_VIEW);
        return organizations.getAncestors(organizationId).stream().map(this::organization).toList();
    }

    public List<Role> listRoles(boolean enabledOnly) {
        require(ROLE_VIEW);
        return (enabledOnly ? roles.getEnabledRoles() : roles.getRoleList()).stream().map(this::role).toList();
    }

    public List<Role> listUserRoles(String userId) {
        require(USER_VIEW);
        SysUser target = users.getById(userId);
        return target == null || target.getRoles() == null ? List.of()
                : target.getRoles().stream().map(this::role).toList();
    }

    public List<IdentityGroup> listUserGroups(String userId) {
        require(USER_VIEW);
        return groups.selectGroupsByUserId(userId).stream()
                .map(group -> new IdentityGroup(group.getId(), group.getGroupCode(), group.getGroupName())).toList();
    }

    private void require(String permission) {
        if (!authorization.hasPermission(permission)) throw new ForbiddenException("没有身份目录读取权限");
    }

    private IdentityUser user(SysUser user) {
        return new IdentityUser(user.getId(), user.getUsername(), user.getNickname(), user.getOrgId(), user.getDeptId());
    }

    private List<String> userKeys(List<String> keys) {
        if (keys.size() > 100) throw new IllegalArgumentException("单次最多查询 100 个用户");
        return keys.stream().filter(key -> key != null && !key.isBlank()).map(String::strip).distinct().toList();
    }

    private Organization organization(SysOrganization org) {
        return new Organization(org.getId(), org.getOrgCode(), org.getOrgName(), org.getType(), org.getParentId(),
                org.getBusinessLevelCode(), org.getStatus());
    }

    private OrganizationNode organizationNode(SysOrganization org) {
        var children = org.getChildren() == null ? List.<OrganizationNode>of()
                : org.getChildren().stream().map(this::organizationNode).toList();
        return new OrganizationNode(organization(org), children);
    }

    private Role role(SysRole role) {
        return new Role(role.getId(), role.getRoleCode(), role.getRoleName(), role.getStatus());
    }
}
