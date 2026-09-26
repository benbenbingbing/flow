package com.workflow.entity.data.infrastructure.adapter;

import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntityRecordQueryAdapterTest {
    private final EntityDataDynamicService records = mock(EntityDataDynamicService.class);
    private final EntityRecordQueryAdapter adapter = new EntityRecordQueryAdapter(records);

    @Test
    void returnsBusinessValuesWithoutSharingTheHostFieldMaps() {
        EntityDataDTO host = new EntityDataDTO();
        host.setId("project-1");
        host.setEntityCode("project");
        host.setCreateBy("creator-1");
        host.setData(new LinkedHashMap<>(Map.of("amount", 100)));
        host.setExtData(new LinkedHashMap<>(Map.of("total", 200)));
        host.setFormReleaseResolutionToken("host-only-token");
        when(records.findById("project", "project-1")).thenReturn(host);

        var result = adapter.findById("project", "project-1");

        assertEquals("creator-1", result.getCreateBy());
        assertEquals("project-1", result.getId());
        assertEquals("project", result.getEntityCode());
        assertEquals(host.getData(), result.getData());
        result.getData().put("amount", 999);
        result.getExtData().clear();
        assertEquals(100, host.getData().get("amount"));
        assertEquals(200, host.getExtData().get("total"));
    }

    @Test
    void preservesHostQueryConditionsAndMissingRecordSemantics() {
        Map<String, Object> condition = Map.of("project_id", "project-1");
        when(records.findByCondition("member", condition)).thenReturn(List.of());

        assertTrue(adapter.findByCondition("member", condition).isEmpty());
        assertNull(adapter.findById("project", "missing"));
        verify(records).findByCondition(eq("member"), same(condition));
    }

    @Test
    void doesNotTurnAnAuthorizationFailureIntoAMissingRecord() {
        var denied = new IllegalStateException("无权读取目标实体");
        when(records.findById("restricted", "record-1")).thenThrow(denied);

        assertSame(denied, assertThrows(IllegalStateException.class,
                () -> adapter.findById("restricted", "record-1")));
    }
}
