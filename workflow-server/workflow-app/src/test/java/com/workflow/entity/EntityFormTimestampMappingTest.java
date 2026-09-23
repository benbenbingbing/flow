package com.workflow.entity;

import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;

import com.baomidou.mybatisplus.annotation.TableField;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 实体表单时间戳字段映射单元测试。
 *
 * <p>被测对象为 {@link EntityForm} 与 {@link EntityFormMapper}，
 * 验证时间戳字段显式声明了数据库列名，且 Mapper 查询不含可能引发歧义的 create_time 排序。</p>
 */
class EntityFormTimestampMappingTest {

    /**
     * 持久化表单的时间戳字段应使用规范的数据库列名。
     *
     * <p>断言 createTime 映射到 create_time、updateTime 映射到 update_time。</p>
     * <p>EntityFormField 已改为节点或快照生成的运行视图，不再要求数据库列映射。</p>
     */
    @Test
    void formTimestampsUseSchemaColumnNames() throws Exception {
        assertColumn(EntityForm.class, "createTime", "create_time");
        assertColumn(EntityForm.class, "updateTime", "update_time");
    }

    /**
     * 表单实体查询不额外追加时间排序，字段条件由实体映射生成。
     */
    @Test
    void formMapperDoesNotAddTimestampOrdering() {
        var configuration = new MybatisConfiguration();
        configuration.setDatabaseId("MYSQL");
        configuration.addMapper(EntityFormMapper.class);
        var mapper = mock(EntityFormMapper.class, CALLS_REAL_METHODS);
        doReturn(List.of()).when(mapper).selectList(any(Wrapper.class));
        mapper.selectByEntityId("entity");
        var wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectList(wrapper.capture());
        assertFalse(wrapper.getValue().getSqlSegment().toLowerCase().contains("order by"));
        assertFalse(wrapper.getValue().getSqlSegment().contains("create_time"));
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
