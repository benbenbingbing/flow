package com.workflow.entity.version.application.model;

/**
 * 实体记录版本的当前运行时能力。
 *
 * @param runtimeEnabled 已发布版本策略是否启用
 * @param manualCaptureEnabled 已发布策略是否支持手工固化
 */
public record EntityRecordVersionCapabilities(
        boolean runtimeEnabled,
        boolean manualCaptureEnabled) {

    /** 无已发布配置或已发布策略停用时的统一结果。 */
    public static EntityRecordVersionCapabilities disabled() {
        return new EntityRecordVersionCapabilities(false, false);
    }
}
