package com.workflow.port.system;

import com.workflow.port.system.model.identity.IdentityDirectoryModels.*;
import java.util.List;

/** 查询和替换系统用户角色关系，由调用方授权。 */
public interface SysUserRolePort {
    /** 反查给定用户的角色。 */
    List<Role> listUserRoles(String userId);

    /** 写操作。原子替换指定用户的角色集合；空角色集合表示清空。 */
    void replaceUserRoles(List<String> userIds, List<String> roleIds);
}
