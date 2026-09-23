package com.workflow.biz.project.custom;

import com.workflow.core.logging.LogValue;
import com.workflow.outbox.api.OutboxEvent;
import com.workflow.outbox.api.OutboxEventHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Outbox 事件处理器示例。
 *
 * <p>主题为 {@value #TOPIC}。业务方可通过平台 {@code OutboxPublisher}
 * 发布该主题；处理器只记录稳定元数据，不解析或输出事件正文。</p>
 */
@Slf4j
@Component
public class ProjectCustomOutboxEventHandler
        implements OutboxEventHandler {

    public static final String TOPIC =
            "PROJECT_CUSTOM_OUTBOX";

    /**
     * 生成{@code topic}文本，供后续匹配或展示。
     *
     * @return 处理后的{@code topic}文本，供调用方比较或展示
     */
    @Override
    public String topic() {
        return TOPIC;
    }

    /**
     * 处理项目自定义待发送事件事件，并将结果传给后续步骤。
     *
     * @param event 事件，作为 {@code LogValue.safe} 的输入影响后续处理
     */
    @Override
    public void handle(OutboxEvent event) {
        log.info(
                "项目 Outbox 事件处理完成: id={}, topic={}, eventKey={}, aggregateType={}, aggregateId={}, retryCount={}, payloadPresent={}",
                LogValue.safe(event == null
                        ? null : event.id()),
                LogValue.safe(event == null
                        ? null : event.topic()),
                LogValue.safe(event == null
                        ? null : event.eventKey()),
                LogValue.safe(event == null
                        ? null : event.aggregateType()),
                LogValue.safe(event == null
                        ? null : event.aggregateId()),
                event == null
                        ? null : event.retryCount(),
                event != null
                        && event.payloadDocument() != null
                        && !event.payloadDocument()
                                .isBlank());
    }

    /**
     * 判断可重试条件是否成立，供调用方选择后续分支。
     *
     * @return 可重试条件成立时为 true，否则为 false
     */
    @Override
    public boolean retryable() {
        return false;
    }
}
