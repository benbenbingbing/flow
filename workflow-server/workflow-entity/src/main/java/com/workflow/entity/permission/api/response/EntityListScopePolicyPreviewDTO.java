package com.workflow.entity.permission.api.response;

import lombok.Data;

/** 单条规则的只读模拟结果，不代表列表合并其他规则后的最终访问权限。 */
@Data
public class EntityListScopePolicyPreviewDTO {

    private String policyId;
    private String ruleName;
    private String ruleEffect;
    private String userId;
    private String username;

    /** 规则是否启用；停用规则仍可编译条件供配置检查。 */
    private boolean enabled;

    /** 模拟用户是否符合规则适用对象，与记录 SQL 的匹配结果分开表示。 */
    private boolean audienceMatched;

    /** 当前规则的数据条件，未叠加列表绑定、默认策略、委托或范围绕过权限。 */
    private String sql;
}
