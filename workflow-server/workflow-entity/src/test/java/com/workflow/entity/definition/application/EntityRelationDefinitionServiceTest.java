package com.workflow.entity.definition.application;

import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityRelationMapper;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.definition.api.request.EntityRelationSaveRequest;
import com.workflow.entity.definition.api.response.EntityRelationDTO;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityRelationDefinitionServiceTest {

    private final EntityDefinitionMapper entityMapper =
            mock(EntityDefinitionMapper.class);
    private final EntityFieldMapper fieldMapper =
            mock(EntityFieldMapper.class);
    private final EntityRelationMapper relationMapper =
            mock(EntityRelationMapper.class);
    private final EntityRelationDefinitionService service =
            new EntityRelationDefinitionService(
                    entityMapper, fieldMapper, relationMapper);

    @Test
    void availableCatalogReusesCanonicalDefinitionInBothDirections() {
        stubEntitiesAndForeignKey();
        EntityRelation relation = relation();
        when(relationMapper.selectAllByParentEntityId("parent-1")).thenReturn(List.of(relation));
        when(relationMapper.selectAllByChildEntityId("child-1")).thenReturn(List.of(relation));
        EntityRelationDTO forward = service.available("parent-1").get(0);
        EntityRelationDTO reverse = service.available("child-1").get(0);
        assertEquals("FORWARD", forward.getDirection());
        assertEquals("REVERSE", reverse.getDirection());
        assertEquals(forward.getRelationCode(), reverse.getRelationCode());
        assertEquals(forward.getParentEntityId(), reverse.getParentEntityId());
        assertEquals("订单", reverse.getParentEntityName());
        assertEquals(forward.getChildRefFieldCode(), reverse.getChildRefFieldCode());
    }

    @Test
    void generatesInternalKeysWithoutRequiringMatchingEntityFields() {
        stubEntitiesAndForeignKey();
        EntityRelationSaveRequest request = request();
        request.setRelationCode(null);
        request.setDataKey("");

        EntityRelationDTO result = service.create("parent-1", request);

        assertTrue(result.getRelationCode().matches("rel_[a-f0-9]{32}"));
        assertEquals(result.getRelationCode(), result.getDataKey());
        assertEquals("parentId", result.getChildRefFieldCode());
        assertNull(result.getParentFieldId());
    }

    @Test
    void separatesCustomRelationCodeFromConflictingParentField() {
        stubEntitiesAndForeignKey();
        EntityRelationSaveRequest request = request();
        request.setRelationCode("reqRelation");
        request.setDataKey(null);
        when(fieldMapper.findByEntityIdAndFieldCode("parent-1", "reqRelation"))
                .thenReturn(new EntityField());

        EntityRelationDTO result = service.create("parent-1", request);

        assertEquals("reqRelation", result.getRelationCode());
        assertNotEquals("reqRelation", result.getDataKey());
        assertTrue(result.getDataKey().startsWith("rel_"));
    }

    @Test
    void preservesHistoricalKeysWhenSimpleEditorOmitsThem() {
        stubEntitiesAndForeignKey();
        when(relationMapper.selectById("relation-1")).thenReturn(relation());
        EntityRelationSaveRequest request = request();
        request.setRelationCode(null);
        request.setDataKey(null);

        EntityRelationDTO result = service.update("parent-1", "relation-1", request);

        assertEquals("detail_relation", result.getRelationCode());
        assertEquals("details", result.getDataKey());
    }

    @Test
    void generatedDataKeyDoesNotReuseRetiredKey() {
        stubEntitiesAndForeignKey();
        EntityRelation retired = relation();
        retired.setDeleted(1);
        when(relationMapper.selectByDataKey("parent-1", "detail_relation")).thenReturn(retired);
        EntityRelationSaveRequest request = request();
        request.setDataKey(null);

        EntityRelationDTO result = service.create("parent-1", request);

        assertNotEquals("detail_relation", result.getDataKey());
    }

    @Test
    void createsRelationWithoutSubFormField() {
        stubEntitiesAndForeignKey();
        EntityRelationSaveRequest request = request();

        EntityRelationDTO result = service.create("parent-1", request);

        ArgumentCaptor<EntityRelation> captor =
                ArgumentCaptor.forClass(EntityRelation.class);
        verify(relationMapper).insert(captor.capture());
        EntityRelation saved = captor.getValue();
        assertEquals("details", saved.getDataKey());
        assertEquals("detail_relation", saved.getRelationCode());
        assertNull(saved.getParentFieldCode());
        assertEquals(
                EntityRelation.OwnershipType.COMPOSITION,
                saved.getOwnershipType());
        assertEquals("details", result.getDataKey());
    }

    @Test
    void rejectsDuplicateDataKeyWithinParentEntity() {
        stubEntitiesAndForeignKey();
        EntityRelation duplicate = new EntityRelation();
        duplicate.setId("relation-existing");
        when(relationMapper.selectByDataKey(
                "parent-1", "details"))
                .thenReturn(duplicate);

        BusinessConflictException failure = assertThrows(
                BusinessConflictException.class,
                () -> service.create("parent-1", request()));

        assertEquals(
                "ENTITY_RELATION_DATA_KEY_DUPLICATE",
                failure.getErrorCode());
    }

    @Test
    void rejectsDataKeyThatShadowsParentField() {
        stubEntitiesAndForeignKey();
        EntityField parentField = new EntityField();
        parentField.setId("parent-field-1");
        parentField.setEntityId("parent-1");
        parentField.setFieldCode("details");
        when(fieldMapper.findByEntityIdAndFieldCode(
                "parent-1", "details"))
                .thenReturn(parentField);

        BusinessConflictException failure = assertThrows(
                BusinessConflictException.class,
                () -> service.create("parent-1", request()));

        assertEquals(
                "ENTITY_RELATION_DATA_KEY_FIELD_CONFLICT",
                failure.getErrorCode());
    }

    @Test
    void allowsExplicitLegacyFieldBindingForSameDataKey() {
        stubEntitiesAndForeignKey();
        EntityField parentField = new EntityField();
        parentField.setId("parent-field-1");
        parentField.setEntityId("parent-1");
        parentField.setFieldCode("details");
        when(fieldMapper.findByEntityIdAndFieldCode(
                "parent-1", "details"))
                .thenReturn(parentField);
        EntityRelationSaveRequest request = request();
        request.setParentFieldId("parent-field-1");
        request.setParentFieldCode("details");

        assertDoesNotThrow(() ->
                service.create("parent-1", request));
    }

    @Test
    void rejectsReservedRuntimeDataKey() {
        stubEntitiesAndForeignKey();
        EntityRelationSaveRequest request = request();
        request.setDataKey("title");

        BusinessConflictException failure = assertThrows(
                BusinessConflictException.class,
                () -> service.create("parent-1", request));

        assertEquals(
                "ENTITY_RELATION_DATA_KEY_RESERVED",
                failure.getErrorCode());
    }

    @Test
    void reportsRetiredRelationCodeBeforeDatabaseConstraint() {
        stubEntitiesAndForeignKey();
        EntityRelation retired = relation();
        retired.setId("retired-relation");
        retired.setDeleted(1);
        when(relationMapper.selectByRelationCode(
                "parent-1", "detail_relation"))
                .thenReturn(retired);

        BusinessConflictException failure = assertThrows(
                BusinessConflictException.class,
                () -> service.create("parent-1", request()));

        assertEquals(
                "ENTITY_RELATION_CODE_RETIRED",
                failure.getErrorCode());
    }

    @Test
    void reportsRetiredDataKeyBeforeDatabaseConstraint() {
        stubEntitiesAndForeignKey();
        EntityRelation retired = relation();
        retired.setId("retired-relation");
        retired.setRelationCode("retired_relation");
        retired.setDeleted(1);
        when(relationMapper.selectByDataKey(
                "parent-1", "details"))
                .thenReturn(retired);

        BusinessConflictException failure = assertThrows(
                BusinessConflictException.class,
                () -> service.create("parent-1", request()));

        assertEquals(
                "ENTITY_RELATION_DATA_KEY_RETIRED",
                failure.getErrorCode());
    }

    @Test
    void rejectsCascadeDeleteForAssociation() {
        stubEntitiesAndForeignKey();
        EntityRelationSaveRequest request = request();
        request.setOwnershipType(
                EntityRelation.OwnershipType.ASSOCIATION);
        request.setCascadeDelete(true);

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> service.create("parent-1", request));

        assertEquals("普通关联关系不能开启级联删除", failure.getMessage());
    }

    @Test
    void ordinaryIdFieldCanBeCreatedUpdatedAndPublished() {
        stubEntitiesAndForeignKey();
        EntityField field = childReference(EntityField.FieldType.STRING, null);
        field.setFieldLength(128);
        when(fieldMapper.findByEntityIdAndFieldCode("child-1", "parentId")).thenReturn(field);
        EntityRelationSaveRequest request = request();
        request.setOwnershipType(EntityRelation.OwnershipType.ASSOCIATION);
        assertEquals("parentId", service.create("parent-1", request).getChildRefFieldCode());
        when(relationMapper.selectById("relation-1")).thenReturn(relation());
        assertDoesNotThrow(() -> service.update("parent-1", "relation-1", request));
        stubPublishedRelation();
        assertDoesNotThrow(() -> service.validateForPublish("parent-1"));
    }

    @Test
    void rejectsPublishWhenChildFieldIsNotIdCompatible() {
        stubEntitiesAndForeignKey();
        EntityField foreignKey = childReference(
                EntityField.FieldType.LONG, null);
        when(fieldMapper.findByEntityIdAndFieldCode(
                "child-1", "parentId"))
                .thenReturn(foreignKey);
        stubPublishedRelation();

        BusinessConflictException failure = assertThrows(
                BusinessConflictException.class,
                () -> service.validateForPublish("parent-1"));

        assertEquals(
                "ENTITY_RELATION_CHILD_REF_TYPE_INVALID",
                failure.getErrorCode());
    }

    @Test
    void rejectsPublishWhenChildReferenceTargetsAnotherEntity() {
        stubEntitiesAndForeignKey();
        EntityField foreignKey = childReference(
                EntityField.FieldType.REFERENCE, "another-parent");
        when(fieldMapper.findByEntityIdAndFieldCode(
                "child-1", "parentId"))
                .thenReturn(foreignKey);
        stubPublishedRelation();

        BusinessConflictException failure = assertThrows(
                BusinessConflictException.class,
                () -> service.validateForPublish("parent-1"));

        assertEquals(
                "ENTITY_RELATION_CHILD_REF_TARGET_INVALID",
                failure.getErrorCode());
    }

    @Test
    void associationUsesTheSameAuthoritativeReferenceInvariant() {
        stubEntitiesAndForeignKey();
        EntityRelation existing = relation();
        existing.setOwnershipType(
                EntityRelation.OwnershipType.ASSOCIATION);
        when(relationMapper.selectByParentEntityId("parent-1"))
                .thenReturn(List.of(existing));
        when(relationMapper.selectByRelationCode(
                "parent-1", "detail_relation"))
                .thenReturn(existing);
        when(relationMapper.selectByDataKey(
                "parent-1", "details"))
                .thenReturn(existing);

        assertDoesNotThrow(() ->
                service.validateForPublish("parent-1"));
    }

    @Test
    void keepsRelationCodeAndDataKeyStableOnUpdate() {
        stubEntitiesAndForeignKey();
        EntityRelation existing = relation();
        when(relationMapper.selectById("relation-1"))
                .thenReturn(existing);
        EntityRelationSaveRequest request = request();
        request.setDataKey("renamedDetails");

        BusinessConflictException failure = assertThrows(
                BusinessConflictException.class,
                () -> service.update(
                        "parent-1", "relation-1", request));

        assertEquals(
                "ENTITY_RELATION_DATA_KEY_LOCKED",
                failure.getErrorCode());
    }

    @Test
    void staleLegacyFieldBindingDoesNotBlockRelationPublish() {
        stubEntitiesAndForeignKey();
        EntityRelation existing = relation();
        existing.setParentFieldId("deleted-field-id");
        existing.setParentFieldCode("deletedField");
        when(relationMapper.selectByParentEntityId("parent-1"))
                .thenReturn(List.of(existing));
        when(relationMapper.selectByRelationCode(
                "parent-1", "detail_relation"))
                .thenReturn(existing);
        when(relationMapper.selectByDataKey(
                "parent-1", "details"))
                .thenReturn(existing);

        assertDoesNotThrow(() ->
                service.validateForPublish("parent-1"));
    }

    @Test
    void rejectsParentPublishWhenChildEntityIsNotPublished() {
        stubEntitiesAndForeignKey();
        EntityDefinition child = definition(
                "child-1", "order_detail", "订单明细");
        child.setStatus(EntityDefinition.Status.DRAFT);
        when(entityMapper.selectById("child-1")).thenReturn(child);
        EntityRelation existing = relation();
        when(relationMapper.selectByParentEntityId("parent-1"))
                .thenReturn(List.of(existing));
        when(relationMapper.selectByRelationCode(
                "parent-1", "detail_relation"))
                .thenReturn(existing);
        when(relationMapper.selectByDataKey(
                "parent-1", "details"))
                .thenReturn(existing);

        BusinessConflictException failure = assertThrows(
                BusinessConflictException.class,
                () -> service.validateForPublish("parent-1"));

        assertEquals(
                "ENTITY_RELATION_CHILD_NOT_PUBLISHED",
                failure.getErrorCode());
    }

    private void stubEntitiesAndForeignKey() {
        EntityDefinition parent = definition(
                "parent-1", "order", "订单");
        EntityDefinition child = definition(
                "child-1", "order_detail", "订单明细");
        when(entityMapper.selectById("parent-1"))
                .thenReturn(parent);
        when(entityMapper.selectById("child-1"))
                .thenReturn(child);
        EntityField foreignKey = childReference(
                EntityField.FieldType.REFERENCE, "parent-1");
        when(fieldMapper.findByEntityIdAndFieldCode(
                "child-1", "parentId"))
                .thenReturn(foreignKey);
        when(relationMapper.insert(any(EntityRelation.class))).thenReturn(1);
    }

    private void stubPublishedRelation() {
        EntityRelation existing = relation();
        when(relationMapper.selectByParentEntityId("parent-1"))
                .thenReturn(List.of(existing));
        when(relationMapper.selectByRelationCode(
                "parent-1", "detail_relation"))
                .thenReturn(existing);
        when(relationMapper.selectByDataKey(
                "parent-1", "details"))
                .thenReturn(existing);
    }

    private EntityField childReference(
            EntityField.FieldType type,
            String targetEntityId) {
        EntityField field = new EntityField();
        field.setId("field-parent-id");
        field.setEntityId("child-1");
        field.setFieldCode("parentId");
        field.setFieldType(type);
        field.setRefEntityId(targetEntityId);
        return field;
    }

    private EntityRelationSaveRequest request() {
        EntityRelationSaveRequest request =
                new EntityRelationSaveRequest();
        request.setRelationCode("detail_relation");
        request.setRelationName("订单明细");
        request.setDataKey("details");
        request.setChildEntityId("child-1");
        request.setChildRefFieldCode("parentId");
        request.setRelationType(
                EntityRelation.RelationType.ONE_TO_MANY);
        return request;
    }

    private EntityRelation relation() {
        EntityRelation relation = new EntityRelation();
        relation.setId("relation-1");
        relation.setParentEntityId("parent-1");
        relation.setParentEntityCode("order");
        relation.setRelationCode("detail_relation");
        relation.setRelationName("订单明细");
        relation.setDataKey("details");
        relation.setChildEntityId("child-1");
        relation.setChildEntityCode("order_detail");
        relation.setChildRefFieldCode("parentId");
        relation.setRelationType(
                EntityRelation.RelationType.ONE_TO_MANY);
        relation.setOwnershipType(
                EntityRelation.OwnershipType.COMPOSITION);
        relation.setEnabled(true);
        relation.setDeleted(0);
        return relation;
    }

    private EntityDefinition definition(
            String id, String code, String name) {
        EntityDefinition definition = new EntityDefinition();
        definition.setId(id);
        definition.setEntityCode(code);
        definition.setEntityName(name);
        definition.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
        definition.setStatus(EntityDefinition.Status.PUBLISHED);
        return definition;
    }
}
