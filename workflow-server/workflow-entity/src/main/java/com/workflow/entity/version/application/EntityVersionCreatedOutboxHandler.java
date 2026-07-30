package com.workflow.entity.version.application;

import com.workflow.outbox.api.OutboxEvent;
import com.workflow.outbox.api.OutboxEventHandler;
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
        implements OutboxEventHandler {

    @Override
    public String topic() {
        return EntityRecordVersionService
                .VERSION_CREATED_TOPIC;
    }

    @Override
    public void handle(OutboxEvent event) {
        log.info(
                "实体数据版本事件已提交: eventKey={}, aggregateId={}",
                event.eventKey(),
                event.aggregateId());
    }
}
