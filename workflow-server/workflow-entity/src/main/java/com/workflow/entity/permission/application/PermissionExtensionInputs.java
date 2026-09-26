package com.workflow.entity.permission.application;

import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.contracts.identity.model.IdentityUser;

/** 宿主在 SPI 调用边界构造最小身份投影，不把密码、会话和持久化状态传给插件。 */
final class PermissionExtensionInputs {
    private PermissionExtensionInputs() {}

    /** 工具栏或未认证场景保持 null；其他场景复制权限扩展需要的身份和组织字段。 */
    static IdentityUser user(SysUser source) {
        return source == null ? null : new IdentityUser(source.getId(), source.getUsername(),
                source.getNickname(), source.getOrgId(), source.getDeptId());
    }
}
