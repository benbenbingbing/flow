package com.workflow.admin.audit.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.result.Result;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditResult;
import com.workflow.contracts.audit.SystemAudit;
import com.workflow.contracts.audit.SystemAuditEvent;
import com.workflow.contracts.audit.SystemAuditPort;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SystemAuditAspectTest {

    @Test
    void recordsSuccessfulOperation() {
        RecordingAuditPort port = new RecordingAuditPort();
        SampleService service = proxy(port);

        service.save("record-1");

        assertEquals(1, port.events.size());
        assertEquals(AuditResult.SUCCESS, port.events.get(0).result());
        assertEquals("record-1", port.events.get(0).targetId());
    }

    @Test
    void recordsReturnedFailureResponse() {
        RecordingAuditPort port = new RecordingAuditPort();
        SampleService service = proxy(port);

        service.returnFailure("record-2");

        assertEquals(AuditResult.FAILURE, port.events.get(0).result());
        assertEquals("业务失败", port.events.get(0).errorMessage());
    }

    @Test
    void recordsThrownFailureWithoutMaskingOriginalException() {
        RecordingAuditPort port = new RecordingAuditPort();
        SampleService service = proxy(port);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.fail("record-3"));

        assertEquals("boom", exception.getMessage());
        assertEquals(AuditResult.FAILURE, port.events.get(0).result());
    }

    @Test
    void recordsOnlyOuterOperationForNestedAuditedCalls() {
        RecordingAuditPort port = new RecordingAuditPort();
        NestedService inner = proxy(new NestedService(null), port);
        NestedService outer = proxy(new NestedService(inner), port);

        outer.execute();

        assertEquals(1, port.events.size());
        assertEquals("外层审计操作", port.events.get(0).operationName());
    }

    @Test
    void usesBusinessOperationIdToLinkAuditAndMutationReceipts() {
        RecordingAuditPort port = new RecordingAuditPort();
        SampleService service = proxy(port);

        service.executeRelatedAction(new ActionRequest("operation-42"));

        SystemAuditEvent event = port.events.get(0);
        assertEquals("operation-42", event.operationId());
        assertEquals("project-7", event.targetId());
        assertEquals("UI_VIEW_COMPOSITION_ACTION",
                event.sourcePointer().sourceType());
        assertEquals("project-7", event.sourcePointer().sourceId());
    }

    private SampleService proxy(RecordingAuditPort port) {
        return proxy(new SampleService(), port);
    }

    private <T> T proxy(T target, RecordingAuditPort port) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new SystemAuditAspect(
                port,
                new ObjectMapper().findAndRegisterModules()));
        return factory.getProxy();
    }

    static class SampleService {

        @SystemAudit(
                module = AuditModule.ENTITY,
                action = AuditAction.UPDATE,
                operation = "保存测试数据",
                targetType = "TEST",
                targetIdArg = 0)
        public Result<Void> save(String id) {
            return Result.success();
        }

        @SystemAudit(
                module = AuditModule.ENTITY,
                action = AuditAction.UPDATE,
                operation = "返回失败",
                targetType = "TEST",
                targetIdArg = 0)
        public Result<Void> returnFailure(String id) {
            return Result.error(500, "业务失败");
        }

        @SystemAudit(
                module = AuditModule.ENTITY,
                action = AuditAction.UPDATE,
                operation = "抛出失败",
                targetType = "TEST",
                targetIdArg = 0)
        public void fail(String id) {
            throw new IllegalStateException("boom");
        }

        @SystemAudit(
                module = AuditModule.ENTITY,
                action = AuditAction.OTHER,
                operation = "执行关联内容动作",
                targetType = "UI_VIEW_COMPOSITION_ACTION")
        public ActionResponse executeRelatedAction(ActionRequest request) {
            return new ActionResponse(request.getOperationId(), "project-7");
        }
    }

    static final class ActionRequest {
        private final String operationId;

        ActionRequest(String operationId) {
            this.operationId = operationId;
        }

        public String getOperationId() {
            return operationId;
        }
    }

    static final class ActionResponse {
        private final String operationId;
        private final String sourceRecordId;

        ActionResponse(String operationId, String sourceRecordId) {
            this.operationId = operationId;
            this.sourceRecordId = sourceRecordId;
        }

        public String getOperationId() {
            return operationId;
        }

        public String getSourceRecordId() {
            return sourceRecordId;
        }
    }

    static class NestedService {
        private final NestedService next;

        NestedService(NestedService next) {
            this.next = next;
        }

        @SystemAudit(
                module = AuditModule.PROCESS,
                action = AuditAction.UPDATE,
                operation = "外层审计操作")
        public void execute() {
            if (next != null) {
                next.executeInner();
            }
        }

        @SystemAudit(
                module = AuditModule.PROCESS,
                action = AuditAction.UPDATE,
                operation = "内层审计操作")
        public void executeInner() {
        }
    }

    static class RecordingAuditPort implements SystemAuditPort {
        private final List<SystemAuditEvent> events = new ArrayList<>();

        @Override
        public void record(SystemAuditEvent event) {
            events.add(event);
        }
    }
}
