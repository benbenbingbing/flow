package com.workflow.entity.permission.api.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 实体按钮的显示与启用条件。
 *
 * <p>两棵规则树互相独立：{@code visibleWhen} 决定按钮是否显示，
 * {@code enabledWhen} 仅在按钮可见后决定是否可操作。空规则树表示无额外限制。</p>
 */
@Data
public class EntityActionRuleDTO {
    /** 结构化配置版本 */
    private Integer version = 2;
    /** 显示条件根节点；不满足时隐藏按钮 */
    private RuleNode visibleWhen;
    /** 启用条件根节点；不满足时禁用按钮 */
    private RuleNode enabledWhen;
    /** 启用条件不满足时的提示信息 */
    private String disabledMessage = "";

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
