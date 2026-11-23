package com.workflow.admin.security.context;

import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.admin.authorization.application.PermissionUtil;
import lombok.RequiredArgsConstructor;

import com.workflow.port.system.identity.model.CurrentActor;
import com.workflow.port.system.CurrentActorPort;
import org.springframework.stereotype.Component;

/** 使用认证上下文与现有角色判定实现跨模块身份和权限查询。 */
@Component("userContextCurrentActorProvider")
@RequiredArgsConstructor
public class UserContextCurrentActorProvider implements CurrentActorPort {
    private final CurrentUserRoleService roles;

    @Override
    public CurrentActor current() {
        return new CurrentActor(UserContext.getUserId(), UserContext.getUsername());
    }

    @Override
    public boolean isAdministrator() {
        return roles.isAdministrator();
    }

    @Override
    public boolean hasPermission(String permissionCode) {
        return permissionCode != null && !permissionCode.isBlank() && PermissionUtil.hasPermission(permissionCode);
    }
}
