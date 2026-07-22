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

class EntityDefinitionAccessPolicyTest {

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
