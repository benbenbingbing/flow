package com.workflow.contracts.entity;

/**
 * 流程发布快照中的实体表单绑定。
 *
 * @param nodeId             流程节点 ID
 * @param formId             表单 ID
 * @param formReleaseId      流程发布时固定的表单发布 ID
 * @param formReleaseVersion 流程发布时固定的表单版本
 */
public record EntityFormBinding(
        String nodeId,
        String formId,
        String formReleaseId,
        Integer formReleaseVersion) {
}
