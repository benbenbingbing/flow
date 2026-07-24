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

/**
 * 实体表单时间戳字段映射单元测试。
 *
 * <p>被测对象为 {@link EntityForm}、{@link EntityFormField} 与 {@link EntityFormMapper}，
 * 验证时间戳字段显式声明了数据库列名，且 Mapper 查询不含可能引发歧义的 create_time 排序。</p>
 */
class EntityFormTimestampMappingTest {

    /**
     * 表单与表单字段的时间戳字段应使用规范的数据库列名。
     *
     * <p>断言 createTime 映射到 create_time、updateTime 映射到 update_time。</p>
     */
    @Test
    void formTimestampsUseSchemaColumnNames() throws Exception {
        assertColumn(EntityForm.class, "createTime", "create_time");
        assertColumn(EntityForm.class, "updateTime", "update_time");
        assertColumn(EntityFormField.class, "createTime", "create_time");
        assertColumn(EntityFormField.class, "updateTime", "update_time");
    }

    /**
     * 表单 Mapper 的 selectByEntityId 查询 SQL 不应包含 create_time 字段名。
     *
     * <p>避免列名歧义，应使用表别名限定。</p>
     */
    @Test
    void formMapperOrdersBySchemaColumnName() throws Exception {
        Method method = EntityFormMapper.class.getDeclaredMethod("selectByEntityId", String.class);
        Select select = method.getAnnotation(Select.class);

        assertNotNull(select);
        String sql = String.join(" ", select.value()).toLowerCase();
        assertFalse(sql.contains("create_time"));
    }

    /**
     * 断言实体字段上声明了指定数据库列名的 TableField 注解。
     *
     * @param entityClass 实体类
     * @param fieldName 字段名
     * @param columnName 期望的数据库列名
     */
    private void assertColumn(Class<?> entityClass, String fieldName, String columnName) throws Exception {
        Field field = entityClass.getDeclaredField(fieldName);
        TableField tableField = field.getAnnotation(TableField.class);
        assertNotNull(tableField, entityClass.getSimpleName() + "." + fieldName + " should declare its DB column");
        assertEquals(columnName, tableField.value(), entityClass.getSimpleName() + "." + fieldName);
    }
}
