package com.workflow.entity.version.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.mutation.EntityChangeTargetApplyCommand;
import com.workflow.contracts.entity.mutation.EntityChangeTargetFreezeCommand;
import com.workflow.contracts.entity.mutation.EntityChangeTargetResolver;
import com.workflow.contracts.entity.mutation.EntityMutationBatchCommand;
import com.workflow.contracts.entity.mutation.EntityMutationBatchResult;
import com.workflow.contracts.entity.mutation.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.EntityMutationPort;
import com.workflow.contracts.entity.mutation.EntityMutationSourceType;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityRelationMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityChangeTargetInstanceMapper;
import com.workflow.entity.version.infrastructure.persistence.record.EntityChangeTargetInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityChangeTargetServiceTest {

    @Mock
    private EntityVersionConfigurationService configurationService;
    @Mock
    private EntityDataDynamicService queryService;
    @Mock
    private EntityRecordVersionService versionService;
    @Mock
    private EntityChangeTargetInstanceMapper instanceMapper;
    @Mock
    private EntityChangeTargetInstanceStatusService statusService;
    @Mock
    private EntityDefinitionMapper definitionMapper;
    @Mock
    private EntityRelationMapper relationMapper;
    @Mock
    private EntityMutationPort mutationPort;

    private ObjectMapper objectMapper;
    private EntityChangeTargetService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new EntityChangeTargetService(
                configurationService,
                queryService,
                versionService,
                instanceMapper,
                statusService,
                definitionMapper,
                relationMapper,
                List.<EntityChangeTargetResolver>of(),
                mutationPort,
                objectMapper);
    }

    @Test
    void freezeCapturesResolvedTargetBaselineAndMappedPatch()
            throws Exception {
        EntityVersionConfiguration configuration =
                configuration();
        when(configurationService
                .findPublishedTargetConfigurations(
                        "asset_change")).thenReturn(
                                List.of(configuration));
        when(queryService.findById(
                "asset_change",
                "change-1")).thenReturn(
                        record(
                                "change-1",
                                "asset_change",
                                Map.of(
                                        "targetAssetId",
                                        "asset-1",
                                        "proposedName",
                                        "新名称")));
        when(queryService.findById(
                "asset",
                "asset-1")).thenReturn(
                        record(
                                "asset-1",
                                "asset",
                                Map.of("name", "原名称")));
        when(versionService.currentVersionNo(
                "asset",
                "asset-1")).thenReturn(3);

        var frozen = service.freeze(
                new EntityChangeTargetFreezeCommand(
                        "asset_change",
                        "change-1",
                        "definition-1",
                        "instance-1",
                        "user-1",
                        Map.of()));

        assertEquals(1, frozen.size());
        assertEquals("asset-1",
                frozen.get(0).targetRecordId());
        assertEquals(3,
                frozen.get(0).baselineVersionNo());
        ArgumentCaptor<EntityChangeTargetInstance> captor =
                ArgumentCaptor.forClass(
                        EntityChangeTargetInstance.class);
        verify(instanceMapper).insert(captor.capture());
        EntityChangeTargetInstance instance =
                captor.getValue();
        assertEquals("FROZEN", instance.getStatus());
        assertEquals(3, instance.getBaselineVersionNo());
        @SuppressWarnings("unchecked")
        Map<String, Object> document =
                objectMapper.readValue(
                        instance.getTargetDocument(),
                        Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> patch =
                (Map<String, Object>) document.get("patch");
        assertEquals(
                "新名称",
                ((Map<?, ?>) patch.get("data"))
                        .get("name"));
        assertEquals("release-1",
                document.get("configReleaseId"));
    }

    @Test
    void applyUsesOneAtomicBatchForAllFrozenTargets()
            throws Exception {
        EntityChangeTargetInstance first =
                frozenInstance(
                        "target-instance-1",
                        "asset-1",
                        1,
                        "名称一");
        EntityChangeTargetInstance second =
                frozenInstance(
                        "target-instance-2",
                        "asset-2",
                        4,
                        "名称二");
        when(instanceMapper.findTargets(
                "asset_change",
                "change-1",
                "instance-1")).thenReturn(
                        List.of(first, second));
        when(mutationPort.executeBatch(any()))
                .thenReturn(
                        new EntityMutationBatchResult(
                                "apply-1",
                                List.of()));

        service.apply(applyCommand());

        ArgumentCaptor<EntityMutationBatchCommand> captor =
                ArgumentCaptor.forClass(
                        EntityMutationBatchCommand.class);
        verify(mutationPort).executeBatch(captor.capture());
        EntityMutationBatchCommand batch =
                captor.getValue();
        assertTrue(batch.atomic());
        assertEquals(2, batch.commands().size());
        assertEquals(
                List.of("asset-1", "asset-2"),
                batch.commands().stream()
                        .map(command -> command.recordId())
                        .toList());
        assertTrue(batch.commands().stream()
                .allMatch(command ->
                        command.operationType()
                                == EntityMutationOperationType.APPLY_CHANGE));
        assertEquals(
                List.of(1, 4),
                batch.commands().stream()
                        .map(command -> command.context()
                                .extraParams()
                                .get("baselineVersionNo"))
                        .toList());
        verify(statusService).update(
                List.of(
                        "target-instance-1",
                        "target-instance-2"),
                "APPLIED");
    }

    @Test
    void baselineConflictMarksAllTargetsAsConflict() {
        EntityChangeTargetInstance instance =
                frozenInstance(
                        "target-instance-1",
                        "asset-1",
                        1,
                        "新名称");
        when(instanceMapper.findTargets(
                "asset_change",
                "change-1",
                "instance-1")).thenReturn(
                        List.of(instance));
        when(mutationPort.executeBatch(any()))
                .thenThrow(
                        new BusinessConflictException(
                                "ENTITY_VERSION_BASELINE_CONFLICT",
                                "基线冲突"));

        assertThrows(
                BusinessConflictException.class,
                () -> service.apply(applyCommand()));

        verify(statusService).update(
                List.of("target-instance-1"),
                "CONFLICT");
    }

    private EntityVersionConfiguration configuration() {
        EntityVersionConfiguration.TargetBinding binding =
                new EntityVersionConfiguration.TargetBinding();
        binding.setBindingCode("asset-change-target");
        binding.setBindingName("资产变更目标");
        binding.setSourceEntityCode("asset_change");
        binding.setTargetEntityCode("asset");
        binding.setResolverType("FIELD");
        binding.setResolverCode("targetAssetId");
        binding.setFieldMapping(
                Map.of(
                        "proposedName",
                        "data.name"));
        binding.setApplyStrategy("MERGE");
        binding.setEnabled(true);

        EntityVersionConfiguration configuration =
                new EntityVersionConfiguration();
        configuration.setEntityCode("asset");
        configuration.setActiveReleaseId("release-1");
        configuration.setActiveReleaseVersion(2);
        configuration.setTargetBindings(
                List.of(binding));
        return configuration;
    }

    private EntityChangeTargetInstance frozenInstance(
            String id,
            String targetRecordId,
            int baselineVersionNo,
            String name) {
        EntityChangeTargetInstance value =
                new EntityChangeTargetInstance();
        value.setId(id);
        value.setBindingCode("asset-change-target");
        value.setSourceEntityCode("asset_change");
        value.setSourceRecordId("change-1");
        value.setProcessInstanceId("instance-1");
        value.setTargetEntityCode("asset");
        value.setTargetRecordId(targetRecordId);
        value.setBaselineVersionNo(
                baselineVersionNo);
        value.setTargetDocument(
                write(Map.of(
                        "patch",
                        Map.of(
                                "data",
                                Map.of("name", name)),
                        "sourceEffectivePatch",
                        Map.of(),
                        "sourceFailedPatch",
                        Map.of())));
        value.setStatus("FROZEN");
        return value;
    }

    private EntityChangeTargetApplyCommand applyCommand() {
        return new EntityChangeTargetApplyCommand(
                "asset_change",
                "change-1",
                "definition-1",
                "instance-1",
                "task-1",
                "user-1",
                "张三",
                EntityMutationSourceType.PROCESS_RUNTIME,
                "CHANGE_EFFECTIVE",
                "变更审批生效",
                "apply-1",
                Map.of());
    }

    private EntityDataDTO record(
            String id,
            String entityCode,
            Map<String, Object> data) {
        EntityDataDTO value = new EntityDataDTO();
        value.setId(id);
        value.setEntityCode(entityCode);
        value.setData(data);
        return value;
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
