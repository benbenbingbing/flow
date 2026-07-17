package com.workflow.common;

import com.workflow.mapper.SysMenuMapper;
import com.workflow.mapper.SysRoleMenuMapper;
import com.workflow.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 权限码校验工具
 */
@Component
@RequiredArgsConstructor
public class PermissionUtil {

    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysMenuMapper menuMapper;

    private static SysUserRoleMapper staticUserRoleMapper;
    private static SysRoleMenuMapper staticRoleMenuMapper;
    private static SysMenuMapper staticMenuMapper;

    @PostConstruct
    public void init() {
        staticUserRoleMapper = userRoleMapper;
        staticRoleMenuMapper = roleMenuMapper;
        staticMenuMapper = menuMapper;
    }

    /**
     * 获取当前用户的所有权限码
     */
    public static Set<String> getCurrentUserPermissions() {
        String userId = UserContext.getUserId();
        if (userId == null) {
            return Collections.emptySet();
        }
        return getUserPermissions(userId);
    }

    /**
     * 获取指定用户的所有权限码
     */
    public static Set<String> getUserPermissions(String userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        Set<String> perms = new HashSet<>();
        perms.addAll(staticMenuMapper.selectPermsByUserId(userId));
        return perms;
    }

    /**
     * 校验当前用户是否拥有指定权限码
     */
    public static void checkPermission(String perm) {
        if (!hasPermission(perm)) {
            throw new RuntimeException("没有权限执行该操作：" + perm);
        }
    }

    /**
     * 校验当前用户是否拥有任意一个权限码
     */
    public static void checkAnyPermission(Collection<String> perms) {
        if (perms == null || perms.isEmpty()) {
            return;
        }
        if (!hasAnyPermission(perms)) {
            throw new RuntimeException("没有权限执行该操作");
        }
    }

    /**
     * 判断当前用户是否拥有指定权限码
     */
    public static boolean hasPermission(String perm) {
        if (perm == null || perm.isEmpty()) {
            return true;
        }
        return getCurrentUserPermissions().contains(perm);
    }

    /**
     * 判断当前用户是否拥有任意一个权限码
     */
    public static boolean hasAnyPermission(Collection<String> perms) {
        if (perms == null || perms.isEmpty()) {
            return true;
        }
        Set<String> userPerms = getCurrentUserPermissions();
        return perms.stream().anyMatch(userPerms::contains);
    }
}
