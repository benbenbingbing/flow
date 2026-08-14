package com.workflow.entity.version.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityChangeTargetBindingMapper;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityVersionConfigMapper;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityVersionConfigReleaseMapper;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityVersionScenarioMapper;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityVersionStepMapper;
import com.workflow.entity.version.infrastructure.persistence.record.EntityChangeTargetBinding;
import com.workflow.entity.version.infrastructure.persistence.record.EntityVersionConfig;
import com.workflow.entity.version.infrastructure.persistence.record.EntityVersionConfigRelease;
import com.workflow.entity.version.infrastructure.persistence.record.EntityVersionScenario;
import com.workflow.entity.version.infrastructure.persistence.record.EntityVersionStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityVersionConfigurationServiceV2Test {

    @Mock
    private EntityVersionConfigMapper configMapper;
    @Mock
    private EntityVersionScenarioMapper scenarioMapper;
    @Mock
    private EntityVersionStepMapper stepMapper;
    @Mock
    private EntityChangeTargetBindingMapper targetBindingMapper;
    @Mock
    private EntityVersionConfigReleaseMapper releaseMapper;
    @Mock
    private EntityDefinitionMapper definitionMapper;
    @Mock
    private EntityVersionConfigurationValidator validator;
    @Mock
    private EntityVersionScopeFreezer scopeFreezer;

    private ObjectMapper objectMapper;
    private EntityVersionConfigurationService service;
    private EntityVersionConfig config;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        service = new EntityVersionConfigurationService(
                configMapper,
                scenarioMapper,
                stepMapper,
                targetBindingMapper,
                releaseMapper,
                definitionMapper,
                objectMapper,
                validator,
                scopeFreezer);
        EntityDefinition definition = new EntityDefinition();
        definition.setId("entity-1");
        definition.setEntityCode("asset");
        definition.setEntityName("资产");
        lenient().when(definitionMapper.findByEntityCode("asset"))
                .thenReturn(Optional.of(definition));
        lenient().when(scopeFreezer.enrichDraftOptions(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        config = new EntityVersionConfig();
        config.setId("config-1");
        config.setEntityId("entity-1");
        config.setEntityCode("asset");
        config.setEnabled(true);
        config.setRevision(7);
        config.setStatus("DRAFT");
        config.setDeleted(0);
        config.setContractVersion(2);
        config.setDraftDocument(objectMapper.writeValueAsString(v2Draft()));
        when(configMapper.findByEntityCode("asset"))
                .thenReturn(config);
    }

    @Test
    void v2SaveDoesNotDeleteOrRewriteLegacyMutationTables() {
        when(configMapper.updateDraftIfRevision(
                eq("config-1"), eq(7), any(), eq(2),
                anyString(), anyString(), any()))
                .thenReturn(1);

        service.saveDraft("asset", v2Draft(), 7);

        verify(scenarioMapper, never()).deleteByConfigId(anyString());
        verify(stepMapper, never()).deleteByConfigId(anyString());
        verify(targetBindingMapper, never()).deleteByConfigId(anyString());
        verify(scenarioMapper, never()).insert(any(EntityVersionScenario.class));
        verify(stepMapper, never()).insert(any(EntityVersionStep.class));
        verify(targetBindingMapper, never()).insert(
                any(EntityChangeTargetBinding.class));
    }

    @Test
    void concurrentFirstSaveReturnsRevisionConflictInsteadOfRawDuplicate() {
        EntityVersionConfig winner = new EntityVersionConfig();
        winner.setRevision(1);
        when(configMapper.findByEntityCode("asset"))
                .thenReturn(null, winner);
        when(configMapper.insert(any(EntityVersionConfig.class)))
                .thenThrow(new DuplicateKeyException("concurrent insert"));

        com.workflow.core.error.BusinessConflictException exception =
                assertThrows(
                        com.workflow.core.error.BusinessConflictException.class,
                        () -> service.saveDraft("asset", v2Draft(), 0));

        assertEquals("ENTITY_VERSION_CONFIG_REVISION_CONFLICT",
                exception.getErrorCode());
        assertTrue(exception.getMessage().contains("currentRevision=1"));
    }

    @Test
    void v2PublishUsesAtomicRevisionAndCopiesLegacyBehaviorServerSide()
            throws Exception {
        config.setActiveReleaseId("release-old");
        EntityVersionConfiguration legacy = new EntityVersionConfiguration();
        legacy.setSchemaVersion(1);
        EntityVersionConfiguration.Scenario scenario =
                new EntityVersionConfiguration.Scenario();
        scenario.setScenarioCode("LEGACY_CHANGE");
        scenario.setScenarioName("旧变更策略");
        legacy.setScenarios(List.of(scenario));
        EntityVersionConfigRelease oldRelease = new EntityVersionConfigRelease();
        oldRelease.setId("release-old");
        oldRelease.setVersion(1);
        oldRelease.setConfigDocument(objectMapper.writeValueAsString(legacy));
        AtomicReference<EntityVersionConfigRelease> inserted =
                new AtomicReference<>();
        when(releaseMapper.selectById(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            return "release-old".equals(id) ? oldRelease : inserted.get();
        });
        when(releaseMapper.findMaxVersion("config-1")).thenReturn(1);
        when(scopeFreezer.freeze(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(configMapper.activateReleaseIfRevision(
                eq("config-1"), eq(7), anyString(), eq(2),
                eq("MIGRATED"), any())).thenAnswer(invocation -> {
                    config.setActiveReleaseId(invocation.getArgument(2));
                    config.setRevision(8);
                    return 1;
                });
        when(releaseMapper.insert(any(EntityVersionConfigRelease.class)))
                .thenAnswer(invocation -> {
            inserted.set(invocation.getArgument(0));
            return 1;
                });

        service.publish("asset", 7);

        ArgumentCaptor<EntityVersionConfigRelease> captor =
                ArgumentCaptor.forClass(EntityVersionConfigRelease.class);
        verify(releaseMapper).insert(captor.capture());
        EntityVersionConfiguration released = objectMapper.readValue(
                captor.getValue().getConfigDocument(),
                EntityVersionConfiguration.class);
        assertEquals(2, released.getSchemaVersion());
        assertEquals("ROOT_CHANGE", released.getTriggers().get(0)
                .getTriggerCode());
        assertEquals("LEGACY_CHANGE", released.getScenarios().get(0)
                .getScenarioCode());
        assertEquals(8, config.getRevision());
        verify(configMapper).activateReleaseIfRevision(
                eq("config-1"), eq(7), eq(captor.getValue().getId()),
                eq(2), eq("MIGRATED"), any());
    }

    @Test
    void rejectsEntityReleaseThatRemovesActiveScopeRelation()
            throws Exception {
        EntityVersionConfiguration active = v2Draft();
        active.getSnapshotScope().getRelations().get(0)
                .setRelationCode("asset_lines");
        EntityVersionConfigRelease release = new EntityVersionConfigRelease();
        release.setId("release-active");
        release.setVersion(2);
        release.setContractVersion(2);
        release.setConfigDocument(objectMapper.writeValueAsString(active));
        config.setActiveReleaseId("release-active");
        when(releaseMapper.selectById("release-active")).thenReturn(release);

        assertTrue(org.junit.jupiter.api.Assertions.assertThrows(
                com.workflow.core.error.BusinessConflictException.class,
                () -> service.requireRelationScopeCompatible(
                        "asset", List.of())).getMessage().contains("asset_lines"));
    }

    @Test
    void rejectsPublishingChangedRelationSelector() throws Exception {
        EntityVersionConfiguration active = v2Draft();
        EntityVersionConfiguration.RelationScope frozen = active
                .getSnapshotScope().getRelations().get(0);
        frozen.setChildEntityCode("asset_line");
        frozen.setChildRefFieldCode("assetId");
        frozen.setRelationType("ONE_TO_MANY");
        frozen.setDataKey("lines");
        EntityVersionConfigRelease release = new EntityVersionConfigRelease();
        release.setId("release-active");
        release.setVersion(2);
        release.setContractVersion(2);
        release.setConfigDocument(objectMapper.writeValueAsString(active));
        config.setActiveReleaseId("release-active");
        when(releaseMapper.selectById("release-active")).thenReturn(release);
        EntityRelation changed = new EntityRelation();
        changed.setRelationCode("asset_lines");
        changed.setChildEntityCode("asset_line");
        changed.setChildRefFieldCode("changedAssetId");
        changed.setRelationType(EntityRelation.RelationType.ONE_TO_MANY);
        changed.setOwnershipType(EntityRelation.OwnershipType.COMPOSITION);
        changed.setDataKey("lines");
        changed.setEnabled(true);

        com.workflow.core.error.BusinessConflictException exception =
                assertThrows(
                        com.workflow.core.error.BusinessConflictException.class,
                        () -> service.requireRelationScopeDefinitionsCompatible(
                                "asset", List.of(changed)));

        assertEquals("ENTITY_VERSION_SCOPE_RELATION_INCOMPATIBLE",
                exception.getErrorCode());
    }

    @Test
    void legacyReleaseWithoutSchemaPropertyUsesReleaseContractV1()
            throws Exception {
        config.setActiveReleaseId("legacy-release");
        EntityVersionConfigRelease release = new EntityVersionConfigRelease();
        release.setId("legacy-release");
        release.setVersion(3);
        release.setContractVersion(1);
        release.setConfigDocument("{\"enabled\":true,\"scenarios\":[]}");
        when(releaseMapper.selectById("legacy-release")).thenReturn(release);

        EntityVersionConfiguration published = service.getPublished("asset")
                .orElseThrow();

        assertEquals(1, published.getSchemaVersion());
        assertEquals(3, published.getActiveReleaseVersion());
    }

    @Test
    void releaseHistoryUsesServerSidePagination() {
        EntityVersionConfigRelease release = new EntityVersionConfigRelease();
        release.setId("release-1");
        release.setVersion(1);
        release.setContractVersion(2);
        release.setConfigDocument(
                "{\"snapshotScope\":{\"relations\":[{\"enabled\":true}]}}");
        when(releaseMapper.countByConfigId("config-1")).thenReturn(3L);
        when(releaseMapper.findPageByConfigId("config-1", 2, 2))
                .thenReturn(List.of(release));

        var page = service.releases("asset", 2, 2);

        assertEquals(3, page.getTotal());
        assertEquals(2, page.getPageNum());
        assertEquals(2, page.getPageSize());
        assertEquals(List.of(1), page.getRecords().stream()
                .map(item -> item.version())
                .toList());
        assertEquals(1, page.getRecords().get(0).relationCount());
    }

    private EntityVersionConfiguration v2Draft() {
        EntityVersionConfiguration value = new EntityVersionConfiguration();
        value.setSchemaVersion(2);
        value.setEnabled(true);
        value.setRevision(7);
        EntityVersionConfiguration.CaptureTrigger trigger =
                new EntityVersionConfiguration.CaptureTrigger();
        trigger.setTriggerCode("ROOT_CHANGE");
        trigger.setTriggerName("根实体变化");
        trigger.setTriggerType("ROOT_MUTATION");
        value.setTriggers(List.of(trigger));
        EntityVersionConfiguration.RelationScope relation =
                new EntityVersionConfiguration.RelationScope();
        relation.setNodeCode("REL_LINES");
        relation.setRelationCode("asset_lines");
        relation.setEnabled(true);
        value.getSnapshotScope().setRelations(List.of(relation));
        return value;
    }
}
