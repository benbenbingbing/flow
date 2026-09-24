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
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;

class EntityRelationRuntimeDataKeyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityRelationMapper relationMapper = mock(EntityRelationMapper.class);
    private final EntityRelationRuntimeService service =
            new EntityRelationRuntimeService(
                    mock(EntityDataDynamicMapper.class),
                    mock(EntityDefinitionMapper.class),
                    mock(EntityFieldMapper.class),
                    relationMapper,
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
    void legacyFormRelationCodeIsNormalizedButNeverWrittenAsParentColumn() {
        EntityRelation relation = relation("singleChild", null);
        relation.setRelationCode("single_child");
        Map<String, Object> child = Map.of("name", "子项");
        Map<String, Object> data = Map.of(
                "name", "主项", "single_child", child);

        Map<String, Object> relationData = service.extractRelationData(
                data, List.of(relation));
        Map<String, Object> parentData = service.withoutRelationData(
                data, List.of(relation));

        assertEquals(child, relationData.get("singleChild"));
        assertFalse(parentData.containsKey("single_child"));
        assertEquals("主项", parentData.get("name"));
    }

    @Test
    void disabledRelationInOldFormFailsBeforeParentInsert() {
        EntityRelation relation = relation("singleChild", null);
        relation.setRelationCode("single_child");
        relation.setRelationName("唯一子项组成关系");
        relation.setEnabled(false);
        Map<String, Object> data = Map.of(
                "name", "主项",
                "single_child", Map.of("name", "不应保存的子项"));

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> service.withoutRelationData(data, List.of(relation)));

        assertEquals("ENTITY_RELATION_DISABLED_FORM_STALE",
                exception.getErrorCode());
        assertTrue(exception.getMessage().contains("重新发布表单"));
        assertTrue(service.extractRelationData(data, List.of(relation))
                .isEmpty());
    }

    @Test
    void emptyDisabledRelationFromOldFormIsRemovedFromParentData() {
        EntityRelation relation = relation("singleChild", null);
        relation.setRelationCode("single_child");
        relation.setEnabled(false);
        Map<String, Object> data = Map.of(
                "name", "主项", "single_child", List.of());

        Map<String, Object> parentData = service.withoutRelationData(
                data, List.of(relation));

        assertEquals(Map.of("name", "主项"), parentData);
        assertTrue(service.extractRelationData(data, List.of(relation))
                .isEmpty());
    }

    @Test
    void releasedDisabledRelationMissingFromSnapshotRejectsStaleForm() {
        EntityDefinition parent = new EntityDefinition();
        parent.setId("parent-entity");
        EntityRelation disabled = relation("singleChild", null);
        disabled.setRelationCode("single_child");
        disabled.setRelationName("唯一子项组成关系");
        disabled.setEnabled(false);
        when(relationMapper.selectAllByParentEntityId("parent-entity"))
                .thenReturn(List.of(disabled));
        Map<String, Object> original = Map.of(
                "name", "主项", "single_child", Map.of("name", "旧表单子项"));
        Map<String, Object> parentData = new HashMap<>(original);

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> service.stripUnpublishedRelationKeys(
                        parent, original, parentData, List.of()));

        assertEquals("ENTITY_RELATION_DISABLED_FORM_STALE",
                exception.getErrorCode());
        assertTrue(exception.getMessage().contains("重新发布表单"));
    }

    @Test
    void releasedDisabledRelationEmptyValueIsNotStoredAsParentColumn() {
        EntityDefinition parent = new EntityDefinition();
        parent.setId("parent-entity");
        EntityRelation disabled = relation("singleChild", null);
        disabled.setRelationCode("single_child");
        disabled.setEnabled(false);
        when(relationMapper.selectAllByParentEntityId("parent-entity"))
                .thenReturn(List.of(disabled));
        Map<String, Object> original = Map.of(
                "data", Map.of("name", "主项", "single_child", List.of()));
        Map<String, Object> parentData = new HashMap<>();
        parentData.put("data", new HashMap<>(
                Map.of("name", "主项", "single_child", List.of())));

        service.stripUnpublishedRelationKeys(
                parent, original, parentData, List.of());

        assertEquals(Map.of("name", "主项"), parentData.get("data"));
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

    @Test
    void rejectsRequiredRelationWhenNewParentHasNoChildRows() {
        RelationSaveFixture fixture = relationSaveFixture();
        fixture.relation().setRelationName("订单明细");
        fixture.relation().setRequired(true);

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> fixture.service().saveRelationData(
                        "parent-new", List.of(fixture.relation()), Map.of()));

        assertEquals("ENTITY_RELATION_REQUIRED_MISSING", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("订单明细"));
        verify(fixture.dynamicMapper(), never()).insert(
                eq("wf_child"), anyMap());
    }

    @Test
    void rejectsReplacingRequiredChildrenWithEmptyList() {
        RelationSaveFixture fixture = relationSaveFixture();
        fixture.relation().setRequired(true);
        when(fixture.dynamicMapper().selectByCondition(
                eq("wf_child"), anyMap()))
                .thenReturn(List.of(Map.of(
                        "id", "child-existing",
                        "parentId", "parent-1")));

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> fixture.service().saveRelationData(
                        "parent-1",
                        List.of(fixture.relation()),
                        Map.of("children", List.of())));

        assertEquals("ENTITY_RELATION_REQUIRED_MISSING", exception.getErrorCode());
        verify(fixture.dynamicMapper(), never()).update(
                eq("wf_child"), anyMap());
    }

    @Test
    void omittedRequiredRelationKeepsExistingChildRows() {
        RelationSaveFixture fixture = relationSaveFixture();
        fixture.relation().setRequired(true);
        when(fixture.dynamicMapper().selectByCondition(
                eq("wf_child"), anyMap()))
                .thenReturn(List.of(Map.of(
                        "id", "child-existing",
                        "parentId", "parent-1")));

        fixture.service().saveRelationData(
                "parent-1", List.of(fixture.relation()), Map.of());

        verify(fixture.dynamicMapper(), never()).update(
                eq("wf_child"), anyMap());
    }

    @Test
    void explicitNullRemovesOptionalOneToOneChild() {
        RelationSaveFixture fixture = relationSaveFixture();
        fixture.relation().setRelationType(EntityRelation.RelationType.ONE_TO_ONE);
        when(fixture.dynamicMapper().selectByCondition(
                eq("wf_child"), anyMap()))
                .thenReturn(List.of(Map.of(
                        "id", "child-existing",
                        "parentId", "parent-1")));
        when(fixture.dynamicMapper().selectByIdForUpdate(
                "wf_child", "child-existing"))
                .thenReturn(Map.of(
                        "id", "child-existing",
                        "parentId", "parent-1"));
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("children", null);

        fixture.service().saveRelationData(
                "parent-1", List.of(fixture.relation()), submitted);

        verify(fixture.dynamicMapper()).deleteById(
                "wf_child", "child-existing");
    }

    @Test
    void explicitNullCannotRemoveRequiredOneToOneChild() {
        RelationSaveFixture fixture = relationSaveFixture();
        fixture.relation().setRelationType(EntityRelation.RelationType.ONE_TO_ONE);
        fixture.relation().setRequired(true);
        when(fixture.dynamicMapper().selectByCondition(
                eq("wf_child"), anyMap()))
                .thenReturn(List.of(Map.of(
                        "id", "child-existing",
                        "parentId", "parent-1")));
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("children", null);

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> fixture.service().saveRelationData(
                        "parent-1", List.of(fixture.relation()), submitted));

        assertEquals("ENTITY_RELATION_REQUIRED_MISSING", exception.getErrorCode());
        verify(fixture.dynamicMapper(), never()).deleteById(
                "wf_child", "child-existing");
    }

    @Test
    void rejectsExistingChildIdOwnedByAnotherParent() {
        RelationSaveFixture fixture = relationSaveFixture();
        when(fixture.dynamicMapper().selectByCondition(
                eq("wf_child"), anyMap()))
                .thenReturn(List.of(Map.of(
                        "id", "child-owned",
                        "parentId", "parent-1")));
        when(fixture.dynamicMapper().selectByIdForUpdate(
                "wf_child", "child-owned"))
                .thenReturn(Map.of(
                        "id", "child-owned",
                        "parentId", "parent-1"));

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> fixture.service().saveRelationData(
                        "parent-1",
                        List.of(fixture.relation()),
                        Map.of("children", List.of(Map.of(
                                "id", "child-from-other-parent")))));

        assertEquals(
                "ENTITY_RELATION_CHILD_OWNERSHIP_CONFLICT",
                exception.getErrorCode());
        verify(fixture.dynamicMapper(), never()).update(
                eq("wf_child"), anyMap());
    }

    @Test
    void rejectsConcurrentOwnershipChangeAfterRelationQuery() {
        RelationSaveFixture fixture = relationSaveFixture();
        when(fixture.dynamicMapper().selectByCondition(
                eq("wf_child"), anyMap()))
                .thenReturn(List.of(Map.of(
                        "id", "child-1",
                        "parentId", "parent-1")));
        when(fixture.dynamicMapper().selectByIdForUpdate(
                "wf_child", "child-1"))
                .thenReturn(Map.of(
                        "id", "child-1",
                        "parentId", "parent-2"));

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> fixture.service().saveRelationData(
                        "parent-1",
                        List.of(fixture.relation()),
                        Map.of("children", List.of(Map.of(
                                "id", "child-1")))));

        assertEquals(
                "ENTITY_RELATION_CHILD_OWNERSHIP_CHANGED",
                exception.getErrorCode());
        verify(fixture.dynamicMapper(), never()).update(
                eq("wf_child"), anyMap());
    }

    private RelationSaveFixture relationSaveFixture() {
        EntityDataDynamicMapper dynamicMapper =
                mock(EntityDataDynamicMapper.class);
        EntityDefinitionMapper definitionMapper =
                mock(EntityDefinitionMapper.class);
        EntityFieldMapper fieldMapper = mock(EntityFieldMapper.class);
        EntityRelationMapper relationMapper = mock(EntityRelationMapper.class);
        DynamicTableService tableService = mock(DynamicTableService.class);
        EntityDefinition child = new EntityDefinition();
        child.setId("child-entity");
        child.setEntityCode("child");
        when(definitionMapper.selectById("child-entity"))
                .thenReturn(child);
        when(definitionMapper.findByEntityCode("child"))
                .thenReturn(Optional.of(child));
        when(fieldMapper.findByEntityId("child-entity"))
                .thenReturn(List.of());
        when(relationMapper.selectByParentEntityId("child-entity"))
                .thenReturn(List.of());
        when(tableService.tableExists("child")).thenReturn(true);
        when(tableService.getTableName("child"))
                .thenReturn("wf_child");
        EntityRelation relation = relation("children", null);
        relation.setParentEntityCode("parent");
        relation.setChildEntityId("child-entity");
        relation.setChildEntityCode("child");
        relation.setChildRefFieldCode("parentId");
        relation.setRelationType(EntityRelation.RelationType.ONE_TO_MANY);
        relation.setOwnershipType(EntityRelation.OwnershipType.COMPOSITION);
        EntityRelationRuntimeService runtimeService =
                new EntityRelationRuntimeService(
                        dynamicMapper,
                        definitionMapper,
                        fieldMapper,
                        relationMapper,
                        tableService,
                        objectMapper,
                        new EntityRuntimeRecordMapper(objectMapper),
                        mock(EntityCodeGeneratorService.class));
        return new RelationSaveFixture(
                runtimeService,
                dynamicMapper,
                relation);
    }

    private record RelationSaveFixture(
            EntityRelationRuntimeService service,
            EntityDataDynamicMapper dynamicMapper,
            EntityRelation relation) {
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
