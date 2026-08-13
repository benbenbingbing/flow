package com.workflow.entity.form.application;

/**
 * 表单运行时发布版本上下文。
 */
public record EntityFormReleaseContext(
        String releaseId,
        Integer releaseVersion,
        String releaseResolutionToken) {

    public static EntityFormReleaseContext current() {
        return new EntityFormReleaseContext(null, null, null);
    }
}
