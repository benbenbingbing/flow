package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.application.EntityFieldFileItemService;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityRelationMapper;
import com.workflow.entity.definition.api.response.EntityFieldDTO;
import com.workflow.entity.definition.application.EntityFieldDefinitionService;
import com.workflow.entity.definition.application.EntityFieldOptionService;
import com.workflow.entity.definition.application.EntityFieldValidationRuleService;
import com.workflow.entity.definition.application.SystemEntityFieldPolicy;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 实体字段增量保存服务测试。
 */
@ExtendWith(MockitoExtension.class)
class EntityFieldDefinitionServiceTest {

    @Mock
    private EntityDefinitionMapper entityMapper;

    @Mock
    private EntityFieldMapper fieldMapper;

    @Mock
    private EntityRelationMapper relationMapper;

    @Mock
    private EntityFieldFileItemService fileItemService;

    @Mock
    private EntityFieldOptionService fieldOptionService;

    @Mock
    private EntityFieldValidationRuleService validationRuleService;

    @Mock
    private SystemEntityFieldPolicy systemEntityFieldPolicy;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private EntityFieldDefinitionService fieldService;

    private EntityDefinition entity;
    private EntityField existingField;

    @BeforeEach
    void setUp() {
        entity = new EntityDefinition();
        entity.setId("1");
        entity.setEntityCode("test_entity");
        entity.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);

        existingField = new EntityField();
        existingField.setId("f1");
        existingField.setEntityId("1");
        existingField.setFieldCode("name");
        existingField.setFieldName("名称");
        existingField.setFieldType(EntityField.FieldType.STRING);
        existingField.setDbType("VARCHAR");
        existingField.setDbColumnName("name");
        existingField.setIsSystem(false);
        existingField.setIsPublished(false);

        lenient().when(fieldOptionService.findOptions(anyString()))
                .thenReturn(List.of());
    }

    @Test
    void updateFieldKeepsOtherFieldsUntouched() {
        EntityFieldDTO fieldDTO = fieldDto(
                "name",
                "项目名称",
                EntityField.FieldType.STRING);
        fieldDTO.setFieldLength(200);
        fieldDTO.setIsRequired(true);
        fieldDTO.setSortOrder(2);

        when(entityMapper.selectById("1")).thenReturn(entity);
        when(fieldMapper.findByIdString("f1")).thenReturn(existingField);
        when(fieldMapper.findByEntityId("1"))
                .thenReturn(List.of(existingField));

        EntityFieldDTO result =
                fieldService.updateField("1", "f1", fieldDTO);

        assertEquals("f1", result.getId());
        assertEquals("项目名称", result.getFieldName());
        assertEquals(200, result.getFieldLength());
        assertTrue(Boolean.TRUE.equals(result.getIsRequired()));
        verify(fieldMapper).updateById(existingField);
        verify(fieldMapper, never()).deleteByEntityId(anyString());
        verify(fieldMapper, never()).deleteById(anyString());
        verify(relationMapper).deleteByParentField("1", "name");
        verify(entityMapper).updateById(entity);
    }

    @Test
    void createFieldReturnsGeneratedId() {
        EntityFieldDTO fieldDTO = fieldDto(
                "amount",
                "金额",
                EntityField.FieldType.DECIMAL);
        fieldDTO.setFieldLength(18);
        fieldDTO.setFieldPrecision(2);
        fieldDTO.setSortOrder(3);

        when(entityMapper.selectById("1")).thenReturn(entity);
        when(fieldMapper.findByEntityId("1"))
                .thenReturn(List.of(existingField));
        when(fieldMapper.insert(any(EntityField.class)))
                .thenAnswer(invocation -> {
                    EntityField saved = invocation.getArgument(0);
                    saved.setId("f2");
                    return 1;
                });

        EntityFieldDTO result =
                fieldService.createField("1", fieldDTO);

        assertEquals("f2", result.getId());
        assertEquals("amount", result.getFieldCode());
        assertFalse(Boolean.TRUE.equals(result.getIsPublished()));
        verify(fieldMapper).insert(any(EntityField.class));
        verify(fieldMapper, never()).deleteByEntityId(anyString());
        verify(entityMapper).updateById(entity);
    }

    @Test
    void systemFieldAllowsNonStructuralProperties() {
        existingField.setIsSystem(true);
        existingField.setIsPublished(true);
        existingField.setFieldLength(50);
        existingField.setIsRequired(false);
        existingField.setIsUnique(false);

        EntityFieldDTO fieldDTO = fieldDto(
                "name",
                "业务名称",
                EntityField.FieldType.STRING);
        fieldDTO.setDbType("TEXT");
        fieldDTO.setFieldLength(500);
        fieldDTO.setIsRequired(true);
        fieldDTO.setIsUnique(true);
        fieldDTO.setValidateRules("{\"minLength\":2,\"maxLength\":500}");

        when(entityMapper.selectById("1")).thenReturn(entity);
        when(fieldMapper.findByIdString("f1")).thenReturn(existingField);
        when(fieldMapper.findByEntityId("1"))
                .thenReturn(List.of(existingField));

        EntityFieldDTO result =
                fieldService.updateField("1", "f1", fieldDTO);

        assertEquals("name", existingField.getFieldCode());
        assertEquals(EntityField.FieldType.STRING, existingField.getFieldType());
        assertEquals("VARCHAR", existingField.getDbType());
        assertEquals("name", existingField.getDbColumnName());
        assertEquals("业务名称", result.getFieldName());
        assertEquals(500, result.getFieldLength());
        assertTrue(Boolean.TRUE.equals(result.getIsRequired()));
        assertTrue(Boolean.TRUE.equals(result.getIsUnique()));
        assertEquals(fieldDTO.getValidateRules(), result.getValidateRules());
    }

    @Test
    void systemFieldRejectsCodeAndTypeChanges() {
        existingField.setIsSystem(true);
        existingField.setIsPublished(true);
        when(entityMapper.selectById("1")).thenReturn(entity);
        when(fieldMapper.findByIdString("f1")).thenReturn(existingField);
        when(fieldMapper.findByEntityId("1"))
                .thenReturn(List.of(existingField));

        EntityFieldDTO changedCode = fieldDto(
                "renamed",
                "名称",
                EntityField.FieldType.STRING);
        BusinessConflictException codeError = assertThrows(
                BusinessConflictException.class,
                () -> fieldService.updateField(
                        "1",
                        "f1",
                        changedCode));
        assertEquals("ENTITY_FIELD_CODE_LOCKED", codeError.getErrorCode());

        EntityFieldDTO changedType = fieldDto(
                "name",
                "名称",
                EntityField.FieldType.TEXT);
        BusinessConflictException typeError = assertThrows(
                BusinessConflictException.class,
                () -> fieldService.updateField(
                        "1",
                        "f1",
                        changedType));
        assertEquals("ENTITY_FIELD_TYPE_LOCKED", typeError.getErrorCode());
    }

    private EntityFieldDTO fieldDto(
            String code,
            String name,
            EntityField.FieldType type) {
        EntityFieldDTO dto = new EntityFieldDTO();
        dto.setFieldCode(code);
        dto.setFieldName(name);
        dto.setFieldType(type);
        return dto;
    }
}
