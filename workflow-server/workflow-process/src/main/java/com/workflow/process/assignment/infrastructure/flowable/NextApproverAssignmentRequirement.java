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

    private NextApproverAssignmentRequirement() {
    }

    static boolean isRequired(Map<String, Object> assigneeConfig) {
        if (assigneeConfig == null) {
            return false;
        }
        Object raw = assigneeConfig.get("nextApproverSelection");
        if (!(raw instanceof Map<?, ?> selection)) {
            return false;
        }
        Object visibility = firstNonNull(
                selection, "visible", "show", "display");
        return visibility != null
                && Boolean.parseBoolean(String.valueOf(visibility));
    }

    /**
     * JSON 已损坏时无法可信读取 visible；只要原文声明了该策略就应失败关闭。
     */
    static boolean declaresSelection(String document) {
        return StringUtils.hasText(document)
                && SELECTION_KEY.matcher(document).find();
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
