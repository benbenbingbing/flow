package com.workflow.service;

import com.workflow.common.BusinessConflictException;
import com.workflow.entity.EntityDefinition;
import com.workflow.mapper.EntityDefinitionMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 实体定义访问策略测试。
 *
 * <p>被测对象：{@link EntityDefinitionAccessPolicy}，覆盖动态实体可使用设计器服务、
 * 系统实体受保护不可使用动态设计器服务等场景。
 */
class EntityDefinitionAccessPolicyTest {

    /** 测试动态实体可使用设计器服务：验证返回的实体与查到的一致 */
    @Test
    void dynamicEntityCanUseDesignerServices() {
        EntityDefinitionMapper mapper = mock(EntityDefinitionMapper.class);
        EntityDefinition entity = new EntityDefinition();
        entity.setEntityCode("expense");
        entity.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
        when(mapper.findByEntityCode("expense")).thenReturn(Optional.of(entity));

        EntityDefinitionAccessPolicy policy = new EntityDefinitionAccessPolicy(mapper);

        assertSame(entity, policy.requireDynamicByCode("expense"));
    }

    /** 测试系统实体不能使用动态设计器服务：验证抛出业务冲突异常且错误码为 ENTITY_SYSTEM_DEFINITION_PROTECTED */
    @Test
    void systemEntityCannotUseDynamicDesignerServices() {
        EntityDefinitionMapper mapper = mock(EntityDefinitionMapper.class);
        EntityDefinition entity = new EntityDefinition();
        entity.setEntityCode("sys_user");
        entity.setStorageMode(EntityDefinition.StorageMode.SYSTEM);
        when(mapper.findByEntityCode("sys_user")).thenReturn(Optional.of(entity));

        EntityDefinitionAccessPolicy policy = new EntityDefinitionAccessPolicy(mapper);
        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> policy.requireDynamicByCode("sys_user"));

        assertEquals("ENTITY_SYSTEM_DEFINITION_PROTECTED", exception.getErrorCode());
    }
}
