package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VarcharTableIdStrategyTest {

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
