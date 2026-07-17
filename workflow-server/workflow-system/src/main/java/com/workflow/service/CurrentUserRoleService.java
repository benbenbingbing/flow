package com.workflow.service;

import com.workflow.common.UserContext;
import com.workflow.common.ForbiddenException;
import com.workflow.entity.SysRole;
import com.workflow.mapper.SysRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CurrentUserRoleService {

    private final SysRoleMapper roleMapper;

    public boolean isSuperAdmin() {
        String userId = UserContext.getUserId();
        if (userId == null || userId.isBlank()) {
            return false;
        }
        List<SysRole> roles = roleMapper.selectRolesByUserId(userId);
        return roles.stream().anyMatch(role -> "super_admin".equals(role.getRoleCode()));
    }

    public boolean isAdministrator() {
        String userId = UserContext.getUserId();
        if (userId == null || userId.isBlank()) {
            return false;
        }
        List<SysRole> roles = roleMapper.selectRolesByUserId(userId);
        return roles.stream().anyMatch(role ->
                "super_admin".equals(role.getRoleCode()) || "admin".equals(role.getRoleCode()));
    }

    public void requireSuperAdmin() {
        if (!isSuperAdmin()) {
            throw new ForbiddenException("仅超级管理员可以查看或操作流程动作执行日志");
        }
    }

    public void requireAdministrator(String message) {
        if (!isAdministrator()) {
            throw new ForbiddenException(message);
        }
    }
}
