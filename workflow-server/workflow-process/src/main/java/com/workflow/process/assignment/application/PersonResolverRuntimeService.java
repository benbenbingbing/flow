package com.workflow.process.assignment.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflow.contracts.identity.resolver.PersonPrincipal;
import com.workflow.contracts.identity.resolver.PersonResolveRequest;
import com.workflow.contracts.identity.resolver.PersonResolveUsage;
import com.workflow.contracts.identity.resolver.PersonResolver;
import com.workflow.admin.identity.group.infrastructure.persistence.record.SysGroup;
import com.workflow.admin.organization.infrastructure.persistence.record.SysOrganization;
import com.workflow.admin.authorization.role.infrastructure.persistence.record.SysRole;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysUserGroupMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserRoleMapper;
import com.workflow.admin.extension.person.infrastructure.persistence.mapper.PersonResolverDefinitionMapper;
import com.workflow.admin.extension.person.infrastructure.persistence.record.PersonResolverDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 统一人员解析器运行时入口。
 */
@Service
@RequiredArgsConstructor
public class PersonResolverRuntimeService {

    private final List<PersonResolver> resolvers;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysGroupMapper groupMapper;
    private final SysUserGroupMapper userGroupMapper;
    private final SysOrganizationMapper organizationMapper;

    @Autowired(required = false)
    private PersonResolverDefinitionMapper resolverDefinitionMapper;

    public boolean supports(String resolverCode, PersonResolveUsage usage) {
        PersonResolver resolver = find(resolverCode);
        return resolver != null
                && (resolver.descriptor().supportedUsages().isEmpty()
                || resolver.descriptor().supportedUsages().contains(usage));
    }

    /**
     * 校验解析器实现、用途以及受控目录启用状态。
     */
    public boolean supportsConfigured(
            String resolverCode,
            PersonResolveUsage usage) {
        if (!supports(resolverCode, usage)) {
            return false;
        }
        if (resolverDefinitionMapper == null) {
            // 兼容不启动 Spring 的轻量单测；应用上下文中该 Mapper 必然注入。
            return true;
        }
        PersonResolverDefinition definition = resolverDefinitionMapper.selectOne(
                new LambdaQueryWrapper<PersonResolverDefinition>()
                        .eq(PersonResolverDefinition::getResolverCode, resolverCode)
                        .eq(PersonResolverDefinition::getDeleted, 0)
                        .last("LIMIT 1"));
        return definition != null
                && Boolean.TRUE.equals(definition.getEnabled());
    }

    public void requireConfigured(
            String resolverCode,
            PersonResolveUsage usage) {
        if (!supportsConfigured(resolverCode, usage)) {
            throw new IllegalArgumentException(
                    "人员接口未配置、未启用、不可用或不支持用途 "
                            + usage.name()
                            + ": "
                            + resolverCode);
        }
    }

    public List<String> resolveUsernames(
            String resolverCode,
            PersonResolveRequest request) {
        PersonResolver resolver = find(resolverCode);
        if (resolver == null) {
            throw new IllegalArgumentException(
                    "未注册人员解析器: " + resolverCode);
        }
        if (!resolver.descriptor().supportedUsages().isEmpty()
                && !resolver.descriptor().supportedUsages()
                .contains(request.usage())) {
            throw new IllegalArgumentException(
                    "人员解析器不支持用途 "
                            + request.usage().name()
                            + ": "
                            + resolverCode);
        }

        return resolvePrincipalUsernames(
                resolver.resolve(request).principals());
    }

    /**
     * 将用户、角色、用户组或组织主体统一展开为启用且未删除的本地用户名。
     * Flowable 任务监听器也使用该入口校验实际 identity link，避免静态配置
     * 绕过受控解析器已有的本地用户安全边界。
     */
    public List<String> resolvePrincipalUsernames(
            Collection<PersonPrincipal> principals) {
        LinkedHashMap<String, SysUser> users = new LinkedHashMap<>();
        if (principals == null) {
            return List.of();
        }
        for (PersonPrincipal principal : principals) {
            if (principal == null) {
                continue;
            }
            List<SysUser> resolved = switch (principal.type()) {
                case USER -> resolveDirectUsers(List.of(principal.key()));
                case ROLE -> resolveRoles(List.of(principal.key()));
                case GROUP -> resolveGroups(List.of(principal.key()));
                case ORGANIZATION ->
                        resolveOrganizations(List.of(principal.key()));
            };
            resolved.forEach(user ->
                    users.putIfAbsent(user.getUsername(), user));
        }
        return new ArrayList<>(users.keySet());
    }

