package com.workflow.entity.data.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.application.mapping.EntityRuntimeRecordMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityRelationMapper;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.definition.application.EntityCodeGeneratorService;
import com.workflow.entity.definition.application.EntityPublishedRelationService;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityPublishHistoryMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityPublishHistory;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityRelationRuntimeSelfCycleTest {

    @Test
    void writeValidationLocksDefinitionEvenWithoutSelfRelation() {
        ProductionGuardFixture fixture = productionGuardFixture(List.of());

        fixture.service.validateSelfRelationWrite(
                fixture.definition,
                "record-1",
                Map.of(),
                true);

        verify(fixture.definitionMapper)
                .findByEntityCodeForShare("node");
        verify(fixture.publishHistoryMapper, never())
                .findLatestByEntityIdForUpdate(anyString());
        verify(fixture.definitionMapper, never())
                .findByEntityCodeForUpdate(anyString());
    }

    @Test
    void productionGuardLocksPublishedVersionAfterDefinitionForSelfRelation() {
        Fixture domain = fixture(
                EntityRelation.OwnershipType.ASSOCIATION);
        ProductionGuardFixture fixture = productionGuardFixture(
                List.of(domain.relation));
        EntityPublishHistory history = new EntityPublishHistory();
        when(fixture.publishHistoryMapper
                .findLatestByEntityIdForUpdate("node-entity"))
                .thenReturn(history);

        fixture.service.lockSelfRelationGuard("node");

        var order = inOrder(
                fixture.definitionMapper,
                fixture.publishedRelationService,
                fixture.publishHistoryMapper);
        order.verify(fixture.definitionMapper)
                .findByEntityCodeForShare("node");
        order.verify(fixture.publishedRelationService)
                .list(fixture.definition);
        order.verify(fixture.publishHistoryMapper)
                .findLatestByEntityIdForUpdate("node-entity");
    }

    @Test
    void rejectsCycleCreatedByTwoDifferentSequentialWrites() {
        Fixture fixture = fixture(
                EntityRelation.OwnershipType.ASSOCIATION);
        Map<String, Map<String, Object>> rows = new HashMap<>();
        rows.put("A", row("A", null));
        rows.put("B", row("B", null));
        fixture.answerLockedRows(rows);

        assertDoesNotThrow(() ->
                fixture.service.validateSelfRelationWrite(
                        fixture.definition,
                        "A",
                        Map.of("parent_id", "B"),
                        false));

        // 模拟第一笔事务已经提交 A -> B，第二笔再尝试 B -> A。
        rows.put("A", row("A", "B"));
        BusinessConflictException failure = assertThrows(
                BusinessConflictException.class,
                () -> fixture.service.validateSelfRelationWrite(
                        fixture.definition,
                        "B",
                        Map.of("parent_id", "A"),
                        false));

        assertEquals(
                "ENTITY_SELF_RELATION_CYCLE",
                failure.getErrorCode());
    }

    @Test
    void rejectsExistingAncestorCycleInsteadOfTreatingItAsNoCycle() {
        Fixture fixture = fixture(
                EntityRelation.OwnershipType.COMPOSITION);
        Map<String, Map<String, Object>> rows = new HashMap<>();
        rows.put("B", row("B", "C"));
        rows.put("C", row("C", "B"));
        fixture.answerLockedRows(rows);

        BusinessConflictException failure = assertThrows(
                BusinessConflictException.class,
                () -> fixture.service.validateSelfRelationWrite(
                        fixture.definition,
                        "A",
                        Map.of("parentId", "B"),
                        true));

        assertEquals(
                "ENTITY_SELF_RELATION_CYCLE",
                failure.getErrorCode());
    }

    @Test
    void rejectsSelfParentForBothOwnershipTypes() {
        for (EntityRelation.OwnershipType ownershipType
                : EntityRelation.OwnershipType.values()) {
            Fixture fixture = fixture(ownershipType);

            BusinessConflictException failure = assertThrows(
                    BusinessConflictException.class,
                    () -> fixture.service.validateSelfRelationWrite(
                            fixture.definition,
                            "A",
                            Map.of("parent_id", "A"),
                            true));

            assertEquals(
                    "ENTITY_SELF_RELATION_SELF_PARENT",
                    failure.getErrorCode());
        }
    }

    @Test
    void rejectsMissingParentAsBusinessConflict() {
        Fixture fixture = fixture(
                EntityRelation.OwnershipType.ASSOCIATION);
        fixture.answerLockedRows(Map.of());

        BusinessConflictException failure = assertThrows(
                BusinessConflictException.class,
                () -> fixture.service.validateSelfRelationWrite(
                        fixture.definition,
                        "A",
                        Map.of("parent_id", "missing"),
                        true));

        assertEquals(
                "ENTITY_SELF_RELATION_PARENT_NOT_FOUND",
                failure.getErrorCode());
    }

    @Test
    void rejectsAncestorChainBeyondSafetyLimit() {
        Fixture fixture = fixture(
                EntityRelation.OwnershipType.COMPOSITION);
        Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        for (int index = 0; index <= 256; index++) {
            String id = "N" + index;
            String parentId = index == 256
                    ? null : "N" + (index + 1);
            rows.put(id, row(id, parentId));
        }
        fixture.answerLockedRows(rows);

        BusinessConflictException failure = assertThrows(
                BusinessConflictException.class,
                () -> fixture.service.validateSelfRelationWrite(
                        fixture.definition,
                        "A",
                        Map.of("parent_id", "N0"),
                        true));

        assertEquals(
                "ENTITY_SELF_RELATION_DEPTH_EXCEEDED",
                failure.getErrorCode());
    }

    @Test
    void nestedCompositionWriteRejectsCycleBeforeUpdatingChild() {
        Fixture fixture = fixture(
                EntityRelation.OwnershipType.COMPOSITION);
        Map<String, Map<String, Object>> rows = new HashMap<>();
        Map<String, Object> child = row("child", "root");
        child.put("parentId", "root");
        rows.put("child", child);
        rows.put("root", row("root", "child"));
        fixture.answerLockedRows(rows);
        when(fixture.dynamicMapper.selectByCondition(
                eq("wf_node"), any()))
                .thenReturn(List.of(child));
        when(fixture.definitionMapper.selectById("node-entity"))
                .thenReturn(fixture.definition);
        when(fixture.fieldMapper.findByEntityId("node-entity"))
                .thenReturn(List.of(fixture.referenceField));
        when(fixture.tableService.tableExists("node"))
                .thenReturn(true);

        BusinessConflictException failure = assertThrows(
                BusinessConflictException.class,
                () -> fixture.service.saveRelationData(
                        "root",
                        List.of(fixture.relation),
                        Map.of(
                                "children",
                                List.of(Map.of("id", "child")))));

        assertEquals(
                "ENTITY_SELF_RELATION_CYCLE",
                failure.getErrorCode());
        verify(fixture.dynamicMapper, never())
                .update(eq("wf_node"), any());
    }

    @Test
    void nestedCompositionLocksChildDefinitionBeforeReadingRowsEvenWithoutSelfRelation() {
        EntityDataDynamicMapper dynamicMapper =
                mock(EntityDataDynamicMapper.class);
        EntityDefinitionMapper definitionMapper =
                mock(EntityDefinitionMapper.class);
        EntityPublishHistoryMapper publishHistoryMapper =
                mock(EntityPublishHistoryMapper.class);
        EntityFieldMapper fieldMapper =
                mock(EntityFieldMapper.class);
        EntityRelationMapper relationMapper =
                mock(EntityRelationMapper.class);
        DynamicTableService tableService =
                mock(DynamicTableService.class);
        EntityPublishedRelationService publishedRelationService =
                mock(EntityPublishedRelationService.class);
        EntityDefinition child = definition("child-id", "child");
        EntityRelation relation = relation(
                "parent-id", "parent", "children",
                "child-id", "child", "parentId");
        when(definitionMapper.selectById("child-id"))
                .thenReturn(child);
        when(definitionMapper.findByEntityCodeForShare("child"))
                .thenReturn(Optional.of(child));
        when(fieldMapper.findByEntityId("child-id"))
                .thenReturn(List.of());
        when(publishedRelationService.list(child))
                .thenReturn(List.of());
        when(tableService.tableExists("child"))
                .thenReturn(true);
        when(tableService.getTableName("child"))
                .thenReturn("wf_child");
        when(dynamicMapper.selectByCondition(
                eq("wf_child"), any()))
                .thenReturn(List.of());
        EntityRelationRuntimeService service = productionService(
                dynamicMapper,
                definitionMapper,
                publishHistoryMapper,
                fieldMapper,
                relationMapper,
                tableService,
                publishedRelationService);

        service.saveRelationData(
                "parent-1",
                List.of(relation),
                Map.of("children", List.of()));

        var order = inOrder(
                definitionMapper,
                publishedRelationService,
                dynamicMapper);
        // 写前唯一 gate 需要先基于普通快照准备，因此关系行读取发生在
        // 定义守卫/关系冻结之前；真正的子业务行锁与写入仍在 gate 之后。
        order.verify(dynamicMapper)
                .selectByCondition(eq("wf_child"), any());
        order.verify(definitionMapper)
                .findByEntityCodeForShare("child");
        // 第一次用于判断是否需要历史互斥锁，第二次才是本层递归冻结的关系。
        order.verify(publishedRelationService, times(2))
                .list(child);
        verify(publishHistoryMapper, never())
                .findLatestByEntityIdForUpdate(anyString());
    }

    @Test
    void cascadeDeleteLocksDefinitionAndHistoryBeforeRowsAndUsesGuardedRelations() {
        EntityDataDynamicMapper dynamicMapper =
                mock(EntityDataDynamicMapper.class);
        EntityDefinitionMapper definitionMapper =
                mock(EntityDefinitionMapper.class);
        EntityPublishHistoryMapper publishHistoryMapper =
                mock(EntityPublishHistoryMapper.class);
        EntityFieldMapper fieldMapper =
                mock(EntityFieldMapper.class);
        EntityRelationMapper relationMapper =
                mock(EntityRelationMapper.class);
        DynamicTableService tableService =
                mock(DynamicTableService.class);
        EntityPublishedRelationService publishedRelationService =
                mock(EntityPublishedRelationService.class);
        EntityDefinition node = definition("node-id", "node");
        EntityRelation children = relation(
                "node-id", "node", "children",
                "node-id", "node", "parentId");
        children.setCascadeDelete(true);
        EntityPublishHistory history = new EntityPublishHistory();
        when(definitionMapper.findByEntityCodeForShare("node"))
                .thenReturn(Optional.of(node));
        when(definitionMapper.selectById("node-id"))
                .thenReturn(node);
        when(fieldMapper.findByEntityId("node-id"))
                .thenReturn(List.of());
        when(publishedRelationService.list(node))
                .thenReturn(List.of(children));
        when(publishHistoryMapper
                .findLatestByEntityIdForUpdate("node-id"))
                .thenReturn(history);
        when(tableService.tableExists("node"))
                .thenReturn(true);
        when(tableService.getTableName("node"))
                .thenReturn("wf_node");
        when(dynamicMapper.selectByCondition(
                eq("wf_node"), any()))
                .thenReturn(List.of(Map.of(
                        "id", "child-1",
                        "parentId", "root-1")));
        EntityRelationRuntimeService service = productionService(
                dynamicMapper,
                definitionMapper,
                publishHistoryMapper,
                fieldMapper,
                relationMapper,
                tableService,
                publishedRelationService);

        service.cascadeDeleteRelations(
                node,
                "root-1",
                false);

        var order = inOrder(
                definitionMapper,
                publishedRelationService,
                publishHistoryMapper,
                dynamicMapper);
        order.verify(definitionMapper)
                .findByEntityCodeForShare("node");
        order.verify(publishedRelationService)
                .list(node);
        order.verify(publishHistoryMapper)
                .findLatestByEntityIdForUpdate("node-id");
        // 根关系在守卫后冻结，之后才允许触碰子业务行。
        order.verify(publishedRelationService)
                .list(node);
        order.verify(definitionMapper)
                .findByEntityCodeForShare("node");
        order.verify(publishedRelationService)
                .list(node);
        order.verify(publishHistoryMapper)
                .findLatestByEntityIdForUpdate("node-id");
        order.verify(publishedRelationService)
                .list(node);
        order.verify(dynamicMapper)
                .selectByCondition(eq("wf_node"), any());
        order.verify(dynamicMapper)
                .deleteById("wf_node", "child-1");
    }

    private EntityRelationRuntimeService productionService(
            EntityDataDynamicMapper dynamicMapper,
            EntityDefinitionMapper definitionMapper,
            EntityPublishHistoryMapper publishHistoryMapper,
            EntityFieldMapper fieldMapper,
            EntityRelationMapper relationMapper,
            DynamicTableService tableService,
            EntityPublishedRelationService publishedRelationService) {
        ObjectMapper objectMapper = new ObjectMapper();
        return new EntityRelationRuntimeService(
                dynamicMapper,
                definitionMapper,
                publishHistoryMapper,
                fieldMapper,
                relationMapper,
                tableService,
                objectMapper,
                new EntityRuntimeRecordMapper(objectMapper),
                mock(EntityCodeGeneratorService.class),
                publishedRelationService);
    }

    private EntityDefinition definition(
            String id,
            String entityCode) {
        EntityDefinition definition = new EntityDefinition();
        definition.setId(id);
        definition.setEntityCode(entityCode);
        definition.setStorageMode(
                EntityDefinition.StorageMode.DYNAMIC);
        definition.setStatus(EntityDefinition.Status.PUBLISHED);
        return definition;
    }

    private EntityRelation relation(
            String parentEntityId,
            String parentEntityCode,
            String dataKey,
            String childEntityId,
            String childEntityCode,
            String childRefFieldCode) {
        EntityRelation relation = new EntityRelation();
        relation.setParentEntityId(parentEntityId);
        relation.setParentEntityCode(parentEntityCode);
        relation.setChildEntityId(childEntityId);
        relation.setChildEntityCode(childEntityCode);
        relation.setRelationCode(dataKey + "_relation");
        relation.setRelationName(dataKey);
        relation.setDataKey(dataKey);
        relation.setChildRefFieldCode(childRefFieldCode);
        relation.setRelationType(
                EntityRelation.RelationType.ONE_TO_MANY);
        relation.setOwnershipType(
                EntityRelation.OwnershipType.COMPOSITION);
        relation.setEnabled(true);
        return relation;
    }

    private Fixture fixture(
            EntityRelation.OwnershipType ownershipType) {
        EntityDataDynamicMapper dynamicMapper =
                mock(EntityDataDynamicMapper.class);
        EntityDefinitionMapper definitionMapper =
                mock(EntityDefinitionMapper.class);
        EntityFieldMapper fieldMapper =
                mock(EntityFieldMapper.class);
        EntityRelationMapper relationMapper =
                mock(EntityRelationMapper.class);
        DynamicTableService tableService =
                mock(DynamicTableService.class);
        EntityCodeGeneratorService codeGenerator =
                mock(EntityCodeGeneratorService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        EntityDefinition definition = new EntityDefinition();
        definition.setId("node-entity");
        definition.setEntityCode("node");
        definition.setEntityName("节点");
        definition.setStorageMode(
                EntityDefinition.StorageMode.DYNAMIC);
        definition.setStatus(EntityDefinition.Status.PUBLISHED);

        EntityField referenceField = new EntityField();
        referenceField.setId("parent-field");
        referenceField.setEntityId("node-entity");
        referenceField.setFieldCode("parentId");
        referenceField.setFieldName("父节点");
        referenceField.setDbColumnName("parent_id");
        referenceField.setFieldType(
                EntityField.FieldType.REFERENCE);
        referenceField.setRefEntityId("node-entity");

        EntityRelation relation = new EntityRelation();
        relation.setId("self-relation");
        relation.setParentEntityId("node-entity");
        relation.setParentEntityCode("node");
        relation.setChildEntityId("node-entity");
        relation.setChildEntityCode("node");
        relation.setRelationCode("children_relation");
        relation.setRelationName("子节点");
        relation.setDataKey("children");
        relation.setChildRefFieldCode("parentId");
        relation.setRelationType(
                EntityRelation.RelationType.ONE_TO_MANY);
        relation.setOwnershipType(ownershipType);
        relation.setEnabled(true);

        when(definitionMapper.findByEntityCode("node"))
                .thenReturn(Optional.of(definition));
        when(definitionMapper.findByEntityCodeForUpdate("node"))
                .thenReturn(Optional.of(definition));
        when(fieldMapper.findByEntityIdAndFieldCode(
                "node-entity", "parentId"))
                .thenReturn(referenceField);
        when(relationMapper.selectByParentEntityId("node-entity"))
                .thenReturn(List.of(relation));
        when(tableService.getTableName("node"))
                .thenReturn("wf_node");

        EntityRelationRuntimeService service =
                new EntityRelationRuntimeService(
                        dynamicMapper,
                        definitionMapper,
                        fieldMapper,
                        relationMapper,
                        tableService,
                        objectMapper,
                        new EntityRuntimeRecordMapper(objectMapper),
                        codeGenerator);
        return new Fixture(
                service,
                dynamicMapper,
                definitionMapper,
                fieldMapper,
                tableService,
                definition,
                referenceField,
                relation);
    }

    private ProductionGuardFixture productionGuardFixture(
            List<EntityRelation> relations) {
        EntityDataDynamicMapper dynamicMapper =
                mock(EntityDataDynamicMapper.class);
        EntityDefinitionMapper definitionMapper =
                mock(EntityDefinitionMapper.class);
        EntityPublishHistoryMapper publishHistoryMapper =
                mock(EntityPublishHistoryMapper.class);
        EntityFieldMapper fieldMapper =
                mock(EntityFieldMapper.class);
        EntityRelationMapper relationMapper =
                mock(EntityRelationMapper.class);
        DynamicTableService tableService =
                mock(DynamicTableService.class);
        EntityCodeGeneratorService codeGenerator =
                mock(EntityCodeGeneratorService.class);
        EntityPublishedRelationService publishedRelationService =
                mock(EntityPublishedRelationService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        EntityDefinition definition = new EntityDefinition();
        definition.setId("node-entity");
        definition.setEntityCode("node");

        when(definitionMapper.findByEntityCodeForShare("node"))
                .thenReturn(Optional.of(definition));
        when(publishedRelationService.list(definition))
                .thenReturn(relations);

        EntityRelationRuntimeService service =
                new EntityRelationRuntimeService(
                        dynamicMapper,
                        definitionMapper,
                        publishHistoryMapper,
                        fieldMapper,
                        relationMapper,
                        tableService,
                        objectMapper,
                        new EntityRuntimeRecordMapper(objectMapper),
                        codeGenerator,
                        publishedRelationService);
        return new ProductionGuardFixture(
                service,
                definitionMapper,
                publishHistoryMapper,
                publishedRelationService,
                definition);
    }

    private Map<String, Object> row(
            String id,
            String parentId) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("parent_id", parentId);
        return row;
    }

    private record Fixture(
            EntityRelationRuntimeService service,
            EntityDataDynamicMapper dynamicMapper,
            EntityDefinitionMapper definitionMapper,
            EntityFieldMapper fieldMapper,
            DynamicTableService tableService,
            EntityDefinition definition,
            EntityField referenceField,
            EntityRelation relation) {

        private void answerLockedRows(
                Map<String, Map<String, Object>> rows) {
            when(dynamicMapper.selectByIdForUpdate(
                    eq("wf_node"), anyString()))
                    .thenAnswer(invocation -> rows.get(
                            invocation.getArgument(1, String.class)));
        }
    }

    private record ProductionGuardFixture(
            EntityRelationRuntimeService service,
            EntityDefinitionMapper definitionMapper,
            EntityPublishHistoryMapper publishHistoryMapper,
            EntityPublishedRelationService publishedRelationService,
            EntityDefinition definition) {
    }
}
