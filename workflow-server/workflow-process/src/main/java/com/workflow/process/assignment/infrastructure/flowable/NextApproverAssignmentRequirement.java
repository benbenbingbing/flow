package com.workflow.process.assignment.infrastructure.flowable;

import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 运行时人员分配对下一审批人展示策略的安全门禁。
 */
final class NextApproverAssignmentRequirement {

    private static final Pattern SELECTION_KEY = Pattern.compile(
            "\\\"nextApproverSelection\\\"\\s*:");
    private static final Pattern VERSION_TWO = Pattern.compile(
            "\\\"assignmentConfigVersion\\\"\\s*:\\s*(?:2(?![0-9])|\\\"2\\\")");

    private NextApproverAssignmentRequirement() {
    }

    static boolean isRequired(Map<String, Object> assigneeConfig) {
        return flag(assigneeConfig, "visible", "show", "display");
    }

    /**
     * 启动预计算时可编辑节点允许暂时没有默认人员，由前序人工覆盖补齐。
     * 该标志不放宽活动真正进入时的空集合门禁。
     */
    static boolean isEditable(Map<String, Object> assigneeConfig) {
        return flag(
                assigneeConfig,
                "editable",
                "allowModify",
                "allowEdit");
    }

    private static boolean flag(
            Map<String, Object> assigneeConfig,
            String... names) {
        if (assigneeConfig == null) {
            return false;
        }
        Object raw = assigneeConfig.get("nextApproverSelection");
        if (!(raw instanceof Map<?, ?> selection)) {
            return false;
        }
        Object value = firstNonNull(selection, names);
        return value != null
                && Boolean.parseBoolean(String.valueOf(value));
    }

    /**
     * 配置 JSON 已损坏时仍从原文识别安全关键的新版本。v2 不允许沿用
     * 历史“记录错误后继续”语义，否则隐藏节点也可能创建无人任务。
     */
    static boolean requiresFailClosed(String document) {
        return StringUtils.hasText(document)
                && (SELECTION_KEY.matcher(document).find()
                || VERSION_TWO.matcher(document).find());
    }

    private static Object firstNonNull(
            Map<?, ?> values,
            String... names) {
        for (String name : names) {
            if (values.containsKey(name) && values.get(name) != null) {
                return values.get(name);
            }
        }
        return null;
    }
}
