package com.workflow.process.coordination.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.outbox.api.OutboxEvent;
import com.workflow.outbox.api.OutboxEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 恢复原操作人上下文并消费跨实体流程协同事件。 */
@Component
@RequiredArgsConstructor
public class RelatedProcessCoordinationOutboxHandler
        implements OutboxEventHandler {

    private final ObjectMapper objectMapper;
    private final RelatedProcessCoordinationExecutionService executionService;

    @Override
    public String topic() {
        return RelatedProcessCoordinationPublisher.TOPIC;
    }

    @Override
    public boolean retryable() {
        return true;
    }

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
