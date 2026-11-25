package com.workflow.entity.definition.application;

import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.application.EntityFieldFileItemService;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityRelationMapper;
import com.workflow.entity.definition.api.response.EntityFieldDTO;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldOptionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 发布状态独立决定绑定锁定，覆盖批量保存共用入口和单字段入口，不查询业务数据。 */
class EntityFieldDictionaryBindingTest {
    private final EntityDefinitionMapper entityMapper = mock(EntityDefinitionMapper.class);
    private final EntityFieldMapper fieldMapper = mock(EntityFieldMapper.class);
    private final EntityFieldDefinitionService service = new EntityFieldDefinitionService(
            entityMapper, fieldMapper, mock(EntityRelationMapper.class),
            mock(EntityFieldFileItemService.class),
            mock(EntityFieldValidationRuleService.class), mock(SystemEntityFieldPolicy.class),
            mock(EntityFieldOptionMapper.class));

    @ParameterizedTest
    @EnumSource(value = EntityField.FieldType.class, names = {"SELECT", "RADIO", "MULTI_SELECT", "CHECKBOX"})
    void publishedFieldRejectsRebindingAndClearingInSharedUpdate(EntityField.FieldType type) {
        for (String requested : new String[]{"other_dict", "", null}) {
            EntityField current = field(type, true, "expense_type");
            BusinessConflictException error = assertThrows(BusinessConflictException.class,
                    () -> service.updateDefinition(current, dto(type, requested)));
            assertEquals("ENTITY_FIELD_DICTIONARY_LOCKED", error.getErrorCode());
            assertEquals("expense_type", current.getDictType());
        }
        verify(fieldMapper, never()).updateById(any(EntityField.class));
    }

    @Test
    void unpublishedFieldCanBindAndReplaceButCannotSaveWithoutDictionary() {
        EntityField current = field(EntityField.FieldType.SELECT, false, null);
        for (String requested : new String[]{"expense_type", "other_dict"}) {
            service.updateDefinition(current, dto(current.getFieldType(), requested));
            assertEquals(requested, current.getDictType());
        }
        assertThrows(IllegalArgumentException.class, () -> service.updateDefinition(current, dto(current.getFieldType(), null)));
        verify(fieldMapper, times(2)).updateById(current);
    }

    @Test
    void publishedFieldCanSaveOtherPropertiesWithSameBinding() {
        EntityField current = field(EntityField.FieldType.SELECT, true, "expense_type");
        EntityFieldDTO dto = dto(current.getFieldType(), "expense_type");
        dto.setFieldName("费用类别");
        service.updateDefinition(current, dto);
        assertEquals("费用类别", current.getFieldName());
        assertEquals("expense_type", current.getDictType());
        verify(fieldMapper).updateById(current);
    }

    @Test
    void draftCanSwitchSourcesButPublicationLocksBothDirectionsAndTheInterface() {
        EntityField current = field(EntityField.FieldType.MULTI_SELECT, false, "expense_type");
        EntityFieldDTO external = dto(current.getFieldType(), null);
        external.setOptionsInterfaceId("interface-1");
        service.updateDefinition(current, external);
        assertNull(current.getDictType());
        assertEquals("interface-1", current.getOptionsInterfaceId());
        assertEquals("SCALAR", current.getValueStorage());

        current.setIsPublished(true);
        assertThrows(BusinessConflictException.class, () ->
                service.updateDefinition(current, dto(current.getFieldType(), "expense_type")));
        external.setOptionsInterfaceId("interface-2");
        assertThrows(BusinessConflictException.class, () -> service.updateDefinition(current, external));
        var dictionaryField = field(EntityField.FieldType.MULTI_SELECT, true, "expense_type");
        assertThrows(BusinessConflictException.class, () -> service.updateDefinition(dictionaryField, external));
    }

    @Test
    void externalValuesValidateStorageShapeWithoutLookingUpCandidates() {
        var single = field(EntityField.FieldType.SELECT, true, null);
        single.setOptionsInterfaceId("interface-1");
        assertDoesNotThrow(() -> EntitySelectionOptionPolicy.validateExternalValue(single, "001:A"));
        assertThrows(IllegalArgumentException.class, () ->
                EntitySelectionOptionPolicy.validateExternalValue(single, 1));
        var multiple = field(EntityField.FieldType.MULTI_SELECT, true, null);
        multiple.setOptionsInterfaceId("interface-1");
        assertDoesNotThrow(() -> EntitySelectionOptionPolicy.validateExternalValue(multiple, java.util.List.of("A1", "A10")));
        assertThrows(IllegalArgumentException.class, () ->
                EntitySelectionOptionPolicy.validateExternalValue(multiple, java.util.List.of("A1", "A1")));
        assertThrows(IllegalArgumentException.class, () ->
                EntitySelectionOptionPolicy.validateExternalValue(multiple, "A1,A10"));
        EntityFieldDTO invalid = dto(multiple.getFieldType(), "expense_type");
        invalid.setOptionsInterfaceId("interface-1");
        assertThrows(IllegalArgumentException.class, () -> EntitySelectionOptionPolicy.validateSubmitted(invalid));
    }

    @Test
    void publishedLegacyFieldMustBeReconfiguredAndStillCannotChangeItsBinding() {
        EntityField current = field(EntityField.FieldType.SELECT, true, null);
        assertThrows(IllegalArgumentException.class,
                () -> service.updateDefinition(current, dto(current.getFieldType(), "")));
        assertNull(current.getDictType());
        assertThrows(BusinessConflictException.class,
                () -> service.updateDefinition(current, dto(current.getFieldType(), "new_dict")));
        verify(fieldMapper, never()).updateById(any(EntityField.class));
    }

    @Test
    void singleFieldEndpointUsesPersistedPublicationStateRatherThanClientFlag() {
        EntityDefinition entity = new EntityDefinition();
        entity.setId("entity-1");
        entity.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
        when(entityMapper.findByIdForUpdate("entity-1")).thenReturn(Optional.of(entity));
        EntityField current = field(EntityField.FieldType.SELECT, true, "expense_type");
        when(fieldMapper.findByIdString("field-1")).thenReturn(current);
        EntityFieldDTO request = dto(current.getFieldType(), "other_dict");
        request.setIsPublished(false);
        BusinessConflictException error = assertThrows(BusinessConflictException.class,
                () -> service.updateField("entity-1", "field-1", request));
        assertEquals("ENTITY_FIELD_DICTIONARY_LOCKED", error.getErrorCode());
        verify(fieldMapper, never()).updateById(any(EntityField.class));
    }

    private EntityField field(EntityField.FieldType type, boolean published, String dictCode) {
        EntityField field = new EntityField();
        field.setId("field-1");
        field.setEntityId("entity-1");
        field.setFieldCode("expenseType");
        field.setFieldType(type);
        field.setIsPublished(published);
        field.setIsSystem(false);
        field.setDictType(dictCode);
        return field;
    }

    private EntityFieldDTO dto(EntityField.FieldType type, String dictCode) {
        EntityFieldDTO dto = new EntityFieldDTO();
        dto.setFieldName("费用类型");
        dto.setFieldCode("expenseType");
        dto.setFieldType(type);
        dto.setDictType(dictCode);
        return dto;
    }
}
