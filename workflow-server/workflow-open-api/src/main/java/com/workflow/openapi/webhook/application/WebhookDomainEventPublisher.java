package com.workflow.openapi.webhook.application;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.workflow.contracts.process.open.OpenProcessEvent;
import com.workflow.contracts.process.open.OpenProcessEventPort;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationProcessBindingMapper;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationProcessBindingRecord;
import com.workflow.outbox.api.OutboxPublishRequest;
import com.workflow.outbox.api.OutboxPublisher;
import java.time.Instant;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes externally visible process facts to the generic transactional outbox.
 */
@Service
public class WebhookDomainEventPublisher
        implements OpenProcessEventPort {

    public static final String TOPIC = "INTEGRATION_DOMAIN_EVENT";
    private static final Set<String> EVENT_TYPES = Set.of(
            "com.flow.process.started.v1",
            "com.flow.task.created.v1",
            "com.flow.task.completed.v1",
            "com.flow.process.completed.v1",
            "com.flow.process.terminated.v1",
            "com.flow.process.failed.v1");

    private final IntegrationProcessBindingMapper bindingMapper;
    private final OutboxPublisher outboxPublisher;

    public WebhookDomainEventPublisher(
            IntegrationProcessBindingMapper bindingMapper,
            OutboxPublisher outboxPublisher) {
        this.bindingMapper = bindingMapper;
        this.outboxPublisher = outboxPublisher;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(OpenProcessEvent event) {
        validate(event);
        IntegrationProcessBindingRecord binding =
                bindingMapper.findOwnerByProcessInstance(
                        event.processInstanceId());
        if (binding == null) {
            return;
        }
        String eventId = IdWorker.getIdStr();
        IntegrationDomainEventPayload payload =
                new IntegrationDomainEventPayload(
                        eventId,
                        event.eventKey(),
                        binding.getApplicationId(),
                        event.eventType(),
                        event.processInstanceId(),
                        binding.getProcessDefinitionKey(),
                        binding.getExternalSystem(),
                        binding.getBusinessType(),
                        binding.getBusinessId(),
                        event.taskId(),
                        event.taskDefinitionKey(),
                        event.traceId() == null
                                || event.traceId().isBlank()
                                ? eventId
                                : event.traceId(),
                        event.occurredAt());
        outboxPublisher.publish(new OutboxPublishRequest(
                TOPIC,
                event.eventKey(),
                "PROCESS_INSTANCE",
                event.processInstanceId(),
                payload,
                20));
    }

    private void validate(OpenProcessEvent event) {
        if (event == null
                || event.eventKey() == null
                || event.eventKey().isBlank()
                || !EVENT_TYPES.contains(event.eventType())
                || event.processInstanceId() == null
                || event.processInstanceId().isBlank()
                || event.occurredAt() == null) {
            throw new IllegalArgumentException(
                    "开放流程事件字段不完整");
        }
        boolean taskEvent = event.eventType().startsWith(
                "com.flow.task.");
        if (taskEvent
                && (event.taskId() == null
                || event.taskId().isBlank()
                || event.taskDefinitionKey() == null
                || event.taskDefinitionKey().isBlank())) {
            throw new IllegalArgumentException(
                    "开放任务事件缺少任务标识");
        }
    }
}
