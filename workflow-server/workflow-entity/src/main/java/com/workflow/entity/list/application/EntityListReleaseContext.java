package com.workflow.entity.list.application;

/**
 * 列表运行时发布版本上下文。
 */
public record EntityListReleaseContext(
        String releaseId,
        Integer releaseVersion,
        String releaseResolutionToken) {

    public static EntityListReleaseContext current() {
        return new EntityListReleaseContext(null, null, null);
    }
}
