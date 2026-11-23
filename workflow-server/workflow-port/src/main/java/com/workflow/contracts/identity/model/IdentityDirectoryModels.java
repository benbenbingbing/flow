package com.workflow.contracts.identity.model;

import java.util.List;

/** 目录查询模型只携带跨模块选择、归属和授权匹配所需的信息。 */
public final class IdentityDirectoryModels {
    private IdentityDirectoryModels() { }

    /** 传空筛选查询当前目录范围；页大小最大 100。 */
    public record UserQuery(int pageNumber, int pageSize, String keyword, String status,
            String organizationId, String departmentId, String roleId, String positionCode) { }

    /** 用户查询分页；total 表示同样筛选条件下的总数。 */
    public record UserPage(List<IdentityUser> users, long total, long pageNumber, long pageSize) {
        public UserPage { users = List.copyOf(users); }
    }

    /** 组织坐标和业务层级供规则匹配，parentId 用于延迟展开组织树。 */
    public record Organization(String id, String code, String name, String type, String parentId,
            String businessLevelCode, String status) { }

    /** 可一次加载的组织子树；大型目录也可使用直属下级接口分段读取。 */
    public record OrganizationNode(Organization organization, List<OrganizationNode> children) {
        public OrganizationNode { children = List.copyOf(children); }
    }

    /** 角色最小目录信息，code 可跨环境使用。 */
    public record Role(String id, String code, String name, String status) { }
}
