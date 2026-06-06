package com.workflow.mapper.provider;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

class EntityDataSqlProviderTest {

    private final EntityDataSqlProvider provider = new EntityDataSqlProvider();

    @Test
    void rejectsUnsafeTableName() {
        Map<String, Object> params = new HashMap<>();
        params.put("tableName", "entity_data_order;drop table sys_user");

        assertThrows(IllegalArgumentException.class, () -> provider.selectList(params));
    }

    @Test
    void rejectsUnsafeConditionField() {
        Map<String, Object> condition = new HashMap<>();
        condition.put("name) OR 1=1 --", "x");

        Map<String, Object> params = new HashMap<>();
        params.put("tableName", "entity_data_order");
        params.put("condition", condition);

        assertThrows(IllegalArgumentException.class, () -> provider.selectByCondition(params));
    }
}
