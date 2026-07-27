package com.workflow.process.cc.application;

import com.workflow.process.cc.infrastructure.persistence.record.ProcessCcRecord;
import com.workflow.outbox.api.OutboxPublishRequest;
import com.workflow.outbox.api.OutboxPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 将流程知会按通知渠道发布到通用 Outbox。
 */
@Service
@RequiredArgsConstructor
public class ProcessCcNotificationPublisher {

    public static final String TOPIC = "PROCESS_CC_NOTIFICATION";

    private final OutboxPublisher outboxPublisher;

    @Transactional
    public void enqueue(
            ProcessCcRecord record,
            List<String> requestedChannels) {
        List<String> channels = normalizeChannels(requestedChannels);
        for (String channel : channels) {
            outboxPublisher.publish(new OutboxPublishRequest(
                    TOPIC,
                    record.getId() + ":" + channel,
                    "PROCESS_CC_RECORD",
                    record.getId(),
                    new CcNotificationPayload(
                            record.getId(),
                            channel,
                            message(record)),
                    5));
        }
    }

    private List<String> normalizeChannels(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of("IN_APP");
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private Map<String, Object> message(ProcessCcRecord record) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(
                "processInstanceId",
                record.getProcessInstanceId());
        payload.put("processName", record.getProcessName());
        payload.put("nodeName", record.getNodeName());
        payload.put("recipient", record.getCcUserId());
        payload.put("comment", record.getComment());
        return payload;
    }
}
