package com.workflow.entity.definition.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.data.application.DynamicTableService;
import com.workflow.entity.definition.api.response.EntityPublishHistoryDTO;
import com.workflow.entity.definition.api.response.EntityVersionDiffDTO;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityVersionDiffServiceTest {

    @Test
    void addedColumnPreviewUsesFullFieldDefinition() {
        EntityDefinitionMapper definitions = mock(EntityDefinitionMapper.class);
        EntityFieldMapper fields = mock(EntityFieldMapper.class);
        EntityPublishHistoryService history = mock(EntityPublishHistoryService.class);
        DynamicTableService tables = mock(DynamicTableService.class);
        EntityVersionDiffService service = new EntityVersionDiffService(
                definitions, fields, history, tables, new ObjectMapper());

        EntityDefinition entity = new EntityDefinition();
        entity.setId("entity-1");
        entity.setEntityCode("sample");
        entity.setEntityName("示例");
        when(definitions.selectById("entity-1")).thenReturn(entity);

        EntityPublishHistoryDTO published = new EntityPublishHistoryDTO();
        published.setVersion(1);
        published.setFields(List.of());
        when(history.getLatestVersion("entity-1")).thenReturn(published);

        EntityField amount = new EntityField();
        amount.setFieldCode("amount");
        amount.setFieldName("金额");
        amount.setFieldType(EntityField.FieldType.DECIMAL);
        amount.setFieldLength(12);
        amount.setFieldPrecision(2);
        amount.setDefaultValue("0");
        amount.setDbColumnName("custom_amount");
        when(fields.findByEntityId("entity-1")).thenReturn(List.of(amount));
        when(tables.buildAddColumnSqlPreviews(eq("sample"), anyList()))
                .thenReturn(List.of("ALTER TABLE sample ADD custom_amount DECIMAL(12,2) DEFAULT 0"));

        EntityVersionDiffDTO diff = service.getPendingPublishDiff("entity-1");

        // 增量发布的预览必须保留自定义物理列名和默认值，不能经过只含展示字段的 DTO 反向组装。
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EntityField>> addedFields = ArgumentCaptor.forClass(List.class);
        verify(tables).buildAddColumnSqlPreviews(eq("sample"), addedFields.capture());
        assertSame(amount, addedFields.getValue().get(0));
        assertEquals("custom_amount", addedFields.getValue().get(0).getDbColumnName());
        assertEquals("0", addedFields.getValue().get(0).getDefaultValue());
        assertEquals(12, diff.getAddedFields().get(0).getFieldLength());
        assertEquals(2, diff.getAddedFields().get(0).getFieldPrecision());
        assertEquals(1, diff.getPendingDdls().size());
    }
    @Test
    void pendingAndHistoricalDiffKeepSameChangesAndIgnoreMissingLegacyPhysicalMetadata() {
        var definitions = mock(EntityDefinitionMapper.class);
        var fields = mock(EntityFieldMapper.class);
        var history = mock(EntityPublishHistoryService.class);
        var service = new EntityVersionDiffService(definitions, fields, history, mock(DynamicTableService.class), new ObjectMapper());
        var entity = new EntityDefinition(); entity.setId("e1"); entity.setEntityCode("expense");
        when(definitions.selectById("e1")).thenReturn(entity);
        var previous = new com.workflow.entity.definition.api.response.EntityFieldDTO();
        previous.setFieldCode("amount"); previous.setFieldName("旧金额"); previous.setDefaultValue("0");
        var current = new EntityField(); current.setId("f1"); current.setFieldCode("amount");
        current.setFieldName("金额"); current.setDefaultValue("1"); current.setFieldLength(12);
        current.setFieldPrecision(2); current.setDbColumnName("custom_amount"); current.setIsRequired(true);
        when(fields.findByEntityId("e1")).thenReturn(List.of(current));
        // 使用独立构造的发布 DTO，防止测试和实现共同遗漏同一字段而产生假通过。
        var publishedCurrent = new com.workflow.entity.definition.api.response.EntityFieldDTO();
        publishedCurrent.setId("f1"); publishedCurrent.setFieldCode("amount"); publishedCurrent.setFieldName("金额");
        publishedCurrent.setDefaultValue("1"); publishedCurrent.setFieldLength(12); publishedCurrent.setFieldPrecision(2);
        publishedCurrent.setDbColumnName("custom_amount"); publishedCurrent.setIsRequired(true);
        var from = new EntityPublishHistoryDTO(); from.setVersion(1); from.setFields(List.of(previous));
        var to = new EntityPublishHistoryDTO(); to.setVersion(2); to.setFields(List.of(publishedCurrent));
        when(history.getLatestVersion("e1")).thenReturn(from);
        when(history.getVersion("e1", 1)).thenReturn(from); when(history.getVersion("e1", 2)).thenReturn(to);
        var pending = service.getPendingPublishDiff("e1"); var historical = service.compareVersions("e1", 1, 2);
        assertEquals(historical.getModifiedFields(), pending.getModifiedFields());
        assertEquals("字段名称: 旧金额 → 金额; 必填: null → true; 默认值: 0 → 1",
                pending.getModifiedFields().get(0).getChangeDescription());
        assertEquals(12, pending.getModifiedFields().get(0).getFieldLength());
        assertEquals(false, pending.getModifiedFields().get(0).getIsSystem());
    }

}
