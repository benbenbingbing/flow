package com.workflow.admin.audit.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.audit.domain.AuditLogPayload;
import com.workflow.admin.audit.infrastructure.AuditDiffCalculator;
import com.workflow.admin.audit.infrastructure.AuditPayloadSanitizer;
import com.workflow.admin.audit.infrastructure.AuditRequestMetadataProvider;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditRiskLevel;
import com.workflow.contracts.audit.AuditSourcePointer;
import com.workflow.contracts.audit.OperationContext;
import com.workflow.contracts.audit.OperationContextHolder;
import com.workflow.contracts.audit.SystemAuditEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditLogPayloadFactoryOperationContextTest {

    @Test
    void inheritsOperationAndSourceButKeepsTraceSeparate() {
        ObjectMapper objectMapper =
                new ObjectMapper().findAndRegisterModules();
        AuditRequestMetadataProvider metadataProvider =
                mock(AuditRequestMetadataProvider.class);
        when(metadataProvider.current()).thenReturn(
                new AuditRequestMetadataProvider.AuditRequestMetadata(
                        "request-trace",
                        "user-1",
                        "张三",
                        null,
                        null,
                        "POST",
                        "/test"));
        AuditLogPayloadFactory factory = new AuditLogPayloadFactory(
                new AuditPayloadSanitizer(objectMapper, 4096),
                new AuditDiffCalculator(objectMapper),
                metadataProvider);
        OperationContext context = new OperationContext(
                "operation-1",
                "trace-1",
                null,
                new AuditSourcePointer(
                        "ENTITY_MUTATION",
                        "FORM",
                        "form-1",
                        "mutation-1"));

        AuditLogPayload payload;
        try (OperationContextHolder.Scope ignored =
                     OperationContextHolder.open(context)) {
            payload = factory.create(SystemAuditEvent.builder()
                    .module(AuditModule.ENTITY)
                    .action(AuditAction.UPDATE)
                    .operationName("更新实体")
                    .riskLevel(AuditRiskLevel.MEDIUM)
                    .beforeData(Map.of("token", "secret-value"))
                    .build());
        }

        assertEquals("operation-1", payload.operationId());
        assertEquals("trace-1", payload.traceId());
        assertEquals("FORM", payload.sourceType());
        assertEquals("form-1", payload.sourceId());
        assertEquals("mutation-1", payload.sourceEventId());
        assertEquals("{\"token\":\"******\"}", payload.beforeJson());
        assertNull(OperationContextHolder.current().orElse(null));
    }

    @Test
    void nestedScopeRestoresParentContext() {
        OperationContext parent =
                OperationContext.root("parent", "trace");
        OperationContext child = parent.child(
                "child",
                new AuditSourcePointer(
                        "PROCESS", "TASK", "task-1", null));

        try (OperationContextHolder.Scope ignored =
                     OperationContextHolder.open(parent)) {
            assertEquals("parent", OperationContextHolder.current()
                    .orElseThrow().operationId());
            try (OperationContextHolder.Scope childScope =
                         OperationContextHolder.open(child)) {
                assertEquals("child", OperationContextHolder.current()
                        .orElseThrow().operationId());
            }
            assertEquals("parent", OperationContextHolder.current()
                    .orElseThrow().operationId());
        }
        assertNull(OperationContextHolder.current().orElse(null));
    }
}