    private PersonResolver find(String resolverCode) {
        if (!StringUtils.hasText(resolverCode)) {
            return null;
        }
        return resolvers.stream()
                .filter(item -> item.descriptor().code()
                        .equalsIgnoreCase(resolverCode))
                .findFirst()
                .orElse(null);
    }

    private List<SysUser> resolveDirectUsers(List<String> values) {
        LinkedHashMap<String, SysUser> users = new LinkedHashMap<>();
        for (String value : values) {
            SysUser user = userMapper.selectByUsername(value);
            if (user == null) {
                user = userMapper.selectById(value);
            }
            if (user != null
                    && SysUser.Status.ENABLED.getValue()
                    .equals(user.getStatus())
                    && !Integer.valueOf(1).equals(user.getDeleted())) {
                users.putIfAbsent(user.getUsername(), user);
            }
        }
        return new ArrayList<>(users.values());
    }

    private List<SysUser> resolveRoles(List<String> values) {
        LinkedHashMap<String, SysUser> users = new LinkedHashMap<>();
        for (String value : values) {
            List<SysRole> roles = roleMapper.selectList(
                    new LambdaQueryWrapper<SysRole>()
                            .and(wrapper -> wrapper
                                    .eq(SysRole::getId, value)
                                    .or()
                                    .eq(SysRole::getRoleCode, value))
                            .eq(SysRole::getStatus,
                                    SysRole.Status.ENABLED.getValue())
                            .eq(SysRole::getDeleted, 0));
            for (SysRole role : roles) {
                if (role == null
                        || !SysRole.Status.ENABLED.getValue()
                        .equals(role.getStatus())
                        || Integer.valueOf(1).equals(role.getDeleted())) {
                    continue;
                }
                resolveDirectUsers(
                        userRoleMapper.selectUserIdsByRoleId(role.getId()))
                        .forEach(user ->
                                users.putIfAbsent(
                                        user.getUsername(), user));
            }
        }
        return new ArrayList<>(users.values());
    }

    private List<SysUser> resolveGroups(List<String> values) {
        LinkedHashMap<String, SysUser> users = new LinkedHashMap<>();
        for (String value : values) {
            List<SysGroup> groups = groupMapper.selectList(
                    new LambdaQueryWrapper<SysGroup>()
                            .and(wrapper -> wrapper
                                    .eq(SysGroup::getId, value)
                                    .or()
                                    .eq(SysGroup::getGroupCode, value))
                            .eq(SysGroup::getStatus,
                                    SysGroup.Status.ENABLED.getValue())
                            .eq(SysGroup::getDeleted, 0));
            for (SysGroup group : groups) {
                if (group == null
                        || !SysGroup.Status.ENABLED.getValue()
                        .equals(group.getStatus())
                        || Integer.valueOf(1).equals(group.getDeleted())) {
                    continue;
                }
                resolveDirectUsers(
                        userGroupMapper.selectUserIdsByGroupId(group.getId()))
                        .forEach(user ->
                                users.putIfAbsent(
                                        user.getUsername(), user));
            }
        }
        return new ArrayList<>(users.values());
    }

    private List<SysUser> resolveOrganizations(List<String> values) {
        List<String> ids = new ArrayList<>();
        for (String value : values) {
            SysOrganization organization =
                    organizationMapper.selectById(value);
            if (organization == null) {
                organization = organizationMapper.selectByCode(value);
            }
            if (organization != null
                    && "0".equals(organization.getStatus())) {
                ids.add(organization.getId());
            }
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus,
                        SysUser.Status.ENABLED.getValue())
                .eq(SysUser::getDeleted, 0)
                .and(wrapper -> wrapper
                        .in(SysUser::getDeptId, ids)
                        .or()
                        .in(SysUser::getOrgId, ids)));
    }
}
