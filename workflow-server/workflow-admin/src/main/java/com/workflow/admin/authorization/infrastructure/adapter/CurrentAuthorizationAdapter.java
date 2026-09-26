package com.workflow.admin.authorization.infrastructure.adapter;

import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.contracts.identity.port.CurrentAuthorizationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 复用管理模块既有角色判定，避免调用方复制管理员识别规则。 */
@Component
@RequiredArgsConstructor
public class CurrentAuthorizationAdapter implements CurrentAuthorizationPort {
    private final CurrentUserRoleService roles;

    /** 委托原有授权实现，保留角色缓存及管理员识别语义。 */
    @Override
    public boolean isAdministrator() {
        return roles.isAdministrator();
    }
}
