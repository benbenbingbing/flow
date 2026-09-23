package com.workflow.entity.data.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.data.application.mapping.EntityRuntimeRecordMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.data.infrastructure.persistence.provider.EntityDataSqlProvider;
import com.workflow.entity.definition.application.EntityFieldValidationRuleService;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import org.apache.ibatis.builder.annotation.ProviderContext;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 唯一值预检必须带发布类型并保持 EQ/NE，不能因为字符串输入退回 LIKE。 */
class EntityDataMutationTypedUniqueQueryTest {
    @Test
    void uniquePrecheckUsesTypedExactComparisonAndStillExcludesTheCurrentRecord() throws Exception {
        var mapper = mock(EntityDataDynamicMapper.class);
        var tables = mock(DynamicTableService.class);
        var snapshots = mock(EntityPublishedSnapshotService.class);
        var field = new EntityField(); field.setFieldCode("serial"); field.setFieldType(EntityField.FieldType.LONG);
        field.setIsUnique(true);
        var snapshot = new EntityPublishedSnapshot(); snapshot.setFields(List.of(field));
        when(snapshots.getLatestByEntityCode("asset")).thenReturn(snapshot);
        when(tables.getTableName("asset")).thenReturn("biz_asset");
        var json = new ObjectMapper();
        var validator = new EntityDataMutationValidator(mapper, tables, snapshots, new EntityRuntimeRecordMapper(json),
                mock(EntityFieldValidationRuleService.class), json);
        var constructor = ProviderContext.class.getDeclaredConstructor(Class.class, java.lang.reflect.Method.class, String.class);
        constructor.setAccessible(true);
        var context = constructor.newInstance(Object.class, Object.class.getMethod("toString"), "MYSQL");

        when(mapper.countByCondition(eq("biz_asset"), anyMap())).thenAnswer(call -> {
            Map<String, Object> condition = call.getArgument(1);
            assertInstanceOf(EntityQueryConditions.class, condition);
            assertEquals("EQ", condition.get("serial_op")); assertEquals("NE", condition.get("id_op"));
            var params = new LinkedHashMap<String, Object>(); params.put("tableName", "biz_asset"); params.put("condition", condition);
            String sql = new EntityDataSqlProvider().countByCondition(params, context);
            assertFalse(sql.contains("LIKE")); assertTrue(sql.contains("`serial` = ")); assertTrue(sql.contains("`id` <> "));
            Map<?, ?> scalars = (Map<?, ?>) params.get("__conditionScalars");
            assertTrue(scalars.containsValue(Long.MAX_VALUE)); assertTrue(scalars.containsValue("current-record"));
            return 0L;
        });
        validator.validatePublishedFields("asset", Map.of("serial", "9223372036854775807"), "current-record");
        verify(mapper).countByCondition(eq("biz_asset"), anyMap());
    }
}
