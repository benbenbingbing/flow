package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 主键 ID 策略单元测试。
 *
 * <p>验证使用 varchar 主键的实体类均声明了 ASSIGN_ID 策略，
 * 确保 MyBatis-Plus 雪花算法生成字符串 ID。</p>
 */
class VarcharTableIdStrategyTest {

    /**
     * 所有 varchar 主键实体类的 id 字段应使用 ASSIGN_ID 策略。
     *
     * <p>覆盖 FormFieldConfig、AssigneeConfig、EntityData 等 8 个实体类，
     * 逐一反射读取 @TableId 注解并断言 type 为 ASSIGN_ID。</p>
     */
    @Test
    void varcharPrimaryKeyEntitiesUseAssignedIds() throws Exception {
        List<Class<?>> entities = List.of(
                FormFieldConfig.class,
                AssigneeConfig.class,
                EntityData.class,
                ProcessVersionHistory.class,
                FormConfig.class,
                NodeConfig.class,
                ProcessDefinitionConfig.class,
                EntityField.class
        );

        for (Class<?> entity : entities) {
            Field idField = entity.getDeclaredField("id");
            TableId tableId = idField.getAnnotation(TableId.class);
            assertEquals(IdType.ASSIGN_ID, tableId.type(), entity.getSimpleName() + ".id should generate varchar IDs");
        }
    }
}
