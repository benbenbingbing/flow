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

    /**
     * 初始化下一步审批人分配{@code requirement}，保存构造参数供后续方法使用。
     */
    private NextApproverAssignmentRequirement() {
    }

    /**
     * 判断是否必填；判断结果决定调用方的后续分支。
     *
     * @param assigneeConfig 办理人配置内容，决定后续必填的处理规则
     * @return 必填条件成立时为 true，否则为 false
     */
    static boolean isRequired(Map<String, Object> assigneeConfig) {
        return flag(assigneeConfig, "visible", "show", "display");
    }

    /**
     * 启动预计算时可编辑节点允许暂时没有默认人员，由前序人工覆盖补齐。
     * 该标志不放宽活动真正进入时的空集合门禁。
     *
     * @param assigneeConfig 办理人配置内容，决定后续可编辑的处理规则
     * @return 可编辑条件成立时为 true，否则为 false
     */
    static boolean isEditable(Map<String, Object> assigneeConfig) {
        return flag(
                assigneeConfig,
                "editable",
                "allowModify",
                "allowEdit");
    }

    /**
     * 标记下一步审批人分配{@code requirement}；后续读取或执行将使用更新后的状态。
     *
     * @param assigneeConfig 办理人配置内容，决定后续下一步审批人分配{@code requirement}的处理规则
     * @param names 名称集合，作为 {@code firstNonNull} 的输入影响后续处理
     * @return 下一步审批人分配{@code requirement}条件成立时为 true，否则为 false
     */
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
     *
     * @param document 文档，供本方法处理需要失败{@code closed}时使用
     * @return 需要失败{@code closed}条件成立时为 true，否则为 false
     */
    static boolean requiresFailClosed(String document) {
        return StringUtils.hasText(document)
                && (SELECTION_KEY.matcher(document).find()
                || VERSION_TWO.matcher(document).find());
    }

    /**
     * 处理首个非空值，并将结果传给后续步骤。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @param names 名称集合，供本方法处理首个非空值时使用
     * @return 处理后的首个非空值结果，供调用方继续处理
     */
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
