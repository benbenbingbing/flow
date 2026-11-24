package com.workflow.port.system;

import com.workflow.port.system.model.identity.IdentityDirectoryModels.*;
import java.util.List;

/** 查询当前身份有权查看的角色目录。 */
public interface SysRolePort {
    /** 列出角色目录，enabledOnly 可限定业务分配可选项。 */
    List<Role> listRoles(boolean enabledOnly);
}
