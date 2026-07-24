package com.workflow.service.form;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.EntityFormField;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 实体表单字段运行时映射器测试。
 *
 * <p>被测对象：{@link EntityFormFieldRuntimeMapper}，覆盖节点可编辑时保留字段只读配置、
 * 节点只读时强制整型只读标志等场景。
 */
class EntityFormFieldRuntimeMapperTest {

    /** 测试节点可编辑时保留字段只读配置：验证只读字段映射后仍为 1 */
    @Test
    void nodeEditableKeepsFieldReadonlyConfiguration() {
        EntityFormField field = new EntityFormField();
        field.setFieldCode("lockedNote");
        field.setIsReadonly(1);

        assertEquals(1, EntityFormFieldRuntimeMapper.toMap(field, null, new ObjectMapper())
                .get("isReadonly"));
    }

    /** 测试节点只读时强制整型只读标志：验证原本可写字段在节点只读时映射为 1 */
    @Test
    void nodeReadonlyForcesIntegerReadonlyFlag() {
        EntityFormField field = new EntityFormField();
        field.setFieldCode("amount");
        field.setIsReadonly(0);

        assertEquals(1, EntityFormFieldRuntimeMapper.toMap(field, true, new ObjectMapper())
                .get("isReadonly"));
    }
}
