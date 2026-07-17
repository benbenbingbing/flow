package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.workflow.mapper.EntityFormMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityFormTimestampMappingTest {

    @Test
    void formTimestampsUseSchemaColumnNames() throws Exception {
        assertColumn(EntityForm.class, "createTime", "create_time");
        assertColumn(EntityForm.class, "updateTime", "update_time");
        assertColumn(EntityFormField.class, "createTime", "create_time");
        assertColumn(EntityFormField.class, "updateTime", "update_time");
    }

    @Test
    void formMapperOrdersBySchemaColumnName() throws Exception {
        Method method = EntityFormMapper.class.getDeclaredMethod("selectByEntityId", String.class);
        Select select = method.getAnnotation(Select.class);

        assertNotNull(select);
        String sql = String.join(" ", select.value()).toLowerCase();
        assertFalse(sql.contains("create_time"));
    }

    private void assertColumn(Class<?> entityClass, String fieldName, String columnName) throws Exception {
        Field field = entityClass.getDeclaredField(fieldName);
        TableField tableField = field.getAnnotation(TableField.class);
        assertNotNull(tableField, entityClass.getSimpleName() + "." + fieldName + " should declare its DB column");
        assertEquals(columnName, tableField.value(), entityClass.getSimpleName() + "." + fieldName);
    }
}
