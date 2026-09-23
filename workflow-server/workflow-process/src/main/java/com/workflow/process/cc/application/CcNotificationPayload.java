package com.workflow.process.cc.application;

import java.util.Map;

/**
 * 知会通知的可靠投递载荷。
 *
 * @param ccRecordId 抄送记录ID，后续用于处理抄送通知载荷时定位或关联目标
 * @param channel 通道，保存在对象中供后续校验、查询或展示
 * @param message 消息，保存在对象中供后续校验、查询或展示
 */
public record CcNotificationPayload(
        String ccRecordId,
        String channel,
        Map<String, Object> message) {
}
