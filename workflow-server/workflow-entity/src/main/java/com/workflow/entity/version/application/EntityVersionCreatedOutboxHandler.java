package com.workflow.entity.version.application;

import com.workflow.contracts.outbox.model.OutboxEvent;
import com.workflow.contracts.outbox.spi.OutboxEventHandlerProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 数据版本事件默认消费者。
 *
 * <p>当前仅确认事件已进入统一 Outbox；后续通知、索引或外部同步可独立替换处理逻辑。</p>
 */
@Slf4j
@Component
public class EntityVersionCreatedOutboxHandler
        implements OutboxEventHandlerProvider {

    /**
     * 生成{@code topic}文本，供后续匹配或展示。
     *
     * @return 处理后的{@code topic}文本，供调用方比较或展示
     */
    @Override
    public String topic() {
        return EntityRecordVersionService
                .VERSION_CREATED_TOPIC;
    }

    /**
     * 处理实体版本已创建待发送事件，并将结果传给后续步骤。
     *
     * @param event 事件，供本方法处理实体版本已创建待发送事件时使用
     */
    @Override
    public void handle(OutboxEvent event) {
        log.info(
                "实体数据版本事件已提交: eventKey={}, aggregateId={}",
                event.eventKey(),
                event.aggregateId());
    }
}
