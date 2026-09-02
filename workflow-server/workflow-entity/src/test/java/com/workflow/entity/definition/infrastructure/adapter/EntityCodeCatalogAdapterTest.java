package com.workflow.entity.definition.infrastructure.adapter;

import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 流程反查实体在 expand 阶段必须对历史重复绑定失败关闭。 */
class EntityCodeCatalogAdapterTest {

    private final EntityDefinitionMapper mapper =
            mock(EntityDefinitionMapper.class);
    private final EntityCodeCatalogAdapter adapter =
            new EntityCodeCatalogAdapter(mapper);

    @Test
    void returnsTheOnlyBinding() {
        EntityDefinition entity = entity("expense");
        when(mapper.findAllByProcessDefinitionId("12"))
                .thenReturn(List.of(entity));

        assertEquals("expense",
                adapter.findEntityCodeByProcessDefinitionId("12"));
    }

    @Test
    void returnsNullWhenUnbound() {
        when(mapper.findAllByProcessDefinitionId("12"))
                .thenReturn(List.of());

        assertNull(adapter.findEntityCodeByProcessDefinitionId("12"));
    }

    @Test
    void rejectsAmbiguousHistoricalBindings() {
        when(mapper.findAllByProcessDefinitionId("12"))
                .thenReturn(List.of(entity("expense"), entity("travel")));

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> adapter.findEntityCodeByProcessDefinitionId("12"));

        assertEquals("ENTITY_WORKFLOW_BINDING_AMBIGUOUS",
                exception.getErrorCode());
    }

    private EntityDefinition entity(String code) {
        EntityDefinition entity = new EntityDefinition();
        entity.setEntityCode(code);
        return entity;
    }
}
