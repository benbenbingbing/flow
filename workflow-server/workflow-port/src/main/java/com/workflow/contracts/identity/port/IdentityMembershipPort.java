package com.workflow.contracts.identity.port;

import java.util.List;

/** 将身份主体展开为可分配的本地用户名；禁止向流程模块暴露用户表和成员关联表。 */
public interface IdentityMembershipPort {
    /** 按用户名优先、ID 兜底查找启用且未删除的用户；保持输入顺序并去重。 */
    List<String> users(List<String> keys);

    /** 展开启用且未删除的角色（ID 或编码），只返回有效用户。 */
    List<String> roles(List<String> keys);

    /** 展开启用且未删除的用户组（ID 或编码），只返回有效用户。 */
    List<String> groups(List<String> keys);

    /** 展开启用的组织（ID 或编码），包含归属部门或组织匹配的有效用户。 */
    List<String> organizations(List<String> keys);
}
