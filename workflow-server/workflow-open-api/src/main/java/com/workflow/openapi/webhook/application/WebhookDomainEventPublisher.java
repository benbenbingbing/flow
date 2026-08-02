package com.workflow.openapi.webhook.application;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.process.open.OpenProcessEvent;
import com.workflow.contracts.process.open.OpenProcessEventPort;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationProcessBindingMapper;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationWorkflowScenarioMapper;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationProcessBindingRecord;
import com.workflow.outbox.api.OutboxPublishRequest;
import com.workflow.outbox.api.OutboxPublisher;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final IntegrationWorkflowScenarioMapper scenarioMapper;
    private final ObjectMapper objectMapper;
    private final OutboxPublisher outboxPublisher;

    public WebhookDomainEventPublisher(
            IntegrationProcessBindingMapper bindingMapper,
            OutboxPublisher outboxPublisher) {
        this(bindingMapper, null, null, outboxPublisher);
    }

    @Autowired
    public WebhookDomainEventPublisher(
            IntegrationProcessBindingMapper bindingMapper,
            IntegrationWorkflowScenarioMapper scenarioMapper,
            ObjectMapper objectMapper,
            OutboxPublisher outboxPublisher) {
        this.bindingMapper = bindingMapper;
        this.scenarioMapper = scenarioMapper;
        this.objectMapper = objectMapper;
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
        if (!scenarioAllows(binding, event.eventType())) {
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
                        event.occurredAt(),
                        binding.getScenarioKey(),
                        binding.getScenarioRevision(),
                        binding.getBusinessVersion(),
                        binding.getIdentityNamespace(),
                        projectAttributes(
                                binding,
                                event.attributes()));
        outboxPublisher.publish(new OutboxPublishRequest(
                TOPIC,
                event.eventKey(),
                "PROCESS_INSTANCE",
                event.processInstanceId(),
                payload,
                20));
    }

    private Map<String, Object> projectAttributes(
            IntegrationProcessBindingRecord binding,
            Map<String, Object> attributes) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (attributes != null) {
            result.putAll(attributes);
        }
        Object rawSources = result.remove(
                OpenProcessEvent.INTERNAL_OUTCOME_VARIABLES);
        if (!(rawSources instanceof Map<?, ?> sources)
                || binding.getOutcomeMappingSnapshotJson() == null
                || objectMapper == null) {
            return Map.copyOf(result);
        }
        try {
            JsonNode mapping = objectMapper.readTree(
                    binding.getOutcomeMappingSnapshotJson());
            mapping.fields().forEachRemaining(entry -> {
                String source = entry.getValue().asText();
                if (source.startsWith("variables.")) {
                    source = source.substring("variables.".length());
                }
                Object value = sources.get(source);
                if (value instanceof String || value instanceof Number
                        || value instanceof Boolean) {
                    result.put(entry.getKey(), value);
                }
            });
            return Map.copyOf(result);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "结果映射快照损坏", exception);
        }
    }

    private boolean scenarioAllows(
            IntegrationProcessBindingRecord binding,
            String eventType) {
        if (binding.getScenarioId() == null || objectMapper == null) {
            return true;
        }
        if (binding.getEventTypesSnapshotJson() != null) {
            try {
                return objectMapper.readValue(
                        binding.getEventTypesSnapshotJson(),
                        new TypeReference<Set<String>>() {
                        }).contains(eventType);
            } catch (Exception exception) {
                throw new IllegalStateException("运行事件白名单快照损坏", exception);
            }
        }
        if (scenarioMapper == null) {
            return false;
        }
        var scenario = scenarioMapper.findById(binding.getScenarioId());
        if (scenario == null || !"ACTIVE".equals(scenario.getStatus())) {
            return false;
        }
        try {
            return objectMapper.readValue(
                    scenario.getEventTypesJson(),
                    new TypeReference<Set<String>>() {
                    }).contains(eventType);
        } catch (Exception exception) {
            throw new IllegalStateException("场景事件白名单损坏", exception);
        }
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
