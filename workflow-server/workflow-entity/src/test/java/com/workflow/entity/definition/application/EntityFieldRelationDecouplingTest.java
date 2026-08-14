package com.workflow.entity.definition.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.data.application.EntityFieldFileItemService;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityRelationMapper;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.definition.api.response.EntityFieldDTO;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityFieldRelationDecouplingTest {

    @Test
    void creatingLegacySubFormFieldDoesNotCreateOrDeleteRelation() {
        EntityDefinitionMapper entityMapper =
                mock(EntityDefinitionMapper.class);
        EntityFieldMapper fieldMapper = mock(EntityFieldMapper.class);
        EntityRelationMapper relationMapper =
                mock(EntityRelationMapper.class);
        EntityDefinition parent = new EntityDefinition();
        parent.setId("parent-1");
        parent.setEntityCode("order");
        parent.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
        when(entityMapper.selectById("parent-1")).thenReturn(parent);

        EntityFieldDefinitionService service =
                new EntityFieldDefinitionService(
                        entityMapper,
                        fieldMapper,
                        relationMapper,
                        mock(EntityFieldFileItemService.class),
                        mock(EntityFieldOptionService.class),
                        mock(EntityFieldValidationRuleService.class),
                        mock(SystemEntityFieldPolicy.class),
                        new ObjectMapper());
        EntityFieldDTO field = new EntityFieldDTO();
        field.setFieldCode("details");
        field.setFieldName("订单明细");
        field.setFieldType(EntityField.FieldType.SUB_FORM);
        field.setChildEntityId("child-1");
        field.setChildRefFieldCode("parentId");

        service.createField("parent-1", field);

        verify(relationMapper, never()).insert(any(EntityRelation.class));
        verify(relationMapper, never()).deleteByParentField(any(), any());
        verify(relationMapper, never()).deleteByParentEntityId(any());
    }

    @Test
    void legacyBulkSyncIsCompatibilityNoOp() {
        EntityRelationMapper relationMapper =
                mock(EntityRelationMapper.class);
        EntityFieldDefinitionService service =
                new EntityFieldDefinitionService(
                        mock(EntityDefinitionMapper.class),
                        mock(EntityFieldMapper.class),
                        relationMapper,
                        mock(EntityFieldFileItemService.class),
                        mock(EntityFieldOptionService.class),
                        mock(EntityFieldValidationRuleService.class),
                        mock(SystemEntityFieldPolicy.class),
                        new ObjectMapper());

        service.syncRelations(
                new EntityDefinition(),
                List.of(new EntityFieldDTO()),
                List.of(new EntityField()));

        verify(relationMapper, never()).insert(any(EntityRelation.class));
        verify(relationMapper, never()).deleteByParentField(any(), any());
        verify(relationMapper, never()).deleteByParentEntityId(any());
    }
}
