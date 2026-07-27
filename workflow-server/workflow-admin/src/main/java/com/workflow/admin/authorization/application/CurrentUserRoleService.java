package com.workflow.admin.authorization.application;

import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.ForbiddenException;
import com.workflow.admin.authorization.role.infrastructure.persistence.record.SysRole;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 当前登录用户角色判断服务
 * <p>
 * 基于当前线程上下文中的用户ID，查询其角色，判断是否为超级管理员或管理员，
 * 并提供权限不足时抛出 {@link ForbiddenException} 的快捷方法。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class CurrentUserRoleService {

    /** 角色 Mapper，用于查询用户角色 */
    private final SysRoleMapper roleMapper;

    /**
     * 判断当前登录用户是否为超级管理员（roleCode 为 super_admin）
     *
     * @return 是超级管理员返回 true；未登录或不是则返回 false
     */
    public boolean isSuperAdmin() {
        String userId = UserContext.getUserId();
        if (userId == null || userId.isBlank()) {
            return false;
        }
        List<SysRole> roles = roleMapper.selectRolesByUserId(userId);
        return roles.stream().anyMatch(role -> "super_admin".equals(role.getRoleCode()));
    }

    /**
     * 判断当前登录用户是否为管理员（super_admin 或 admin）
     *
     * @return 是管理员返回 true；未登录或不是则返回 false
     */
    public boolean isAdministrator() {
        String userId = UserContext.getUserId();
        if (userId == null || userId.isBlank()) {
            return false;
        }
        List<SysRole> roles = roleMapper.selectRolesByUserId(userId);
        return roles.stream().anyMatch(role ->
                "super_admin".equals(role.getRoleCode()) || "admin".equals(role.getRoleCode()));
    }

    /**
     * 校验当前用户是否为超级管理员，不是则抛出禁止访问异常
     *
     * @throws ForbiddenException 当前用户不是超级管理员时抛出
     */
    public void requireSuperAdmin() {
        if (!isSuperAdmin()) {
            throw new ForbiddenException("仅超级管理员可以查看或操作流程动作执行日志");
        }
    }

    /**
     * 校验当前用户是否为管理员，不是则抛出禁止访问异常
     *
     * @param message 抛出异常时使用的提示信息
     * @throws ForbiddenException 当前用户不是管理员时抛出
     */
    public void requireAdministrator(String message) {
        if (!isAdministrator()) {
            throw new ForbiddenException(message);
        }
    }
}
