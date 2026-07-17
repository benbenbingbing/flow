package com.workflow.dto.permission;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限规则 - 匹配条件配置
 */
@Data
public class MatchConfigDTO {

    /** 结构化配置版本 */
    private Integer version = 1;

    /** 匹配逻辑：AND / OR */
    private String logic = "OR";

    /** 匹配条件列表 */
    private List<MatchConditionDTO> conditions;

    /**
     * 可选的结构化根节点。未配置时兼容旧版 logic + conditions。
     */
    private MatchNodeDTO root;

    /**
     * 单条匹配条件
     */
    @Data
    public static class MatchConditionDTO {

        /** 范围类型：ROLE / GROUP / DEPT / ORG / USER / ALL_USERS */
        private String scopeType;

        /** 目标ID列表 */
        private List<String> targetIds;

        /** 匹配运算符：ANY(任一) / ALL(全部) */
        private String operator = "ANY";

        /** 是否包含下级节点（DEPT/ORG 类型时有效） */
        private Boolean includeSubDept;
    }

    /**
     * 结构化匹配节点。
     */
    @Data
    public static class MatchNodeDTO {
        /** GROUP / CONDITION */
        private String type;
        /** GROUP 节点逻辑：AND / OR */
        private String logic;
        /** 子节点 */
        private List<MatchNodeDTO> children = new ArrayList<>();
        /** 条件节点内容 */
        private MatchConditionDTO condition;
    }
}
