package com.workflow.process.cc.application;

import java.util.Map;

/**
 * 知会通知的可靠投递载荷。
 */
public record CcNotificationPayload(
        String ccRecordId,
        String channel,
        Map<String, Object> message) {
}
