package com.workflow.admin.identity.infrastructure.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflow.contracts.identity.port.IdentityMembershipPort;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** 身份所属模块统一展开用户、角色、用户组和组织，保留原有启用状态及软删除过滤。 */
@Component
@RequiredArgsConstructor
public class IdentityMembershipAdapter implements IdentityMembershipPort {
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysGroupMapper groupMapper;
    private final SysUserGroupMapper userGroupMapper;
    private final SysOrganizationMapper organizationMapper;

    /** 返回可分配的本地用户名；按原查询顺序保留结果，跨主体去重由调用方完成。 */
    @Override
    public List<String> users(List<String> keys) {
        return resolveDirectUsers(keys == null ? List.of() : keys).stream().map(SysUser::getUsername).toList();
    }

    /** 返回可分配的本地用户名；按原查询顺序保留结果，跨主体去重由调用方完成。 */
    @Override
    public List<String> roles(List<String> keys) {
        return resolveRoles(keys == null ? List.of() : keys).stream().map(SysUser::getUsername).toList();
    }

    /** 返回可分配的本地用户名；按原查询顺序保留结果，跨主体去重由调用方完成。 */
    @Override
    public List<String> groups(List<String> keys) {
        return resolveGroups(keys == null ? List.of() : keys).stream().map(SysUser::getUsername).toList();
    }

    /** 返回可分配的本地用户名；按原查询顺序保留结果，跨主体去重由调用方完成。 */
    @Override
    public List<String> organizations(List<String> keys) {
        return resolveOrganizations(keys == null ? List.of() : keys).stream().map(SysUser::getUsername).toList();
    }

    /**
     * 优先按用户名解析，未找到时兼容用户 ID；停用或删除的账号不能成为流程候选人。
     *
     * @param values 用户名或用户 ID
     * @return 按首次出现顺序去重的可用用户
     */
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

    /**
     * 展开启用角色的成员，并再次应用用户状态过滤。
     *
     * @param values 角色编码或角色 ID
     * @return 系统用户集合，供调用方遍历或展示
     */
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

    /**
     * 展开启用用户组的成员，并再次应用用户状态过滤。
     *
     * @param values 用户组编码或用户组 ID
     * @return 系统用户集合，供调用方遍历或展示
     */
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

    /**
     * 查询归属指定启用组织或部门的可用用户；不递归扩展子组织。
     *
     * @param values 组织编码或组织 ID
     * @return 系统用户集合，供调用方遍历或展示
     */
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
