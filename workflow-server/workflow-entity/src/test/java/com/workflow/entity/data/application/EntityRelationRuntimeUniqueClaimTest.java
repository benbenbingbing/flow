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
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.form.uniqueness.application.EntityFormUniqueClaimService;
import com.workflow.entity.form.uniqueness.application.EntityFormUniqueClaimService.PreparedUniqueClaims;
import com.workflow.entity.form.application.PublishedFormUniqueRuleService;
import com.workflow.entity.form.uniqueness.application.FormUniqueMutationContext;
import com.workflow.entity.form.uniqueness.application.TrustedSubFormUniqueReference;
import com.workflow.entity.form.uniqueness.infrastructure.persistence.EntityFormUniqueClaimRepository;
import com.workflow.entity.form.uniqueness.infrastructure.persistence.EntityFormUniqueValueGateRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityRelationRuntimeUniqueClaimTest {

    @Test
    void newChildReloadsFinalRowAndReconcilesTrustedForm() {
        Fixture fixture = new Fixture();
        when(fixture.dynamicMapper.selectById(
                eq("wf_child"), anyString()))
                .thenAnswer(invocation -> Map.of(
                        "id", invocation.getArgument(1),
                        "item_name", "\u660e\u7ec6A",
                        "parent_id", "parent-1",
                        "code", "C001",
                        "deleted", 0));
        Map<String, Object> row = fixture.trustedRow(
                Map.of("itemName", "\u660e\u7ec6A"));

        fixture.saveRelationData(
                "parent-1",
                List.of(fixture.relation),
                Map.of("details", List.of(row)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> inserted =
                ArgumentCaptor.forClass(Map.class);
        verify(fixture.dynamicMapper).insert(
                eq("wf_child"),
                inserted.capture());
        assertTrue(TrustedSubFormUniqueReference
                .remove(inserted.getValue())
                .isEmpty());
        String childId = String.valueOf(
                inserted.getValue().get("id"));
        InOrder writeOrder = inOrder(
                fixture.claimService,
                fixture.dynamicMapper);
        writeOrder.verify(fixture.claimService)
                .verifyRelationPrepared(
                        org.mockito.ArgumentMatchers.any(),
                        eq(fixture.rootPrepared));
        writeOrder.verify(fixture.claimService)
                .verifyChildPrepared(
                        eq("child"),
                        eq(childId),
                        anyMap(),
                        eq(List.of(fixture.reference)),
                        notNull());
        writeOrder.verify(fixture.dynamicMapper).insert(
                eq("wf_child"), anyMap());
        verify(fixture.claimService).reconcileChildRecord(
                eq("child"),
                eq(childId),
                org.mockito.ArgumentMatchers.argThat(record ->
                        "\u660e\u7ec6A".equals(record.get("itemName"))
                                && "C001".equals(record.get("code"))),
                eq(List.of(fixture.reference)),
                notNull());
    }

    @Test
    void existingChildUsesTrustedFormAndNeverWritesMarker() {
        Fixture fixture = new Fixture();
        fixture.existingChild("child-1", "\u65e7明细");
        when(fixture.dynamicMapper.selectById(
                "wf_child", "child-1"))
                .thenReturn(Map.of(
                        "id", "child-1",
                        "item_name", "\u65b0明细",
                        "parent_id", "parent-1",
                        "deleted", 0));
        Map<String, Object> submitted = new LinkedHashMap<>();
        submitted.put("id", "child-1");
        submitted.put("itemName", "\u65b0明细");
        Map<String, Object> row = fixture.trustedRow(submitted);

        fixture.saveRelationData(
                "parent-1",
                List.of(fixture.relation),
                Map.of("details", List.of(row)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> updated =
                ArgumentCaptor.forClass(Map.class);
        verify(fixture.dynamicMapper).update(
                eq("wf_child"),
                updated.capture());
        assertTrue(TrustedSubFormUniqueReference
                .remove(updated.getValue())
                .isEmpty());
        verify(fixture.claimService).reconcileChildRecord(
                eq("child"),
                eq("child-1"),
                org.mockito.ArgumentMatchers.argThat(record ->
                        "\u65b0明细".equals(record.get("itemName"))),
                eq(List.of(fixture.reference)),
                notNull());
        InOrder order = inOrder(
                fixture.claimService,
                fixture.dynamicMapper);
        order.verify(fixture.claimService)
                .verifyRelationPrepared(
                        org.mockito.ArgumentMatchers.any(),
                        eq(fixture.rootPrepared));
        order.verify(fixture.claimService)
                .verifyChildPrepared(
                        eq("child"),
                        eq("child-1"),
                        anyMap(),
                        eq(List.of(fixture.reference)),
                        notNull());
        order.verify(fixture.dynamicMapper)
                .selectByIdForUpdate("wf_child", "child-1");
        order.verify(fixture.dynamicMapper).update(
                eq("wf_child"), anyMap());
    }

    @Test
    void childWrittenWithoutTrustedFormOnlyReleasesOldClaim() {
        Fixture fixture = new Fixture();

        fixture.saveRelationData(
                "parent-1",
                List.of(fixture.relation),
                Map.of("details", List.of(
                        Map.of("itemName", "\u76f4接写入"))));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> inserted =
                ArgumentCaptor.forClass(Map.class);
        verify(fixture.dynamicMapper).insert(
                eq("wf_child"),
                inserted.capture());
        verify(fixture.claimService).releaseRecord(
                "child",
                String.valueOf(inserted.getValue().get("id")));
        verify(fixture.claimService, never()).prepareAll(
                org.mockito.ArgumentMatchers.anyList());
        verify(fixture.claimService, never())
                .reconcileChildRecord(
                        eq("child"),
                        anyString(),
                        anyMap(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
        verify(fixture.dynamicMapper, never()).selectById(
                eq("wf_child"), anyString());
    }

    @Test
    void missingChildIsDeletedAndItsClaimReleased() {
        Fixture fixture = new Fixture();
        fixture.existingChild("child-1", "\u5f85删除");

        fixture.saveRelationData(
                "parent-1",
                List.of(fixture.relation),
                Map.of("details", List.of()));

        verify(fixture.dynamicMapper).deleteById(
                "wf_child", "child-1");
        verify(fixture.claimService).releaseRecord(
                "child", "child-1");
    }

    @Test
    void uniqueConflictPropagatesFromTransactionalChildWrite() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.dynamicMapper.selectById(
                eq("wf_child"), anyString()))
                .thenAnswer(invocation -> Map.of(
                        "id", invocation.getArgument(1),
                        "item_name", "\u91cd复明细",
                        "deleted", 0));
        BusinessConflictException conflict =
                new BusinessConflictException(
                        "FORM_FIELD_UNIQUE_CONFLICT",
                        "\u660e细名称已存在");
        org.mockito.Mockito.doThrow(conflict)
                .when(fixture.claimService)
                .reconcileChildRecord(
                        eq("child"),
                        anyString(),
                        anyMap(),
                        eq(List.of(fixture.reference)),
                        notNull());

        BusinessConflictException thrown = assertThrows(
                BusinessConflictException.class,
                () -> fixture.saveRelationData(
                        "parent-1",
                        List.of(fixture.relation),
                        Map.of("details", List.of(
                                fixture.trustedRow(Map.of(
                                        "itemName",
                                        "\u91cd复明细"))))));

        assertEquals(conflict, thrown);
        verify(fixture.dynamicMapper).insert(
                eq("wf_child"), anyMap());
        Transactional transactional =
                EntityRelationRuntimeService.class
                        .getMethod(
                                "saveRelationData",
                                String.class,
                                List.class,
                                Map.class)
                        .getAnnotation(Transactional.class);
        assertEquals(
                Propagation.MANDATORY,
                transactional.propagation());
        assertTrue(List.of(transactional.rollbackFor())
                .contains(Exception.class));
    }

    @Test
    void sameChildProcessedByTwoFormsReconcilesAllReferencesOnce() {
        Fixture fixture = new Fixture();
        FormUniqueMutationContext.Reference second =
                new FormUniqueMutationContext.Reference(
                        "child-form-2",
                        "child-release-4",
                        4,
                        "child-release-4");
        when(fixture.dynamicMapper.selectById(
                eq("wf_child"), anyString()))
                .thenAnswer(invocation -> Map.of(
                        "id", invocation.getArgument(1),
                        "item_name", "多表单明细",
                        "deleted", 0));
        Map<String, Object> row = new LinkedHashMap<>(
                Map.of("itemName", "多表单明细"));
        TrustedSubFormUniqueReference.attach(
                row, "child", fixture.reference);
        TrustedSubFormUniqueReference.attach(
                row, "child", second);
        fixture.prepareTrustedRow(row);

        fixture.saveRelationData(
                "parent-1",
                List.of(fixture.relation),
                Map.of("details", List.of(row)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> inserted =
                ArgumentCaptor.forClass(Map.class);
        verify(fixture.dynamicMapper).insert(
                eq("wf_child"), inserted.capture());
        String childId = String.valueOf(
                inserted.getValue().get("id"));
        verify(fixture.claimService, times(1))
                .reconcileChildRecord(
                        eq("child"),
                        eq(childId),
                        anyMap(),
                        eq(List.of(
                                fixture.reference,
                                second)),
                        notNull());
        verify(fixture.claimService, never()).prepareAll(
                org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void trustedProductionChildWithoutParentPreparationFailsBeforeWrite() {
        Fixture fixture = new Fixture();
        Map<String, Object> row = new LinkedHashMap<>(
                Map.of("itemName", "明细A"));
        TrustedSubFormUniqueReference.attach(
                row,
                "child",
                fixture.reference);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> fixture.saveRelationData(
                        "parent-1",
                        List.of(fixture.relation),
                        Map.of("details", List.of(row))));

        assertEquals(
                "可信子表单缺少 out-of-band 写计划",
                exception.getMessage());
        verify(fixture.dynamicMapper, never()).insert(
                eq("wf_child"), anyMap());
        verify(fixture.dynamicMapper, never()).update(
                eq("wf_child"), anyMap());
    }

    @Test
    void strippedMarkerFailsAgainstOutOfBandPlanBeforeChildWrite() {
        Fixture fixture = new Fixture();
        Map<String, Object> row = fixture.trustedRow(
                Map.of("itemName", "明细A"));
        TrustedSubFormUniqueReference.removePrepared(row);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> fixture.saveRelationData(
                        "parent-1",
                        List.of(fixture.relation),
                        Map.of("details", List.of(row))));

        assertEquals(
                "可信子表单写计划与写入 payload 不一致",
                exception.getMessage());
        verify(fixture.dynamicMapper, never()).insert(
                eq("wf_child"), anyMap());
        verify(fixture.dynamicMapper, never()).update(
                eq("wf_child"), anyMap());
    }

    @Test
    void twoLevelTrustedChildrenKeepNestedMarkersUntilRecursiveInsert() {
        Fixture fixture = new Fixture();
        fixture.enableGrandchildren(
                EntityRelation.RelationType.ONE_TO_ONE);
        when(fixture.dynamicMapper.selectById(
                eq("wf_child"), anyString()))
                .thenAnswer(invocation -> Map.of(
                        "id", invocation.getArgument(1),
                        "item_name", "一级明细",
                        "parent_id", "parent-1",
                        "code", "C001",
                        "deleted", 0));
        when(fixture.dynamicMapper.selectById(
                eq("wf_grandchild"), anyString()))
                .thenAnswer(invocation -> Map.of(
                        "id", invocation.getArgument(1),
                        "detail_name", "二级明细",
                        "code", "G001",
                        "deleted", 0));
        Map<String, Object> grandchild = new LinkedHashMap<>();
        grandchild.put("detailName", "二级明细");
        grandchild.put("tags", List.of("A", "B"));
        TrustedSubFormUniqueReference.attach(
                grandchild,
                "grandchild",
                fixture.reference);
        Map<String, Object> child = new LinkedHashMap<>();
        child.put("itemName", "一级明细");
        child.put("attributes", Map.of("level", 1));
        child.put("items", grandchild);
        TrustedSubFormUniqueReference.attach(
                child,
                "child",
                fixture.reference);
        fixture.prepareTrustedRow(child);

        fixture.saveRelationData(
                "parent-1",
                List.of(fixture.relation),
                Map.of("details", List.of(child)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> childInsert =
                ArgumentCaptor.forClass(Map.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> grandchildInsert =
                ArgumentCaptor.forClass(Map.class);
        verify(fixture.dynamicMapper).insert(
                eq("wf_child"), childInsert.capture());
        verify(fixture.dynamicMapper).insert(
                eq("wf_grandchild"), grandchildInsert.capture());
        assertTrue(!childInsert.getValue().containsKey("items"));
        assertEquals(
                "{\"level\":1}",
                childInsert.getValue().get("attributes"));
        assertEquals(
                childInsert.getValue().get("id"),
                grandchildInsert.getValue().get("childId"));
        assertEquals(
                "[\"A\",\"B\"]",
                grandchildInsert.getValue().get("tags"));
        verify(fixture.claimService, times(2))
                .verifyChildPrepared(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        anyMap(),
                        eq(List.of(fixture.reference)),
                        notNull());
        InOrder order = inOrder(
                fixture.claimService,
                fixture.dynamicMapper);
        order.verify(fixture.claimService)
                .verifyChildPrepared(
                        eq("child"),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.argThat(record ->
                                record.get("items") instanceof Map<?, ?>
                                        && record.get("attributes")
                                        instanceof Map<?, ?>),
                        eq(List.of(fixture.reference)),
                        notNull());
        order.verify(fixture.dynamicMapper).insert(
                eq("wf_child"), anyMap());
        order.verify(fixture.claimService)
                .verifyChildPrepared(
                        eq("grandchild"),
                        org.mockito.ArgumentMatchers.anyString(),
                        anyMap(),
                        eq(List.of(fixture.reference)),
                        notNull());
        order.verify(fixture.dynamicMapper).insert(
                eq("wf_grandchild"), anyMap());
    }

    @Test
    void missingNestedMarkerFailsBeforeFirstChildInsert() {
        Fixture fixture = new Fixture();
        fixture.enableGrandchildren(
                EntityRelation.RelationType.ONE_TO_MANY);
        Map<String, Object> grandchild = new LinkedHashMap<>(
                Map.of("detailName", "二级明细"));
        TrustedSubFormUniqueReference.attach(
                grandchild,
                "grandchild",
                fixture.reference);
        Map<String, Object> child = new LinkedHashMap<>();
        child.put("itemName", "一级明细");
        child.put("items", List.of(grandchild));
        TrustedSubFormUniqueReference.attach(
                child,
                "child",
                fixture.reference);
        fixture.prepareTrustedRow(child);
        TrustedSubFormUniqueReference.removePrepared(grandchild);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> fixture.saveRelationData(
                        "parent-1",
                        List.of(fixture.relation),
                        Map.of("details", List.of(child))));

        assertEquals(
                "可信子表单写计划与写入 payload 不一致",
                exception.getMessage());
        verify(fixture.dynamicMapper, never()).insert(
                org.mockito.ArgumentMatchers.anyString(),
                anyMap());
        verify(fixture.dynamicMapper, never()).update(
                org.mockito.ArgumentMatchers.anyString(),
                anyMap());
        verify(fixture.dynamicMapper, never()).selectByCondition(
                org.mockito.ArgumentMatchers.anyString(),
                anyMap());
    }

    private static final class Fixture {

        private final EntityDataDynamicMapper dynamicMapper =
                mock(EntityDataDynamicMapper.class);
        private final EntityDefinitionMapper definitionMapper =
                mock(EntityDefinitionMapper.class);
        private final EntityFieldMapper fieldMapper =
                mock(EntityFieldMapper.class);
        private final EntityRelationMapper relationMapper =
                mock(EntityRelationMapper.class);
        private final DynamicTableService dynamicTableService =
                mock(DynamicTableService.class);
        private final EntityCodeGeneratorService codeGeneratorService =
                mock(EntityCodeGeneratorService.class);
        private final EntityFormUniqueClaimService claimService =
                mock(EntityFormUniqueClaimService.class);
        private final EntityFormUniqueClaimService preparer =
                new EntityFormUniqueClaimService(
                        mock(PublishedFormUniqueRuleService.class),
                        mock(EntityFormUniqueClaimRepository.class),
                        mock(EntityFormUniqueValueGateRepository.class));
        private final FormUniqueMutationContext.Reference reference =
                new FormUniqueMutationContext.Reference(
                        "child-form",
                        "child-release-1",
                        1,
                        "child-hotfix-2",
                        "child-hash-2",
                        "child-target-2");
        private final EntityRelation relation = relation();
        private final EntityRelationRuntimeService service;
        private PreparedUniqueClaims rootPrepared;

        private Fixture() {
            ObjectMapper objectMapper = new ObjectMapper();
            EntityDefinition child = new EntityDefinition();
            child.setId("child-id");
            child.setEntityCode("child");
            EntityField itemName = new EntityField();
            itemName.setFieldCode("itemName");
            itemName.setDbColumnName("item_name");
            itemName.setFieldType(EntityField.FieldType.STRING);
            when(definitionMapper.selectById("child-id"))
                    .thenReturn(child);
            when(definitionMapper.findByEntityCode("child"))
                    .thenReturn(Optional.of(child));
            when(fieldMapper.findByEntityId("child-id"))
                    .thenReturn(List.of(itemName));
            when(relationMapper.selectByParentEntityId("child-id"))
                    .thenReturn(List.of());
            when(dynamicTableService.tableExists("child"))
                    .thenReturn(true);
            when(dynamicTableService.getTableName("child"))
                    .thenReturn("wf_child");
            when(codeGeneratorService.generateCode("child"))
                    .thenReturn("C001");
            service = new EntityRelationRuntimeService(
                    dynamicMapper,
                    definitionMapper,
                    null,
                    fieldMapper,
                    relationMapper,
                    dynamicTableService,
                    objectMapper,
                    new EntityRuntimeRecordMapper(objectMapper),
                    codeGeneratorService,
                    null,
                    claimService);
            doAnswer(invocation -> {
                preparer.verifyRelationPrepared(
                        invocation.getArgument(0),
                        invocation.getArgument(1));
                return null;
            }).when(claimService).verifyRelationPrepared(
                    org.mockito.ArgumentMatchers.any(),
                    nullable(PreparedUniqueClaims.class));
            when(claimService.requiresRelationWrites(
                    nullable(PreparedUniqueClaims.class)))
                    .thenAnswer(invocation ->
                            preparer.requiresRelationWrites(
                                    invocation.getArgument(0)));
            doAnswer(invocation -> {
                preparer.verifyChildPrepared(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        invocation.getArgument(3),
                        invocation.getArgument(4));
                return null;
            }).when(claimService).verifyChildPrepared(
                    org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyString(),
                    anyMap(),
                    org.mockito.ArgumentMatchers.anyList(),
                    notNull());
        }

        private Map<String, Object> trustedRow(
                Map<String, Object> values) {
            Map<String, Object> row = new LinkedHashMap<>(values);
            TrustedSubFormUniqueReference.attach(
                    row, "child", reference);
            prepareTrustedRow(row);
            return row;
        }

        private void prepareTrustedRow(
                Map<String, Object> row) {
            rootPrepared = preparer.prepareAll(List.of(
                    EntityFormUniqueClaimService.Preparation.of(
                            "parent",
                            null,
                            Map.of(),
                            Map.of("details", List.of(row)),
                            List.of()))).get(0);
        }

        private void saveRelationData(
                String parentId,
                List<EntityRelation> relations,
                Map<String, Object> relationData) {
            service.saveRelationData(
                    parentId,
                    relations,
                    relationData,
                    rootPrepared);
        }

        private void existingChild(
                String id,
                String itemName) {
            Map<String, Object> stored = new LinkedHashMap<>();
            stored.put("id", id);
            stored.put("parentId", "parent-1");
            stored.put("itemName", itemName);
            stored.put("deleted", 0);
            when(dynamicMapper.selectByCondition(
                    eq("wf_child"), anyMap()))
                    .thenReturn(List.of(stored));
            when(dynamicMapper.selectByIdForUpdate(
                    "wf_child", id))
                    .thenReturn(stored);
        }

        private void enableGrandchildren(
                EntityRelation.RelationType relationType) {
            EntityDefinition grandchild = new EntityDefinition();
            grandchild.setId("grandchild-id");
            grandchild.setEntityCode("grandchild");
            EntityField detailName = new EntityField();
            detailName.setFieldCode("detailName");
            detailName.setDbColumnName("detail_name");
            detailName.setFieldType(EntityField.FieldType.STRING);
            EntityRelation nested = new EntityRelation();
            nested.setParentEntityId("child-id");
            nested.setParentEntityCode("child");
            nested.setDataKey("items");
            nested.setRelationCode("child_items");
            nested.setRelationName("二级明细");
            nested.setChildEntityId("grandchild-id");
            nested.setChildEntityCode("grandchild");
            nested.setChildRefFieldCode("childId");
            nested.setRelationType(relationType);
            nested.setOwnershipType(
                    EntityRelation.OwnershipType.COMPOSITION);
            nested.setEnabled(true);
            when(definitionMapper.selectById("grandchild-id"))
                    .thenReturn(grandchild);
            when(definitionMapper.findByEntityCode("grandchild"))
                    .thenReturn(Optional.of(grandchild));
            when(fieldMapper.findByEntityId("grandchild-id"))
                    .thenReturn(List.of(detailName));
            when(relationMapper.selectByParentEntityId("child-id"))
                    .thenReturn(List.of(nested));
            when(relationMapper.selectByParentEntityId("grandchild-id"))
                    .thenReturn(List.of());
            when(dynamicTableService.tableExists("grandchild"))
                    .thenReturn(true);
            when(dynamicTableService.getTableName("grandchild"))
                    .thenReturn("wf_grandchild");
            when(codeGeneratorService.generateCode("grandchild"))
                    .thenReturn("G001");
        }

        private static EntityRelation relation() {
            EntityRelation relation = new EntityRelation();
            relation.setParentEntityId("parent-id");
            relation.setParentEntityCode("parent");
            relation.setParentFieldCode("details");
            relation.setRelationCode("parent_details");
            relation.setRelationName("\u660e细");
            relation.setChildEntityId("child-id");
            relation.setChildEntityCode("child");
            relation.setChildRefFieldCode("parentId");
            relation.setRelationType(
                    EntityRelation.RelationType.ONE_TO_MANY);
            relation.setCascadeDelete(true);
            relation.setEnabled(true);
            return relation;
        }
    }
}
