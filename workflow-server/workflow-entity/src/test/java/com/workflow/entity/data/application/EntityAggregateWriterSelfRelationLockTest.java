package com.workflow.entity.data.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Map;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EntityAggregateWriterSelfRelationLockTest {

    @Test
    void locksSelfRelationGuardBeforeBusinessRecord() {
        EntityDataDynamicService queryService =
                mock(EntityDataDynamicService.class);
        EntityDataMutationService mutationService =
                mock(EntityDataMutationService.class);
        DynamicTableService tableService =
                mock(DynamicTableService.class);
        EntityDataDynamicMapper dynamicMapper =
                mock(EntityDataDynamicMapper.class);
        EntityRelationRuntimeService relationRuntimeService =
                mock(EntityRelationRuntimeService.class);
        when(tableService.getTableName("node"))
                .thenReturn("wf_node");
        when(dynamicMapper.selectByIdForUpdate(
                "wf_node", "record-1"))
                .thenReturn(Map.of("id", "record-1"));
        EntityAggregateWriter writer = new EntityAggregateWriter(
                queryService,
                mutationService,
                tableService,
                dynamicMapper,
                relationRuntimeService,
                new ObjectMapper());

        writer.lock("node", "record-1");

        InOrder order = inOrder(
                relationRuntimeService,
                dynamicMapper);
        order.verify(relationRuntimeService)
                .lockSelfRelationGuard("node");
        order.verify(dynamicMapper)
                .selectByIdForUpdate("wf_node", "record-1");
    }
}
