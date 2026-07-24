package com.workflow.service.cc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.ProcessCcOutbox;
import com.workflow.entity.ProcessCcRecord;
import com.workflow.mapper.ProcessCcOutboxMapper;
import com.workflow.mapper.ProcessCcRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 知会发送箱服务。
 *
 * <p>将知会记录按通知渠道写入发送箱（Outbox），并通过定时任务异步分发到对应渠道。
 * 采用幂等入队与重试机制，保证知会消息可靠送达或进入死信状态。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessCcOutboxService {
    private final ProcessCcOutboxMapper outboxMapper;
    private final ProcessCcRecordMapper recordMapper;
    private final ObjectMapper objectMapper;
    /** 已注册的通知渠道列表 */
    private final List<CcNotificationChannel> channels;

    /**
     * 将一条知会记录按请求的渠道入队（默认 IN_APP）。
     *
     * @param record           知会记录
     * @param requestedChannels 期望的渠道列表，为空时使用 IN_APP
     */
    @Transactional(rollbackFor = Exception.class)
    public void enqueue(ProcessCcRecord record, List<String> requestedChannels) {
        List<String> normalized = requestedChannels == null || requestedChannels.isEmpty()
                ? List.of("IN_APP")
                : requestedChannels.stream().map(value -> value.toUpperCase(Locale.ROOT)).distinct().toList();
        for (String channel : normalized) {
            ProcessCcOutbox outbox = new ProcessCcOutbox();
            outbox.setCcRecordId(record.getId());
            outbox.setChannel(channel);
            outbox.setPayload(payload(record));
            outbox.setStatus("PENDING");
            outbox.setRetryCount(0);
            outbox.setCreateTime(LocalDateTime.now());
            try {
                outboxMapper.insert(outbox);
            } catch (DuplicateKeyException ignored) {
                // 幂等入队。
            }
        }
    }

    /**
     * 定时分发已就绪的发送箱记录。按固定延迟执行。
     */
    @Scheduled(fixedDelayString = "${workflow.cc.outbox-delay-ms:5000}")
    public void dispatchReady() {
        outboxMapper.findReady().forEach(this::dispatchOne);
    }

    /**
     * 分发单条发送箱记录：抢占后路由到对应渠道发送，失败则计入重试。
     *
     * @param outbox 发送箱记录
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatchOne(ProcessCcOutbox outbox) {
        if (outboxMapper.claim(outbox.getId()) == 0) {
            return;
        }
        outbox.setStatus("PROCESSING");
        ProcessCcRecord record = recordMapper.selectById(outbox.getCcRecordId());
        if (record == null) {
            markFailed(outbox, "知会记录不存在");
            return;
        }
        CcNotificationChannel sender = channels.stream()
                .filter(item -> item.channel().equalsIgnoreCase(outbox.getChannel()))
                .findFirst()
                .orElse(null);
        if (sender == null) {
            markFailed(outbox, "未注册通知渠道: " + outbox.getChannel());
            return;
        }
        try {
            sender.send(record, outbox);
            outbox.setStatus("SENT");
            outbox.setSentTime(LocalDateTime.now());
            outbox.setErrorMessage(null);
            outboxMapper.updateById(outbox);
        } catch (Exception exception) {
            markFailed(outbox, exception.getMessage());
        }
    }

    /** 标记发送失败：累计重试次数，达到上限则进入死信，并计算下次重试时间 */
    private void markFailed(ProcessCcOutbox outbox, String message) {
        int retries = outbox.getRetryCount() == null ? 1 : outbox.getRetryCount() + 1;
        outbox.setRetryCount(retries);
        outbox.setStatus(retries >= 5 ? "DEAD" : "FAILED");
        outbox.setNextRetryTime(LocalDateTime.now().plusMinutes(Math.min(30, 1L << Math.min(retries, 5))));
        outbox.setErrorMessage(message);
        outboxMapper.updateById(outbox);
        log.warn("知会发送失败: outboxId={}, channel={}, retry={}, message={}",
                outbox.getId(), outbox.getChannel(), retries, message);
    }

    /** 组装知会消息载荷JSON */
    private String payload(ProcessCcRecord record) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("processInstanceId", record.getProcessInstanceId());
        payload.put("processName", record.getProcessName());
        payload.put("nodeName", record.getNodeName());
        payload.put("recipient", record.getCcUserId());
        payload.put("comment", record.getComment());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("知会消息无法序列化", exception);
        }
    }
}
