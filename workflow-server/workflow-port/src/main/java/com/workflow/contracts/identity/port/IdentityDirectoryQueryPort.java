package com.workflow.contracts.identity.port;

import com.workflow.contracts.identity.model.IdentityDirectoryModels.*;
import com.workflow.contracts.identity.model.IdentityGroup;
import com.workflow.contracts.identity.model.IdentityUser;

import java.util.List;
import java.util.Optional;

/** 面向业务模块的身份目录浏览，复用系统目录权限。 */
public interface IdentityDirectoryQueryPort {
    /** 根据用户筛选条件查询目录页。 */
    UserPage pageUsers(UserQuery query);

    /** 通过准确 ID 批量取目录详情，保持输入顺序去重，最多 100 个。 */
    List<IdentityUser> findUsersByIds(List<String> userIds);

    /** 通过准确用户名批量取目录详情，保持输入顺序去重，最多 100 个。 */
    List<IdentityUser> findUsersByUsernames(List<String> usernames);

    /** 查找已完成父链关联的组织或部门。 */
    Optional<Organization> findOrganization(String organizationId);

    /** 按父 ID 延迟展开组织树；空值时返回根层。 */
    List<Organization> listOrganizationChildren(String parentId, String type);

    /** 按目录的组织或部门类型读取树，未指定时读取有效组织树。 */
    List<OrganizationNode> listOrganizationTree(String type);

    /** 返回由根至目标有序的父链，包含目标节点。 */
    List<Organization> listOrganizationAncestors(String organizationId);

    /** 列出角色目录，enabledOnly 可限定业务分配可选项。 */
    List<Role> listRoles(boolean enabledOnly);

    /** 反查给定用户的角色。 */
    List<Role> listUserRoles(String userId);

    /** 反查给定用户当前所属的有效用户组。 */
    List<IdentityGroup> listUserGroups(String userId);
}
