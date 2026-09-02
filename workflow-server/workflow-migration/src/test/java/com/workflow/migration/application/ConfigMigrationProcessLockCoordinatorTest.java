package com.workflow.migration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigEnvironmentMappingMapper;
import com.workflow.migration.infrastructure.persistence.record.ConfigEnvironmentMapping;
import com.workflow.migration.infrastructure.persistence.record.ConfigImportItem;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigMigrationProcessLockCoordinatorTest {

    private EntityDefinitionMapper entityMapper;
    private ProcessDefinitionConfigMapper processMapper;
    private ConfigEnvironmentMappingMapper environmentMappingMapper;
    private ConfigMigrationProcessLockCoordinator coordinator;

    @BeforeEach
    void setUp() {
        entityMapper = mock(EntityDefinitionMapper.class);
        processMapper = mock(ProcessDefinitionConfigMapper.class);
        environmentMappingMapper = mock(ConfigEnvironmentMappingMapper.class);
        coordinator = new ConfigMigrationProcessLockCoordinator(
                entityMapper,
                processMapper,
                environmentMappingMapper,
                new ObjectMapper());
    }

    @Test
    void locksImportedCurrentAndMappedTargetProcessesInCanonicalStableOrder() {
        ProcessDefinitionConfig importedProcess = process("10", "imported-process");
        ProcessDefinitionConfig targetProcess = process("003", "target-process");
        when(processMapper.findByProcessKey("imported-process"))
                .thenReturn(Optional.of(importedProcess));
        when(processMapper.findByProcessKey("new-process"))
                .thenReturn(Optional.empty());
        when(processMapper.findByProcessKey("alias-process"))
                .thenReturn(Optional.of(process("0002", "alias-process")));
        when(processMapper.findByProcessKey("target-process"))
                .thenReturn(Optional.of(targetProcess));

        EntityDefinition currentEntity = new EntityDefinition();
        currentEntity.setId("20");
        currentEntity.setEntityCode("request");
        currentEntity.setProcessDefinitionId("02");
        when(entityMapper.findByEntityCode("request"))
                .thenReturn(Optional.of(currentEntity));
        when(entityMapper.findByIdForUpdate("20"))
                .thenReturn(Optional.of(currentEntity));
        ConfigEnvironmentMapping mapping = new ConfigEnvironmentMapping();
        mapping.setTargetKey("target-process");
        when(environmentMappingMapper.selectOne(any())).thenReturn(mapping);

        coordinator.lockAffectedExistingProcesses(List.of(
                item(ConfigMigrationAssetService.ENTITY, "request", """
                        {"definition":{"entityCode":"request","processKey":"source-process"}}
                        """),
                item(ConfigMigrationAssetService.PROCESS, "imported-process", """
                        {"definition":{"processKey":"imported-process"}}
                        """),
                item(ConfigMigrationAssetService.PROCESS, "alias-process", """
                        {"definition":{"processKey":"alias-process"}}
                        """),
                item(ConfigMigrationAssetService.PROCESS, "new-process", """
                        {"definition":{"processKey":"new-process"}}
                        """)));

        InOrder lockOrder = inOrder(processMapper, entityMapper);
        lockOrder.verify(processMapper).selectAnyByIdForBindingUpdate("10");
        lockOrder.verify(processMapper).selectAnyByIdForBindingUpdate("2");
        lockOrder.verify(processMapper).selectAnyByIdForBindingUpdate("3");
        lockOrder.verify(entityMapper).findByIdForUpdate("20");
        verify(processMapper, times(3))
                .selectAnyByIdForBindingUpdate(any());
    }

    @Test
    void doesNotLockRowsForNewProcessesOrInvalidHistoricalBindings() {
        EntityDefinition currentEntity = new EntityDefinition();
        currentEntity.setId("1");
        currentEntity.setEntityCode("request");
        currentEntity.setProcessDefinitionId("legacy-invalid-id");
        when(entityMapper.findByEntityCode("request"))
                .thenReturn(Optional.of(currentEntity));
        when(entityMapper.findByIdForUpdate("1"))
                .thenReturn(Optional.of(currentEntity));
        when(processMapper.findByProcessKey("new-process"))
                .thenReturn(Optional.empty());

        coordinator.lockAffectedExistingProcesses(List.of(
                item(ConfigMigrationAssetService.ENTITY, "request", """
                        {"definition":{"entityCode":"request","processKey":"new-process"}}
                        """)));

        verify(processMapper, never()).selectAnyByIdForBindingUpdate(any());
        verify(entityMapper).findByIdForUpdate("1");
    }

    @Test
    void rejectsWhenBindingChangesBetweenSnapshotAndEntityLock() {
        EntityDefinition snapshot = entity("7", "request", "5");
        EntityDefinition changed = entity("7", "request", "6");
        when(entityMapper.findByEntityCode("request"))
                .thenReturn(Optional.of(snapshot));
        when(entityMapper.findByIdForUpdate("7"))
                .thenReturn(Optional.of(changed));

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> coordinator.lockAffectedExistingProcesses(List.of(
                        item(ConfigMigrationAssetService.ENTITY, "request", """
                                {"definition":{"entityCode":"request"}}
                                """))));

        assertEquals("ENTITY_WORKFLOW_BINDING_CHANGED", exception.getErrorCode());
        InOrder lockOrder = inOrder(processMapper, entityMapper);
        lockOrder.verify(processMapper).selectAnyByIdForBindingUpdate("5");
        lockOrder.verify(entityMapper).findByIdForUpdate("7");
    }

    @Test
    void locksEveryProcessBeforeEntitiesAndSortsEntityIds() {
        EntityDefinition entityThree = entity("03", "request", "20");
        EntityDefinition entityTwo = entity("2", "expense", "10");
        when(entityMapper.findByEntityCode("request"))
                .thenReturn(Optional.of(entityThree));
        when(entityMapper.findByEntityCode("expense"))
                .thenReturn(Optional.of(entityTwo));
        when(entityMapper.findByIdForUpdate("2"))
                .thenReturn(Optional.of(entityTwo));
        when(entityMapper.findByIdForUpdate("3"))
                .thenReturn(Optional.of(entityThree));

        coordinator.lockAffectedExistingProcesses(List.of(
                item(ConfigMigrationAssetService.ENTITY, "request", """
                        {"definition":{"entityCode":"request"}}
                        """),
                item(ConfigMigrationAssetService.ENTITY, "expense", """
                        {"definition":{"entityCode":"expense"}}
                        """)));

        InOrder lockOrder = inOrder(processMapper, entityMapper);
        lockOrder.verify(processMapper).selectAnyByIdForBindingUpdate("10");
        lockOrder.verify(processMapper).selectAnyByIdForBindingUpdate("20");
        lockOrder.verify(entityMapper).findByIdForUpdate("2");
        lockOrder.verify(entityMapper).findByIdForUpdate("3");
    }

    @Test
    void rollbackLockPlanUsesRestoredTargetInsteadOfImportedTarget() {
        EntityDefinition current = entity("8", "request", "1");
        when(entityMapper.findByEntityCode("request"))
                .thenReturn(Optional.of(current));
        when(entityMapper.findByIdForUpdate("8"))
                .thenReturn(Optional.of(current));
        when(processMapper.findByProcessKey("rollback-process"))
                .thenReturn(Optional.of(process("2", "rollback-process")));

        ConfigImportItem imported = item(
                ConfigMigrationAssetService.ENTITY,
                "request",
                """
                        {"definition":{"entityCode":"request","processKey":"imported-process"}}
                        """);
        ConfigImportItem rollback = item(
                ConfigMigrationAssetService.ENTITY,
                "request",
                """
                        {"definition":{"entityCode":"request","processKey":"rollback-process"}}
                        """);
        List<ConfigImportItem> lockItems =
                ConfigMigrationImportApplyService.rollbackLockItems(List.of(
                        new ConfigMigrationImportApplyService.RollbackItemPlan(
                                imported, rollback)));

        coordinator.lockAffectedExistingProcesses(lockItems);

        verify(processMapper).findByProcessKey("rollback-process");
        verify(processMapper, never()).findByProcessKey("imported-process");
        InOrder lockOrder = inOrder(processMapper, entityMapper);
        lockOrder.verify(processMapper).selectAnyByIdForBindingUpdate("1");
        lockOrder.verify(processMapper).selectAnyByIdForBindingUpdate("2");
        lockOrder.verify(entityMapper).findByIdForUpdate("8");
    }

    @Test
    void canonicalizesNumericAliasesAndRejectsNonPositiveOrNonNumericIds() {
        assertEquals("2", ConfigMigrationProcessLockCoordinator.canonicalProcessId(" 002 "));
        assertNull(ConfigMigrationProcessLockCoordinator.canonicalProcessId("0"));
        assertNull(ConfigMigrationProcessLockCoordinator.canonicalProcessId("-1"));
        assertNull(ConfigMigrationProcessLockCoordinator.canonicalProcessId("not-an-id"));
    }

    private ConfigImportItem item(String assetType, String businessKey, String snapshot) {
        ConfigImportItem item = new ConfigImportItem();
        item.setAssetType(assetType);
        item.setBusinessKey(businessKey);
        item.setSnapshotJson(snapshot);
        return item;
    }

    private ProcessDefinitionConfig process(String id, String processKey) {
        ProcessDefinitionConfig process = new ProcessDefinitionConfig();
        process.setId(id);
        process.setProcessKey(processKey);
        return process;
    }

    private EntityDefinition entity(
            String id,
            String entityCode,
            String processDefinitionId) {
        EntityDefinition entity = new EntityDefinition();
        entity.setId(id);
        entity.setEntityCode(entityCode);
        entity.setProcessDefinitionId(processDefinitionId);
        return entity;
    }
}
