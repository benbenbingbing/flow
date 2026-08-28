package com.workflow.entity.data.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationContext;
import com.workflow.contracts.entity.mutation.EntityMutationSourceType;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.form.uniqueness.application.FormUniqueMutationContext;
import com.workflow.entity.form.uniqueness.application.TrustedSubFormUniqueReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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

    @Test
    void createPreservesTrustedSubFormMarkerAcrossDtoConversion() {
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
        EntityAggregateWriter writer = new EntityAggregateWriter(
                queryService,
                mutationService,
                tableService,
                dynamicMapper,
                relationRuntimeService,
                new ObjectMapper());
        FormUniqueMutationContext.Reference reference =
                new FormUniqueMutationContext.Reference(
                        "child-form",
                        "release-1",
                        1,
                        "hotfix-2");
        Map<String, Object> child = new LinkedHashMap<>(
                Map.of("name", "明细A"));
        TrustedSubFormUniqueReference.attach(
                child, "child", reference);
        EntityDataDTO saved = new EntityDataDTO();
        saved.setId("parent-1");
        when(mutationService.save(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(saved);

        writer.apply(EntityMutationCommand.create(
                "parent",
                Map.of("data", Map.of(
                        "details", List.of(child))),
                EntityMutationContext.builder(
                                EntityMutationSourceType.FORM,
                                "CREATE_RECORD",
                                "新增")
                        .build()));

        ArgumentCaptor<EntityDataDTO> captor =
                ArgumentCaptor.forClass(EntityDataDTO.class);
        verify(mutationService).save(
                captor.capture(),
                org.mockito.ArgumentMatchers.isNull());
        @SuppressWarnings("unchecked")
        Map<String, Object> writtenChild =
                (Map<String, Object>) ((List<?>) captor.getValue()
                        .getData().get("details")).get(0);
        assertEquals(
                List.of(reference),
                TrustedSubFormUniqueReference.remove(
                        writtenChild));
    }
}
