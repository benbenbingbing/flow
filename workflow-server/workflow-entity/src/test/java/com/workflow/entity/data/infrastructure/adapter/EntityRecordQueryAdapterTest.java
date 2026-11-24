package com.workflow.entity.data.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataActionService;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.core.error.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntityRecordQueryAdapterTest {
    private final EntityDataDynamicService records = mock(EntityDataDynamicService.class);
    private final EntityDataActionService actions = mock(EntityDataActionService.class);
    private final EntityRecordQueryAdapter adapter = new EntityRecordQueryAdapter(records, new ObjectMapper(),
            new StaticListableBeanFactory(Map.of("actions", actions)).getBeanProvider(EntityDataActionService.class), mock(EntitySubmissionAdapter.class));

    @Test
    void returnsBusinessValuesWithoutSharingTheHostFieldMaps() {
        EntityDataDTO host = new EntityDataDTO();
        host.setId("project-1");
        host.setEntityCode("project");
        host.setCreateBy("creator-1");
        host.setData(new LinkedHashMap<>(Map.of("amount", 100)));
        host.setExtData(new LinkedHashMap<>(Map.of("total", 200)));
        host.setRuntimeContext(Map.of("taskId", "host-only-context"));
        when(records.findByIdForPort("project", "project-1")).thenReturn(host);

        var result = adapter.findById("project", "project-1");

        assertEquals("creator-1", result.getCreateBy());
        assertEquals("project-1", result.getId());
        assertEquals("project", result.getEntityCode());
        assertEquals(host.getData(), result.getData());
        assertTrue(result.getRevision().matches("[a-f0-9]{64}"));
        result.getData().put("amount", 999);
        result.getExtData().clear();
        assertEquals(100, host.getData().get("amount"));
        assertEquals(200, host.getExtData().get("total"));
    }

    @Test
    void preservesHostQueryConditionsAndMissingRecordSemantics() {
        Map<String, Object> condition = Map.of("project_id", "project-1");
        when(records.findByConditionForPort("member", condition)).thenReturn(List.of());

        assertTrue(adapter.findByCondition("member", condition).isEmpty());
        assertNull(adapter.findById("project", "missing"));
        verify(records).findByConditionForPort(eq("member"), same(condition));
    }

    @Test
    void propagatesReadFailuresWithoutReturningMissingRecord() {
        var denied = new IllegalStateException("读取失败");
        when(records.findByIdForPort("restricted", "record-1")).thenThrow(denied);

        assertSame(denied, assertThrows(IllegalStateException.class,
                () -> adapter.findById("restricted", "record-1")));
    }

    @Test
    void checksThePublishedListBeforeReadingAnyRecords() {
        doThrow(new ForbiddenException("disabled list")).when(actions)
                .requirePublishedListForPort("project", "private-list");

        assertThrows(ForbiddenException.class, () -> adapter.findById("project", "private-list", "row"));

        verifyNoInteractions(records);
    }

    @Test
    void batchesIdsAndPreservesOrderWithoutReturningMissingRows() {
        var first = new EntityDataDTO();
        first.setId("first");
        var last = new EntityDataDTO();
        last.setId("last");
        var condition = Map.<String, Object>of("id_op", "IN", "id", List.of("first", "missing", "last"));
        when(records.findByConditionForPort("project", condition)).thenReturn(List.of(last, first));

        var result = adapter.findByIds("project", List.of("first", "missing", "first", "last"));

        assertEquals(List.of("first", "last"), result.stream().map(row -> row.getId()).toList());
        verify(records).findByConditionForPort("project", condition);
        verifyNoMoreInteractions(records);
    }

    @Test
    void looksUpAnExactProcessInstanceAndRejectsEmptyIds() {
        var record = new EntityDataDTO();
        record.setId("record-1");
        var condition = Map.<String, Object>of("process_instance_id", "run_%", "process_instance_id_op", "EQ");
        when(records.findByConditionForPort("project", condition)).thenReturn(List.of(record));

        assertThrows(IllegalArgumentException.class, () -> adapter.findByProcessInstance("project", " "));
        assertEquals("record-1", adapter.findByProcessInstance("project", "run_%").getId());
        verify(records).findByConditionForPort("project", condition);
        verifyNoMoreInteractions(records);
    }
}
