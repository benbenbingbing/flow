package com.workflow.biz.project.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.model.EntityRecordData;
import com.workflow.contracts.entity.mutation.model.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.model.EntityMutationResult;
import com.workflow.contracts.entity.mutation.port.EntityMutationPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProjectEntityMutationExecutorTest {
    @Test
    void preservesAuditWireNameAndIgnoresHostPresentationFieldsInTheReceipt() {
        EntityMutationPort port = mock(EntityMutationPort.class);
        Map<String, Object> receipt = Map.of(
                "id", "project-1", "entityCode", "project", "create_by", "creator-1",
                "data", Map.of("budget", 100), "formReleaseResolutionToken", "host-only-token",
                "actionCapabilities", Map.of("edit", Map.of("enabled", true)));
        when(port.execute(any())).thenAnswer(invocation -> {
            EntityMutationCommand command = invocation.getArgument(0);
            return new EntityMutationResult(command.operationId(), command.entityCode(), "project-1",
                    command.operationType(), receipt, 1, null, true, false);
        });
        var executor = new ProjectEntityMutationExecutor(port, new ObjectMapper());
        var draft = new EntityRecordData();
        draft.setEntityCode("project");
        draft.setCreateBy("creator-1");
        draft.setData(Map.of("budget", 100));

        var result = executor.inSession(null, "CREATE_PROJECT", "创建项目", () -> executor.save(draft));

        var captured = ArgumentCaptor.forClass(EntityMutationCommand.class);
        verify(port).execute(captured.capture());
        Map<String, Object> payload = captured.getValue().payload();
        assertEquals("creator-1", payload.get("create_by"));
        assertFalse(payload.containsKey("createBy"));
        assertFalse(payload.containsKey("formReleaseResolutionToken"));
        assertEquals("creator-1", result.getCreateBy());
        assertEquals("project-1", result.getId());
        assertEquals(100, result.getData().get("budget"));
        assertTrue(receipt.containsKey("create_by"), "投影不应改写端口的原始回执");
    }
}
