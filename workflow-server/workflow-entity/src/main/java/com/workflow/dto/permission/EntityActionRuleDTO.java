package com.workflow.dto.permission;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 实体列表按钮适用条件。
 */
@Data
public class EntityActionRuleDTO {
    /** 结构化配置版本 */
    private Integer version = 1;
    /** 不满足条件时的行为：HIDE / DISABLE */
    private String unavailableBehavior = "HIDE";
    /** 不满足条件时的提示信息 */
    private String message;
    /** 规则树根节点 */
    private RuleNode root;

    /**
     * 规则树节点，支持嵌套分组（GROUP）与单条条件（CONDITION）。
     */
    @Data
    public static class RuleNode {
        /** 节点类型：GROUP / CONDITION */
        private String type;
        /** GROUP 节点逻辑：AND / OR */
        private String logic;
        /** 子节点列表（GROUP 类型时使用） */
        private List<RuleNode> children = new ArrayList<>();
        /** 关联关系（CONDITION 类型时使用的关联键） */
        private String relation;
        /** 比较字段 */
        private String field;
        /** 比较运算符（EQ/NE/IN 等） */
        private String operator;
        /** 比较值 */
        private Object value;
    }
}
