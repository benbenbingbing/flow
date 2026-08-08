package com.workflow.openapi.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.process.open.OpenBusinessReference;
import com.workflow.contracts.process.open.OpenProcessDefinition;
import com.workflow.contracts.process.open.OpenProcessView;
import com.workflow.openapi.api.error.OpenApiException;
import com.workflow.openapi.api.request.OpenStartProcessRequest;
import com.workflow.openapi.api.response.OpenBusinessReferenceView;
import com.workflow.openapi.api.response.OpenProcessDefinitionView;
import com.workflow.openapi.api.response.OpenProcessInstanceView;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationWorkflowScenarioMapper;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationProcessBindingRecord;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationProcessGrantRecord;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationWorkflowScenarioRecord;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds public process responses from runtime and immutable binding data. */
final class OpenProcessResponseAssembler {

    private static final TypeReference<List<String>> STRING_LIST =
            new TypeReference<>() {
            };

    private final IntegrationWorkflowScenarioMapper scenarioMapper;
    private final OpenProcessScenarioSupport scenarioSupport;
    private final ObjectMapper objectMapper;

    OpenProcessResponseAssembler(
            IntegrationWorkflowScenarioMapper scenarioMapper,
            OpenProcessScenarioSupport scenarioSupport,
            ObjectMapper objectMapper) {
        this.scenarioMapper = scenarioMapper;
        this.scenarioSupport = scenarioSupport;
        this.objectMapper = objectMapper;
    }

    Set<String> readMessageKeys(IntegrationProcessGrantRecord contract) {
        try {
            return new LinkedHashSet<>(objectMapper.readValue(
                    contract.allowedMessageKeys(), STRING_LIST));
        } catch (JsonProcessingException exception) {
            throw unavailable(exception);
        }
    }

    OpenProcessDefinitionView toDefinitionView(
            OpenProcessDefinition definition,
            IntegrationProcessGrantRecord contract) {
        if (contract == null) {
            throw new IllegalStateException("流程目录返回了未授权流程");
        }
        try {
            JsonNode schema = objectMapper.readTree(contract.inputSchemaJson());
            return new OpenProcessDefinitionView(
                    definition.processKey(), definition.name(), definition.version(),
                    definition.description(), schema, definition.publishedAt());
        } catch (JsonProcessingException exception) {
            throw unavailable(exception);
        }
    }

    OpenBusinessReference toBusinessReference(OpenStartProcessRequest request) {
        return new OpenBusinessReference(
                request.businessReference().system(),
                request.businessReference().type(),
                request.businessReference().id(),
                request.businessReference().version());
    }

    OpenProcessInstanceView toInstanceView(
            OpenProcessView process,
            IntegrationProcessBindingRecord binding) {
        IntegrationWorkflowScenarioRecord scenario = binding.getScenarioId() == null
                || scenarioMapper == null
                ? null
                : scenarioMapper.findPublishedByApplicationAndKey(
                        binding.getApplicationId(), binding.getScenarioKey());
        return toInstanceView(
                process,
                binding.getExternalSystem(), binding.getBusinessType(),
                binding.getBusinessId(), binding.getBusinessVersion(),
                binding.getScenarioKey(), binding.getScenarioRevision(),
                scenarioSupport.projectResult(process,
                        binding.getOutcomeMappingSnapshotJson() == null
                                && scenario != null
                                ? scenario.getOutcomeMappingJson()
                                : binding.getOutcomeMappingSnapshotJson()));
    }

    OpenProcessInstanceView toInstanceView(
            OpenProcessView process,
            String externalSystem,
            String businessType,
            String businessId) {
        return toInstanceView(process, externalSystem, businessType, businessId,
                null, null, null, Map.of());
    }

    OpenProcessInstanceView toInstanceView(
            OpenProcessView process,
            String externalSystem,
            String businessType,
            String businessId,
            String businessVersion,
            String scenarioKey,
            Long scenarioRevision,
            Map<String, Object> result) {
        return new OpenProcessInstanceView(
                process.processInstanceId(), process.processKey(), process.status(),
                new OpenBusinessReferenceView(
                        externalSystem, businessType, businessId, businessVersion),
                process.createdAt(), process.completedAt(), scenarioKey,
                scenarioRevision, result);
    }

    private OpenApiException unavailable(Throwable cause) {
        OpenApiException exception = new OpenApiException(
                503, "INTEGRATION_TEMPORARILY_UNAVAILABLE",
                "开放接口暂时不可用");
        if (cause != null) {
            exception.initCause(cause);
        }
        return exception;
    }
}
