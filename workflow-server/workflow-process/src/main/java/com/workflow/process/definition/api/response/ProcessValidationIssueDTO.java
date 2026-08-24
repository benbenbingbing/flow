package com.workflow.process.definition.api.response;

/**
 * 流程发布预检问题。
 *
 * @param code        稳定问题码
 * @param severity    严重级别
 * @param blocking    是否阻断发布
 * @param elementId   BPMN 元素 ID；全局问题为空
 * @param elementType BPMN 元素类型；全局问题为空
 * @param message     面向管理员的问题说明
 * @param suggestion  修复建议
 * @param fixRoute    可选修复入口
 */
public record ProcessValidationIssueDTO(
        String code,
        Severity severity,
        boolean blocking,
        String elementId,
        String elementType,
        String message,
        String suggestion,
        String fixRoute) {

    /** 预检问题级别。 */
    public enum Severity {
        BLOCKER,
        WARNING,
        INFO
    }
}
