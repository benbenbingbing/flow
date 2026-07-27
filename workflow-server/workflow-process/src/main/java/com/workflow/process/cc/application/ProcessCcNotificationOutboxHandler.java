package com.workflow.process.cc.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.cc.infrastructure.persistence.record.ProcessCcRecord;
import com.workflow.process.cc.infrastructure.persistence.mapper.ProcessCcRecordMapper;
import com.workflow.outbox.api.OutboxEvent;
import com.workflow.outbox.api.OutboxEventHandler;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 消费流程知会 Outbox 事件并路由到对应通知渠道。
 */
@Component
public class ProcessCcNotificationOutboxHandler
        implements OutboxEventHandler {

    private final ProcessCcRecordMapper recordMapper;
    private final ObjectMapper objectMapper;
    private final Map<String, CcNotificationChannel> channels;

    public ProcessCcNotificationOutboxHandler(
            ProcessCcRecordMapper recordMapper,
            ObjectMapper objectMapper,
            List<CcNotificationChannel> channels) {
        this.recordMapper = recordMapper;
        this.objectMapper = objectMapper;
        this.channels = indexChannels(channels);
    }

    @Override
    public String topic() {
        return ProcessCcNotificationPublisher.TOPIC;
    }

    @Override
    public void handle(OutboxEvent event) throws Exception {
        CcNotificationPayload payload = objectMapper.readValue(
                event.payloadDocument(),
                CcNotificationPayload.class);
        ProcessCcRecord record = recordMapper.selectById(
                payload.ccRecordId());
        if (record == null) {
            throw new IllegalStateException(
                    "知会记录不存在: " + payload.ccRecordId());
        }
        CcNotificationChannel channel = channels.get(
                payload.channel());
        if (channel == null) {
            throw new IllegalStateException(
                    "未注册通知渠道: " + payload.channel());
        }
        channel.send(
                record,
                payload.message() == null
                        ? Map.of()
                        : payload.message());
    }

    private Map<String, CcNotificationChannel> indexChannels(
            List<CcNotificationChannel> values) {
        Map<String, CcNotificationChannel> result =
                new LinkedHashMap<>();
        for (CcNotificationChannel channel : values) {
            String code = channel.channel().trim().toUpperCase();
            CcNotificationChannel previous =
                    result.putIfAbsent(code, channel);
            if (previous != null) {
                throw new IllegalStateException(
                        "知会通知渠道重复注册: " + code);
            }
        }
        return Map.copyOf(result);
    }
}
