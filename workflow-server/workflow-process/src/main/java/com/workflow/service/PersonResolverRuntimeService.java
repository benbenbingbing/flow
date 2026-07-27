package com.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflow.contracts.identity.resolver.PersonPrincipal;
import com.workflow.contracts.identity.resolver.PersonResolveRequest;
import com.workflow.contracts.identity.resolver.PersonResolveUsage;
import com.workflow.contracts.identity.resolver.PersonResolver;
import com.workflow.entity.SysGroup;
import com.workflow.entity.SysOrganization;
import com.workflow.entity.SysRole;
import com.workflow.entity.SysUser;
import com.workflow.mapper.SysGroupMapper;
import com.workflow.mapper.SysOrganizationMapper;
import com.workflow.mapper.SysRoleMapper;
import com.workflow.mapper.SysUserGroupMapper;
import com.workflow.mapper.SysUserMapper;
import com.workflow.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
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

    public boolean supports(String resolverCode, PersonResolveUsage usage) {
        PersonResolver resolver = find(resolverCode);
        return resolver != null
                && (resolver.descriptor().supportedUsages().isEmpty()
                || resolver.descriptor().supportedUsages().contains(usage));
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

        LinkedHashMap<String, SysUser> users = new LinkedHashMap<>();
        for (PersonPrincipal principal :
                resolver.resolve(request).principals()) {
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
                            .eq(SysRole::getDeleted, 0));
            for (SysRole role : roles) {
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
                            .eq(SysGroup::getDeleted, 0));
            for (SysGroup group : groups) {
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
