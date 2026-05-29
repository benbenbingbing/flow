package com.workflow.dto.permission;

import lombok.Data;

import java.util.List;

/**
 * 权限规则 - 匹配条件配置
 */
@Data
public class MatchConfigDTO {

    /** 匹配逻辑：AND / OR */
    private String logic = "OR";

    /** 匹配条件列表 */
    private List<MatchConditionDTO> conditions;

    /**
     * 单条匹配条件
     */
    @Data
    public static class MatchConditionDTO {

        /** 范围类型：ROLE / DEPT / USER / ALL_USERS / EXPRESSION */
        private String scopeType;

        /** 目标ID列表 */
        private List<String> targetIds;

        /** 匹配运算符：ANY(任一) / ALL(全部) */
        private String operator = "ANY";

        /** 是否包含子部门（DEPT类型时有效） */
        private Boolean includeSubDept;

        /** Groovy表达式（scopeType=EXPRESSION时有效） */
        private String expression;
    }
}
