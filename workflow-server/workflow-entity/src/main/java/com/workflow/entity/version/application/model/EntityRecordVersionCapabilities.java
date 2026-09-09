package com.workflow.entity.version.application.model;

/**
 * 实体记录版本的当前运行时能力。
 *
 * @param runtimeEnabled 当前版本策略是否启用
 * @param manualCaptureEnabled 当前策略是否支持手工固化
 * @param historyReadable 该实体是否已有可读取的历史数据版本
 */
public record EntityRecordVersionCapabilities(
        boolean runtimeEnabled,
        boolean manualCaptureEnabled,
        boolean historyReadable) {

    /** 无当前配置时仅保留历史版本可读能力。 */
    public static EntityRecordVersionCapabilities disabled(
            boolean historyReadable) {
        return new EntityRecordVersionCapabilities(
                false, false, historyReadable);
    }
}
