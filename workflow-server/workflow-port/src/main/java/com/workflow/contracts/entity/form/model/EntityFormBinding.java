package com.workflow.contracts.entity.form.model;

/**
 * 流程发布快照中的实体表单绑定。
 *
 * @param nodeId             流程节点 ID
 * @param formId             表单 ID
 * @param formReleaseId      旧流程 JSON 兼容字段；当前配置按 formId 解析，不使用此坐标
 * @param formReleaseVersion 旧流程 JSON 兼容字段；新流程不保存表单版本
 */
public record EntityFormBinding(
        String nodeId,
        String formId,
        String formReleaseId,
        Integer formReleaseVersion) {
}
