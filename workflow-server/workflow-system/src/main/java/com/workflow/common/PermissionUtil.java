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
 * <p>
 * 通过用户-角色-菜单关联查询用户的权限标识（perm）集合，
 * 提供静态方法供业务层校验当前用户是否拥有指定权限码。
 * 由于工具类以静态方法对外暴露，Bean 注入的 Mapper 通过 @PostConstruct 转存到静态字段。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class PermissionUtil {

    /** 用户角色关联 Mapper（Spring 注入） */
    private final SysUserRoleMapper userRoleMapper;
    /** 角色菜单关联 Mapper（Spring 注入） */
    private final SysRoleMenuMapper roleMenuMapper;
    /** 菜单 Mapper（Spring 注入） */
    private final SysMenuMapper menuMapper;

    /** 静态化的用户角色关联 Mapper，供静态方法使用 */
    private static SysUserRoleMapper staticUserRoleMapper;
    /** 静态化的角色菜单关联 Mapper，供静态方法使用 */
    private static SysRoleMenuMapper staticRoleMenuMapper;
    /** 静态化的菜单 Mapper，供静态方法使用 */
    private static SysMenuMapper staticMenuMapper;

    /**
     * Bean 初始化时将注入的 Mapper 转存到静态字段
     */
    @PostConstruct
    public void init() {
        staticUserRoleMapper = userRoleMapper;
        staticRoleMenuMapper = roleMenuMapper;
        staticMenuMapper = menuMapper;
    }

    /**
     * 获取当前用户的所有权限码
     *
     * @return 当前用户权限码集合，未登录返回空集合
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
     *
     * @param userId 用户ID
     * @return 权限码集合，userId 为空返回空集合
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
     * 校验当前用户是否拥有指定权限码，不通过则抛出异常
     *
     * @param perm 权限码
     * @throws RuntimeException 当前用户无该权限时抛出
     */
    public static void checkPermission(String perm) {
        if (!hasPermission(perm)) {
            throw new RuntimeException("没有权限执行该操作：" + perm);
        }
    }

    /**
     * 校验当前用户是否拥有任意一个权限码，不通过则抛出异常
     *
     * @param perms 候选权限码集合，为空时不校验
     * @throws RuntimeException 当前用户无任一权限时抛出
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
     *
     * @param perm 权限码，为空视为无需权限
     * @return 拥有权限返回 true，否则 false
     */
    public static boolean hasPermission(String perm) {
        if (perm == null || perm.isEmpty()) {
            return true;
        }
        return getCurrentUserPermissions().contains(perm);
    }

    /**
     * 判断当前用户是否拥有任意一个权限码
     *
     * @param perms 候选权限码集合，为空视为无需权限
     * @return 拥有任一权限返回 true，否则 false
     */
    public static boolean hasAnyPermission(Collection<String> perms) {
        if (perms == null || perms.isEmpty()) {
            return true;
        }
        Set<String> userPerms = getCurrentUserPermissions();
        return perms.stream().anyMatch(userPerms::contains);
    }
}
