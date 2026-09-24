package com.workflow.entity.data.application;

import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.process.model.ProcessStartRequest;
import com.workflow.contracts.process.model.ProcessStartResult;
import com.workflow.contracts.process.port.ProcessRuntimePort;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.mapping.EntityRuntimeRecordMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.definition.application.EntityCodeGeneratorService;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityStatusMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 验证同一记录的保存、状态保护、启动载荷及过期重放边界。引擎代次另由流程运行时测试覆盖。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EntityProcessRestartMutationTest {
    @Mock EntityDataDynamicMapper mapper;
    @Mock EntityDefinitionMapper definitions;
    @Mock EntityStatusMapper statuses;
    @Mock DynamicTableService tables;
    @Mock EntityCodeGeneratorService codes;
    @Mock EntityRuntimeRecordMapper records;
    @Mock EntityRelationRuntimeService relations;
    @Mock EntityMultiValueRuntimeService multi;
    @Mock ProcessRuntimePort processes;
    @Mock SysUserService users;
    @Mock EntityPublishedSnapshotService snapshots;
    @Mock EntityRecordTeamService team;
    @Mock EntityDataMutationValidator validator;
    @Mock EntityDataMutationPayloadMapper payloads;
    @InjectMocks EntityDataMutationService service;
    Map<String, Object> stored;
    final Map<String, Object> request = Map.of("restartProcess", true, "previousProcessInstanceId", "old",
            "data", Map.of("amount", 200));

    @BeforeEach void setup() {
        UserContext.setCurrentUser("starter", "starter");
        stored = new HashMap<>(Map.of("id", "record", "code", "EXP-001", "status", "WITHDRAWN",
                "process_status", "COMPLETED", "process_instance_id", "old", "submitter_id", "starter",
                "amount", 100, "unchanged", "保留"));
        EntityDefinition entity = new EntityDefinition();
        entity.setId("entity"); entity.setEntityCode("expense");
        when(definitions.findByEntityCode("expense")).thenReturn(Optional.of(entity));
        when(tables.getTableName("expense")).thenReturn("biz_expense");
        when(relations.withoutRelationDataFromRequest(anyMap(), anyList())).thenAnswer(call -> call.getArgument(0));
        when(mapper.selectByIdForUpdate("biz_expense", "record")).thenAnswer(call -> new HashMap<>(stored));
        when(mapper.selectById("biz_expense", "record")).thenAnswer(call -> new HashMap<>(stored));
        when(mapper.update(eq("biz_expense"), anyMap())).thenAnswer(call -> {
            stored.putAll(call.<Map<String, Object>>getArgument(1)); return 1;
        });
        when(payloads.buildUpdateData(eq("expense"), eq("record"), anyMap(), anyMap()))
                .thenReturn(new HashMap<>(Map.of("id", "record", "amount", 200)));
        when(payloads.toRuntimeDto(anyMap(), eq("expense"))).thenAnswer(call -> {
            Map<String, Object> row = call.getArgument(0);
            EntityDataDTO dto = new EntityDataDTO(); dto.setId((String)row.get("id"));
            dto.setCode((String)row.get("code")); dto.setEntityCode("expense");
            dto.setStatus((String)row.get("status")); dto.setProcessStatus((String)row.get("process_status"));
            dto.setProcessInstanceId((String)row.get("process_instance_id")); dto.setData(new HashMap<>(row));
            return dto;
        });
        EntityStatus withdrawn = new EntityStatus(); withdrawn.setStatusCategory("WITHDRAWN");
        when(statuses.findByEntityAndCode("expense", "WITHDRAWN")).thenReturn(withdrawn);
        when(processes.canRestart("expense", "record", "old", "starter")).thenReturn(true);
        EntityPublishedSnapshot snapshot = mock(EntityPublishedSnapshot.class);
        when(snapshot.getProcessDefinitionId()).thenReturn("published-process");
        when(snapshots.getLatestByEntityCode("expense")).thenReturn(snapshot);
        when(processes.start(any())).thenReturn(new ProcessStartResult("new", null, "task", "审批", "reviewer", "RUNNING"));
    }

    @AfterEach void cleanup() { UserContext.clear(); }

    @Test void restartingPreservesIdentityAndUsesSavedCompleteDataThenRejectsOldSubmission() {
        EntityProcessRestartContext.execute("expense", "record", "old", () -> service.update("expense", "record", request));
        ArgumentCaptor<ProcessStartRequest> start = ArgumentCaptor.forClass(ProcessStartRequest.class);
        verify(processes).start(start.capture());
        assertEquals("record", start.getValue().entityRecordId());
        assertEquals("EXP-001", start.getValue().code());
        assertEquals("old", start.getValue().previousProcessInstanceId());
        assertEquals(200, start.getValue().data().get("amount"));
        assertEquals("保留", start.getValue().data().get("unchanged"));
        assertEquals("new", stored.get("process_instance_id"));
        assertEquals("RUNNING", stored.get("process_status"));
        assertEquals("WITHDRAWN", stored.get("status")); // 未配置开始连线，不能隐式覆盖实体状态。
        assertThrows(BusinessConflictException.class, () -> EntityProcessRestartContext.execute(
                "expense", "record", "old", () -> service.update("expense", "record", request)));
        verify(processes, times(1)).start(any());
        verifyNoInteractions(codes);
    }

    @Test void forgedRequestAndWrongEndFactsNeverWriteOrStart() {
        assertThrows(BusinessForbiddenException.class, () -> service.update("expense", "record", request));
        when(processes.canRestart("expense", "record", "old", "starter")).thenReturn(false);
        assertThrows(BusinessConflictException.class, () -> EntityProcessRestartContext.execute(
                "expense", "record", "old", () -> service.update("expense", "record", request)));
        verify(mapper, never()).update(anyString(), anyMap());
        verify(processes, never()).start(any());
    }
}
