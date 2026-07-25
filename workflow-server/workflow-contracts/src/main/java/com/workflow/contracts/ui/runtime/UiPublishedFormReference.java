package com.workflow.contracts.ui.runtime;

/**
 * 发布表单快照中固定的直接子表单引用。
 */
public record UiPublishedFormReference(
        String formId,
        String releaseId,
        Integer releaseVersion) {
}
