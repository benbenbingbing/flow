package com.workflow.process.cc.application;

import com.workflow.process.cc.infrastructure.persistence.record.ProcessCcRecord;
import com.workflow.contracts.outbox.model.OutboxPublishRequest;
import com.workflow.contracts.outbox.port.OutboxPublishPort;
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

    private final OutboxPublishPort outboxPublisher;

    /**
     * 入队流程抄送通知{@code publisher}；后续由接收方或异步任务继续处理。
     *
     * @param record 记录，作为 {@code outboxPublisher.publish} 的输入影响后续处理
     * @param requestedChannels 请求{@code channels}，作为 {@code normalizeChannels} 的输入影响后续处理
     */
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

    /**
     * 规范化{@code channels}；输出作为后续校验或处理的输入。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 流程抄送通知{@code publisher}集合，供调用方遍历或展示
     */
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

    /**
     * 整理消息数据，供调用方遍历或继续处理。
     *
     * @param record 记录，作为 {@code payload.put} 的输入影响后续处理
     * @return 消息键值结果，供调用方继续处理
     */
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
