package com.workflow.dto.permission;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 实体列表按钮适用条件。
 */
@Data
public class EntityActionRuleDTO {
    private Integer version = 1;
    private String unavailableBehavior = "HIDE";
    private String message;
    private RuleNode root;

    @Data
    public static class RuleNode {
        private String type;
        private String logic;
        private List<RuleNode> children = new ArrayList<>();
        private String relation;
        private String field;
        private String operator;
        private Object value;
    }
}
