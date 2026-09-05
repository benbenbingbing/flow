package com.workflow.config;

import com.workflow.admin.authorization.menu.infrastructure.persistence.mapper.SysMenuMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.record.SysRole;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.embed.runtime.port.EmbedNativeActorRuntimePort;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 从现有账号、角色和菜单权限链构造 Embed 原生 UI 快照。 */
@Component
public class EmbedNativeActorRuntimeAdapter
        implements EmbedNativeActorRuntimePort {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;

    public EmbedNativeActorRuntimeAdapter(
            SysUserMapper userMapper,
            SysRoleMapper roleMapper,
            SysMenuMapper menuMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.menuMapper = menuMapper;
    }

    /**
     * 只允许读取已由 Embed 认证桥写入 UserContext 的同一用户，
     * 防止将该端口误用成通用权限查询器。
     */
    @Override
    public ActorSnapshot resolve(
            String flowUserId,
            String expectedUsername,
            String fallbackDisplayName) {
        if (!StringUtils.hasText(flowUserId)
                || !Objects.equals(flowUserId, UserContext.getUserId())) {
            throw new IllegalStateException("Embed 映射用户上下文不一致");
        }
        SysUser user = userMapper.selectById(flowUserId);
        if (user == null || !StringUtils.hasText(user.getUsername())
                || StringUtils.hasText(expectedUsername)
                && !Objects.equals(expectedUsername, user.getUsername())) {
            throw new IllegalStateException("Embed 映射用户不可用");
        }
        List<String> roles = safeRoles(roleMapper.selectRolesByUserId(flowUserId));
        boolean superAdmin = roles.contains("super_admin");
        Set<String> selected = menuMapper.selectPermsByUserId(flowUserId);
        List<String> permissions = selected == null
                ? List.of()
                : selected.stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .distinct()
                        .sorted()
                        .toList();
        String nickname = StringUtils.hasText(user.getNickname())
                ? user.getNickname().trim() : user.getUsername();
        String displayName = StringUtils.hasText(fallbackDisplayName)
                ? fallbackDisplayName.trim() : nickname;
        return new ActorSnapshot(
                user.getUsername(), nickname, displayName,
                roles, superAdmin, permissions);
    }

    private static List<String> safeRoles(List<SysRole> source) {
        if (source == null) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        source.stream()
                .map(SysRole::getRoleCode)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .sorted()
                .forEach(result::add);
        return List.copyOf(result);
    }
}
