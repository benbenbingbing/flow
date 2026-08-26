package com.workflow.entity.list.extension;

import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ListFieldConditionEvaluatorCandidateFilterTest {

    @Test
    void isNullKeepsOnlyUnlinkedRowsDuringInMemoryExtensionFiltering() {
        EntityListField field = new EntityListField();
        field.setFieldCode("projectId");
        field.setIsQuery(true);
        EntityDataDTO unlinked = record("row-1", null);
        EntityDataDTO linked = record("row-2", "project-1");

        List<EntityDataDTO> result = new ListFieldConditionEvaluator().filter(
                List.of(unlinked, linked),
                List.of(field),
                Map.of(
                        "projectId", true,
                        "projectId_op", "IS_NULL"));

        assertEquals(List.of("row-1"), result.stream()
                .map(EntityDataDTO::getId)
                .toList());
    }

    private EntityDataDTO record(String id, String projectId) {
        EntityDataDTO record = new EntityDataDTO();
        record.setId(id);
        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("projectId", projectId);
        record.setData(data);
        return record;
    }
}
