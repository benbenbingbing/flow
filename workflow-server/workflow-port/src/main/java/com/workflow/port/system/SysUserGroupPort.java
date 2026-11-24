package com.workflow.port.system;

import com.workflow.port.system.model.identity.IdentityDirectoryModels.*;
import com.workflow.port.system.model.identity.IdentityGroup;
import com.workflow.port.system.model.identity.IdentityUser;
import java.util.List;

/** 查询用户与分组的关系，或替换分组成员，由调用方授权。 */
public interface SysUserGroupPort {
    /** 反查给定用户当前所属的有效用户组。 */
    List<IdentityGroup> listUserGroups(String userId);

    /**
     * 查询分组用户集合；查询结果供调用方展示或继续处理。
     *
     * @param idOrCode ID或编码，后续用于查询分组用户集合时定位或关联目标
     * @return 身份用户集合，供调用方遍历或展示
     */
    List<IdentityUser> findGroupUsers(String idOrCode);

    /** 写操作。替换用户组的完整成员集合；空集合表示清空。 */
    void replaceGroupMembers(String groupId, List<String> userIds);
}
