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

    /**
     * 初始化流程抄送通知待发送事件处理器，保存构造参数供后续方法使用。
     *
     * @param recordMapper 记录映射器依赖，保存到当前对象供后续业务方法调用
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     * @param channels {@code channels}，保存在对象中供后续校验、查询或展示
     */
    public ProcessCcNotificationOutboxHandler(
            ProcessCcRecordMapper recordMapper,
            ObjectMapper objectMapper,
            List<CcNotificationChannel> channels) {
        this.recordMapper = recordMapper;
        this.objectMapper = objectMapper;
        this.channels = indexChannels(channels);
    }

    /**
     * 生成{@code topic}文本，供后续匹配或展示。
     *
     * @return 处理后的{@code topic}文本，供调用方比较或展示
     */
    @Override
    public String topic() {
        return ProcessCcNotificationPublisher.TOPIC;
    }

    /**
     * 处理流程抄送通知待发送事件，并将结果传给后续步骤。
     *
     * @param event 事件，作为 {@code objectMapper.readValue} 的输入影响后续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     * @throws Exception 下游操作失败时向调用方传递
     */
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

    /**
     * 整理索引{@code channels}数据，供调用方遍历或继续处理。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 索引{@code channels}键值结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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
