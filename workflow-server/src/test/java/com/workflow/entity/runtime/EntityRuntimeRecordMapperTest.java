package com.workflow.entity.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.EntityDataDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class EntityRuntimeRecordMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityRuntimeRecordMapper mapper = new EntityRuntimeRecordMapper(objectMapper);

    @Test
    void toDtoMapsSystemFieldsAndCustomFields() {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> row = Map.of(
                "id", "data-1",
                "data_no", "NO-1",
                "status", "PENDING",
                "current_task_assignee", "admin",
                "create_time", now,
                "amount_total", 12,
                "detail_json", "{\"name\":\"明细\"}"
        );

        EntityDataDTO dto = mapper.toDto(row, "expense");

        assertEquals("data-1", dto.getId());
        assertEquals("expense", dto.getEntityCode());
        assertEquals("NO-1", dto.getDataNo());
        assertEquals("PENDING", dto.getStatus());
        assertEquals("admin", dto.getCurrentTaskAssignee());
        assertEquals(now, dto.getCreatedAt());
        assertEquals(12, dto.getData().get("amountTotal"));
        assertInstanceOf(Map.class, dto.getData().get("detailJson"));
    }

    @Test
    void toStorageMapKeepsSystemFieldsAndNormalizesCustomFields() throws Exception {
        EntityDataDTO dto = new EntityDataDTO();
        dto.setId("data-1");
        dto.setEntityCode("expense");
        dto.setCurrentTaskAssignee("admin");
        dto.setData(Map.of(
                "amountTotal", 12,
                "emptyValue", "",
                "currentTaskAssignee", "ignored",
                "detailRows", Map.of("name", "明细")
        ));

        Map<String, Object> row = mapper.toStorageMap(dto);

        assertEquals("data-1", row.get("id"));
        assertEquals("admin", row.get("current_task_assignee"));
        assertEquals(12, row.get("amount_total"));
        assertNull(row.get("empty_value"));
        assertFalse(row.containsKey("entity_code"));
        Map<String, Object> detailRows = objectMapper.readValue(
                String.valueOf(row.get("detail_rows")),
                new TypeReference<>() {
                }
        );
        assertEquals("明细", detailRows.get("name"));
    }

    @Test
    void extractRequestCustomDataReturnsDynamicColumnsOnly() {
        Map<String, Object> request = Map.of("data", Map.of(
                "status", "APPROVED",
                "currentTaskId", "task-1",
                "amountTotal", 12
        ));

        Map<String, Object> customData = mapper.extractRequestCustomData(request);

        assertEquals(Map.of("amount_total", 12), customData);
    }
}
