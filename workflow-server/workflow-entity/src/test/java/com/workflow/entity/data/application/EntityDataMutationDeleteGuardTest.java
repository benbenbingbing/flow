package com.workflow.entity.data.application;

import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.contracts.process.ProcessRuntimePort;
import com.workflow.entity.data.application.mapping.EntityRuntimeRecordMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.definition.application.EntityCodeGeneratorService;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityStatusMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Optional;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EntityDataMutationDeleteGuardTest {

    @Test
    void logicalDeleteObtainsReleaseGuardBeforeCascadeAndRootDelete() {
        Fixture fixture = fixture();

        fixture.service.delete("node", "record-1");

        InOrder order = inOrder(
                fixture.relationRuntimeService,
                fixture.definitionMapper,
                fixture.multiValueRuntimeService,
                fixture.dynamicMapper);
        order.verify(fixture.relationRuntimeService)
                .lockSelfRelationGuard("node");
        order.verify(fixture.definitionMapper)
                .findByEntityCode("node");
        order.verify(fixture.dynamicMapper)
                .selectByIdForUpdate("wf_node", "record-1");
        order.verify(fixture.relationRuntimeService)
                .cascadeDeleteRelations(
                        fixture.definition,
                        "record-1",
                        false);
        order.verify(fixture.multiValueRuntimeService)
                .delete("node", "record-1");
        order.verify(fixture.dynamicMapper)
                .deleteById("wf_node", "record-1");
    }

    @Test
    void physicalDeleteObtainsReleaseGuardBeforeCascadeAndRootDelete() {
        Fixture fixture = fixture();

        fixture.service.physicalDelete("node", "record-1");

        InOrder order = inOrder(
                fixture.relationRuntimeService,
                fixture.definitionMapper,
                fixture.multiValueRuntimeService,
                fixture.dynamicMapper);
        order.verify(fixture.relationRuntimeService)
                .lockSelfRelationGuard("node");
        order.verify(fixture.definitionMapper)
                .findByEntityCode("node");
        order.verify(fixture.dynamicMapper)
                .selectByIdForUpdate("wf_node", "record-1");
        order.verify(fixture.relationRuntimeService)
                .cascadeDeleteRelations(
                        fixture.definition,
                        "record-1",
                        true);
        order.verify(fixture.multiValueRuntimeService)
                .delete("node", "record-1");
        order.verify(fixture.dynamicMapper)
                .physicalDeleteById("wf_node", "record-1");
    }

    private Fixture fixture() {
        EntityDataDynamicMapper dynamicMapper =
                mock(EntityDataDynamicMapper.class);
        EntityDefinitionMapper definitionMapper =
                mock(EntityDefinitionMapper.class);
        EntityStatusMapper statusMapper =
                mock(EntityStatusMapper.class);
        DynamicTableService tableService =
                mock(DynamicTableService.class);
        EntityCodeGeneratorService codeGeneratorService =
                mock(EntityCodeGeneratorService.class);
        EntityRuntimeRecordMapper recordMapper =
                mock(EntityRuntimeRecordMapper.class);
        EntityRelationRuntimeService relationRuntimeService =
                mock(EntityRelationRuntimeService.class);
        EntityMultiValueRuntimeService multiValueRuntimeService =
                mock(EntityMultiValueRuntimeService.class);
        ProcessRuntimePort processRuntimePort =
                mock(ProcessRuntimePort.class);
        SysUserService sysUserService =
                mock(SysUserService.class);
        EntityPublishedSnapshotService snapshotService =
                mock(EntityPublishedSnapshotService.class);
        EntityRecordTeamService recordTeamService =
                mock(EntityRecordTeamService.class);
        EntityDataMutationValidator validator =
                mock(EntityDataMutationValidator.class);
        EntityDataMutationPayloadMapper payloadMapper =
                mock(EntityDataMutationPayloadMapper.class);
        EntityDefinition definition = new EntityDefinition();
        definition.setId("node-id");
        definition.setEntityCode("node");
        when(definitionMapper.findByEntityCode("node"))
                .thenReturn(Optional.of(definition));
        when(tableService.getTableName("node"))
                .thenReturn("wf_node");
        when(dynamicMapper.selectByIdForUpdate(
                "wf_node", "record-1"))
                .thenReturn(java.util.Map.of(
                        "id", "record-1"));
        EntityDataMutationService service =
                new EntityDataMutationService(
                        dynamicMapper,
                        definitionMapper,
                        statusMapper,
                        tableService,
                        codeGeneratorService,
                        recordMapper,
                        relationRuntimeService,
                        multiValueRuntimeService,
                        processRuntimePort,
                        sysUserService,
                        snapshotService,
                        recordTeamService,
                        validator,
                        payloadMapper);
        return new Fixture(
                service,
                dynamicMapper,
                definitionMapper,
                tableService,
                relationRuntimeService,
                multiValueRuntimeService,
                definition);
    }

    private record Fixture(
            EntityDataMutationService service,
            EntityDataDynamicMapper dynamicMapper,
            EntityDefinitionMapper definitionMapper,
            DynamicTableService tableService,
            EntityRelationRuntimeService relationRuntimeService,
            EntityMultiValueRuntimeService multiValueRuntimeService,
            EntityDefinition definition) {
    }
}
