package com.workflow.process.coordination.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.outbox.model.OutboxEvent;
import com.workflow.contracts.outbox.spi.OutboxEventHandlerProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 恢复原操作人上下文并消费跨实体流程协同事件。 */
@Component
@RequiredArgsConstructor
public class RelatedProcessCoordinationOutboxHandler
        implements OutboxEventHandlerProvider {

    private final ObjectMapper objectMapper;
    private final RelatedProcessCoordinationExecutionService executionService;

    /**
     * 生成{@code topic}文本，供后续匹配或展示。
     *
     * @return 处理后的{@code topic}文本，供调用方比较或展示
     */
    @Override
    public String topic() {
        return RelatedProcessCoordinationPublisher.TOPIC;
    }

    /**
     * 判断可重试条件是否成立，供调用方选择后续分支。
     *
     * @return 可重试条件成立时为 true，否则为 false
     */
    @Override
    public boolean retryable() {
        return true;
    }

    /**
     * 处理关联流程协同待发送事件，并将结果传给后续步骤。
     *
     * @param event 事件，作为 {@code objectMapper.readValue} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     * @throws Exception 下游操作失败时向调用方传递
     */
    @Override
    public void handle(OutboxEvent event) throws Exception {
        RelatedProcessCoordinationEvent payload = objectMapper.readValue(
                event.payloadDocument(),
                RelatedProcessCoordinationEvent.class);
        String userId = payload.originalPlan().source().operatorId();
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException(
                    "流程协同事件未固定原操作人");
        }
        String previousUserId = UserContext.getUserId();
        String previousUsername = UserContext.getUsername();
        try {
            UserContext.clear();
            UserContext.setCurrentUser(
                    userId,
                    StringUtils.hasText(
                            payload.originalPlan().source().operatorName())
                            ? payload.originalPlan().source().operatorName()
                            : userId);
            executionService.execute(
                    event.id(), event.eventKey(), payload);
        } finally {
            UserContext.clear();
            if (StringUtils.hasText(previousUserId)) {
                UserContext.setCurrentUser(
                        previousUserId,
                        StringUtils.hasText(previousUsername)
                                ? previousUsername : previousUserId);
            }
        }
    }
}
