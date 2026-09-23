package com.workflow.entity.definition.application;

import com.workflow.integration.database.api.*;
import com.workflow.core.database.port.SchemaMetadataPort;
import com.workflow.entity.definition.infrastructure.persistence.mapper.*;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SystemEntityCatalogServiceTest {
    /** 元数据端口返回的列属性必须保留，复合主键中的一列不能被误登记为单列唯一。 */
    @Test
    void synchronizesKnownSystemTablesUsingPortableMetadata() {
        var metadata = mock(SchemaMetadataPort.class);
        var definitions = mock(EntityDefinitionMapper.class);
        var fields = mock(EntityFieldMapper.class);
        when(definitions.findByEntityCode(anyString())).thenReturn(Optional.empty());
        when(metadata.tables()).thenReturn(List.of(new SchemaTableMetadata("sys_user", "用户"),
                new SchemaTableMetadata("sys_unknown", "未知系统表"), new SchemaTableMetadata("biz_orders", "订单")));
        when(metadata.columns("sys_user")).thenReturn(List.of(
                new SchemaColumnMetadata("user_id", "VARCHAR", Types.VARCHAR, 64L, null, null,
                        false, null, "用户编号", true, false, 1),
                new SchemaColumnMetadata("profile", "TEXT", Types.LONGVARCHAR, 65535L, null, null,
                        true, null, "用户资料", false, false, 2),
                new SchemaColumnMetadata("score", "DECIMAL", Types.DECIMAL, null, 10, 0,
                        true, "0", "积分", false, false, 3)));
        var service = new SystemEntityCatalogService(metadata, definitions, fields);
        assertEquals(1, service.synchronize());
        var captured = ArgumentCaptor.forClass(EntityField.class);
        verify(fields, times(3)).insert(captured.capture());
        var result = captured.getAllValues();
        assertEquals("用户编号", result.get(0).getFieldName());
        assertTrue(result.get(0).getIsRequired());
        assertFalse(result.get(0).getIsUnique());
        assertEquals(64, result.get(0).getFieldLength());
        assertEquals(EntityField.FieldType.TEXT, result.get(1).getFieldType());
        assertEquals(EntityField.FieldType.DECIMAL, result.get(2).getFieldType());
        assertTrue(result.stream().allMatch(field -> field.getIsSystem() && !field.getEditable()));
        verify(metadata, never()).columns("sys_unknown");
        verify(metadata, never()).columns("biz_orders");
    }
}
