package com.workflow.entity.data.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.application.mapping.EntityRuntimeRecordMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityRelationMapper;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.definition.application.EntityCodeGeneratorService;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class EntityRelationRuntimeDataKeyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityRelationRuntimeService service =
            new EntityRelationRuntimeService(
                    mock(EntityDataDynamicMapper.class),
                    mock(EntityDefinitionMapper.class),
                    mock(EntityFieldMapper.class),
                    mock(EntityRelationMapper.class),
                    mock(DynamicTableService.class),
                    objectMapper,
                    new EntityRuntimeRecordMapper(objectMapper),
                    mock(EntityCodeGeneratorService.class));

    @Test
    void extractsAndRemovesIndependentRelationByDataKey() {
        EntityRelation relation = relation("children", null);
        Map<String, Object> data = Map.of(
                "name", "订单",
                "children", List.of(Map.of("id", "child-1")));

        Map<String, Object> relationData =
                service.extractRelationData(data, List.of(relation));
        Map<String, Object> parentData =
                service.withoutRelationData(data, List.of(relation));

        assertTrue(relationData.containsKey("children"));
        assertFalse(parentData.containsKey("children"));
        assertEquals("订单", parentData.get("name"));
    }

    @Test
    void legacyRelationFallsBackToParentFieldCode() {
        EntityRelation relation = relation(null, "detailList");

        assertEquals(
                "detailList",
                service.effectiveDataKey(relation));
    }

    @Test
    void fieldlessLegacyRelationFinallyFallsBackToRelationCode() {
        EntityRelation relation = relation(null, null);
        relation.setRelationCode("detail_relation");

        assertEquals(
                "detail_relation",
                service.effectiveDataKey(relation));
    }

    @Test
    void rejectsNestedWritesForIndependentAssociation() {
        EntityRelation relation = relation("members", null);
        relation.setRelationName("成员关联");
        relation.setOwnershipType(EntityRelation.OwnershipType.ASSOCIATION);

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> service.saveRelationData(
                        "parent-1",
                        List.of(relation),
                        Map.of("members", List.of(Map.of("id", "child-1")))));

        assertEquals(
                "ENTITY_RELATION_ASSOCIATION_NESTED_WRITE_UNSUPPORTED",
                exception.getErrorCode());
    }

    @Test
    void rejectsMultipleSubmittedRowsForOneToOneRelation() {
        EntityRelation relation = relation("detail", null);
        relation.setRelationType(EntityRelation.RelationType.ONE_TO_ONE);

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> service.saveRelationData(
                        "parent-1",
                        List.of(relation),
                        Map.of(
                                "detail",
                                List.of(
                                        Map.of("id", "child-1"),
                                        Map.of("id", "child-2")))));

        assertEquals(
                "ENTITY_VERSION_RELATION_CARDINALITY_VIOLATION",
                exception.getErrorCode());
    }

    private EntityRelation relation(
            String dataKey,
            String parentFieldCode) {
        EntityRelation relation = new EntityRelation();
        relation.setDataKey(dataKey);
        relation.setParentFieldCode(parentFieldCode);
        return relation;
    }
}
