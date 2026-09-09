package com.workflow.entity.version.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityRecordVersionMapper;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityVersionConfigMapper;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityVersionRolloutBridgeMapper;
import com.workflow.entity.version.infrastructure.persistence.record.EntityVersionConfig;
import com.workflow.entity.version.infrastructure.persistence.record.EntityVersionRolloutState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityVersionConfigurationServiceV2Test {

    @Mock
    private EntityVersionConfigMapper configMapper;
    @Mock
    private EntityVersionRolloutBridgeMapper rolloutBridgeMapper;
    @Mock
    private EntityRecordVersionMapper recordVersionMapper;
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
                rolloutBridgeMapper,
                recordVersionMapper,
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
        lenient().when(scopeFreezer.enrichManagementOptions(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(scopeFreezer.freeze(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(recordVersionMapper.existsByEntityCode("asset"))
                .thenReturn(false);
        lenient().when(rolloutBridgeMapper.syncLegacyDraft(
                        anyString(), any(), any(), anyString(), any()))
                .thenReturn(1);
        lenient().when(rolloutBridgeMapper.findNextReleaseVersion(anyString()))
                .thenReturn(1);
        lenient().when(rolloutBridgeMapper.insertCompatibilityRelease(
                        anyString(), anyString(), any(), any(), anyString(),
                        any(), any(), any()))
                .thenReturn(1);
        lenient().when(rolloutBridgeMapper.activateCompatibilityRelease(
                        anyString(), any(), anyString(), any()))
                .thenReturn(1);

        config = currentConfig(v2Configuration("ROOT_MUTATION", true));
        lenient().when(configMapper.findByEntityCode("asset"))
                .thenReturn(config);
    }

    @Test
    void saveFreezesThenAtomicallyReplacesCurrentDocument()
            throws Exception {
        when(configMapper.updateCurrentIfRevision(
                eq("config-1"), eq(7), eq(true),
                anyString(), any()))
                .thenReturn(1);

        EntityVersionConfiguration saved = service.save(
                "asset", v2Configuration("ROOT_MUTATION", true), 7);

        ArgumentCaptor<String> document =
                ArgumentCaptor.forClass(String.class);
        verify(configMapper).updateCurrentIfRevision(
                eq("config-1"), eq(7), eq(true),
                document.capture(), any());
        verify(validator, org.mockito.Mockito.times(2)).validate(any());
        verify(scopeFreezer).freeze(any());
        InOrder bridgeOrder = inOrder(rolloutBridgeMapper);
        bridgeOrder.verify(rolloutBridgeMapper).syncLegacyDraft(
                eq("config-1"), eq(8), eq(2),
                eq(document.getValue()), any());
        ArgumentCaptor<String> releaseId =
                ArgumentCaptor.forClass(String.class);
        bridgeOrder.verify(rolloutBridgeMapper).insertCompatibilityRelease(
                releaseId.capture(),
                eq("config-1"),
                eq(1),
                eq(2),
                eq(document.getValue()),
                any(),
                any(),
                any());
        bridgeOrder.verify(rolloutBridgeMapper).activateCompatibilityRelease(
                eq("config-1"), eq(8), eq(releaseId.getValue()), any());
        EntityVersionConfiguration persisted = objectMapper.readValue(
                document.getValue(), EntityVersionConfiguration.class);
        assertEquals("ROOT_CHANGE", persisted.getTriggers().get(0)
                .getTriggerCode());
        assertEquals(8, persisted.getRevision());
        assertEquals(8, saved.getRevision());
        assertTrue(persisted.getScenarios().isEmpty());
        assertTrue(persisted.getSteps().isEmpty());
        assertTrue(persisted.getTargetBindings().isEmpty());
        assertTrue(persisted.getRelationOptions().isEmpty());
        assertTrue(persisted.getFieldOptions().isEmpty());
    }

    @Test
    void everySaveCreatesAndActivatesANewImmutableCompatibilityRelease() {
        when(configMapper.updateCurrentIfRevision(
                eq("config-1"), eq(7), eq(true),
                anyString(), any()))
                .thenReturn(1);
        when(rolloutBridgeMapper.findNextReleaseVersion("config-1"))
                .thenReturn(4);
        when(rolloutBridgeMapper.insertCompatibilityRelease(
                anyString(), eq("config-1"), eq(4), eq(2),
                anyString(), any(), any(), any()))
                .thenReturn(1);
        when(rolloutBridgeMapper.activateCompatibilityRelease(
                eq("config-1"), eq(8), anyString(), any()))
                .thenReturn(1);

        service.save(
                "asset", v2Configuration("ROOT_MUTATION", true), 7);

        ArgumentCaptor<String> releaseId =
                ArgumentCaptor.forClass(String.class);
        verify(rolloutBridgeMapper).insertCompatibilityRelease(
                releaseId.capture(), eq("config-1"), eq(4), eq(2),
                anyString(), any(), any(), any());
        verify(rolloutBridgeMapper).activateCompatibilityRelease(
                eq("config-1"), eq(8), eq(releaseId.getValue()), any());
    }

    @Test
    void bridgeFailureAbortsTheSaveTransaction() {
        when(configMapper.updateCurrentIfRevision(
                eq("config-1"), eq(7), eq(true),
                anyString(), any()))
                .thenReturn(1);
        when(rolloutBridgeMapper.syncLegacyDraft(
                eq("config-1"), eq(8), eq(2), anyString(), any()))
                .thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> service.save(
                        "asset",
                        v2Configuration("ROOT_MUTATION", true),
                        7));

        verify(rolloutBridgeMapper, never())
                .findNextReleaseVersion(anyString());
    }

    @Test
    void firstSaveRequiresRevisionZeroAndMapsDuplicateToConflict() {
        EntityVersionConfig winner = new EntityVersionConfig();
        winner.setRevision(1);
        when(configMapper.findByEntityCode("asset"))
                .thenReturn(null, winner);
        when(configMapper.insert(any(EntityVersionConfig.class)))
                .thenThrow(new DuplicateKeyException("concurrent insert"));

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> service.save(
                        "asset",
                        v2Configuration("ROOT_MUTATION", true),
                        0));

        assertEquals("ENTITY_VERSION_CONFIG_REVISION_CONFLICT",
                exception.getErrorCode());
        assertTrue(exception.getMessage().contains("currentRevision=1"));
    }

    @Test
    void firstSaveRejectsMissingExpectedRevision() {
        when(configMapper.findByEntityCode("asset")).thenReturn(null);

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> service.save(
                        "asset",
                        v2Configuration("ROOT_MUTATION", true),
                        null));

        assertEquals("ENTITY_VERSION_CONFIG_REVISION_CONFLICT",
                exception.getErrorCode());
        verify(configMapper, never()).insert(any(EntityVersionConfig.class));
    }

    @Test
    void staleRevisionCannotReplaceCurrentDocument() {
        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> service.save(
                        "asset",
                        v2Configuration("ROOT_MUTATION", true),
                        6));

        assertEquals("ENTITY_VERSION_CONFIG_REVISION_CONFLICT",
                exception.getErrorCode());
        verify(configMapper, never()).updateCurrentIfRevision(
                anyString(), any(), any(), anyString(), anyString());
        verifyNoInteractions(rolloutBridgeMapper);
    }

    @Test
    void failedCasReturnsLatestRevisionConflict() {
        EntityVersionConfig latest = currentConfigUnchecked(
                v2Configuration("ROOT_MUTATION", true));
        latest.setRevision(8);
        when(configMapper.findByEntityCode("asset"))
                .thenReturn(config, latest);
        when(configMapper.updateCurrentIfRevision(
                eq("config-1"), eq(7), eq(true),
                anyString(), any()))
                .thenReturn(0);

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> service.save(
                        "asset",
                        v2Configuration("ROOT_MUTATION", true),
                        7));

        assertTrue(exception.getMessage().contains("currentRevision=8"));
    }

    @Test
    void managementReadReturnsDefaultWhenEntityHasNoConfiguration() {
        when(configMapper.findByEntityCode("asset")).thenReturn(null);

        EntityVersionConfiguration result = service.get("asset");

        assertEquals(0, result.getRevision());
        assertEquals(2, result.getSchemaVersion());
        assertFalse(result.getEnabled());
        assertTrue(result.getTriggers().stream().anyMatch(trigger ->
                "MANUAL".equals(trigger.getTriggerType())));
    }

    @Test
    void managementReadTreatsLegacyDraftOnlyRowAsDisabledPlaceholder() {
        config.setConfigDocument(null);
        config.setEnabled(true);

        EntityVersionConfiguration result = service.get("asset");

        assertEquals("config-1", result.getId());
        assertEquals(7, result.getRevision());
        assertFalse(result.getEnabled());
        assertTrue(service.getCurrent("asset").isEmpty());
    }

    @Test
    void legacyDraftOnlyRowCanBeTakenOverAtItsExistingRevision() {
        config.setConfigDocument(null);
        when(configMapper.updateCurrentIfRevision(
                eq("config-1"), eq(7), eq(true),
                anyString(), any()))
                .thenReturn(1);

        EntityVersionConfiguration result = service.save(
                "asset", v2Configuration("ROOT_MUTATION", true), 7);

        assertEquals(8, result.getRevision());
        verify(configMapper).updateCurrentIfRevision(
                eq("config-1"), eq(7), eq(true),
                anyString(), any());
    }

    @Test
    void legacyDraftSaveAdvancesOnlyTheDraftAndKeepsCurrentRuntime()
            throws Exception {
        String currentDocument = config.getConfigDocument();
        EntityVersionConfiguration candidate =
                v2Configuration("MANUAL", true);
        EntityVersionRolloutState before = rolloutState(
                "PUBLISHED", 7, currentDocument, currentDocument);
        EntityVersionRolloutState after = rolloutState(
                "DRAFT", 8,
                objectMapper.writeValueAsString(candidate),
                currentDocument);
        when(rolloutBridgeMapper.findStateByEntityCode("asset"))
                .thenReturn(before, after);
        when(rolloutBridgeMapper.updateLegacyDraftIfRevision(
                eq("config-1"), eq(7), eq(true), eq(2),
                anyString(), any()))
                .thenReturn(1);

        Map<String, Object> saved = service.saveLegacyDraft(
                "asset", candidate, 7);

        assertEquals("DRAFT", saved.get("status"));
        assertEquals(8, saved.get("revision"));
        assertEquals("ROOT_MUTATION", service.getCurrent("asset")
                .orElseThrow().getTriggers().get(0).getTriggerType());
        verify(configMapper, never()).updateCurrentIfRevision(
                anyString(), any(), any(), anyString(), any());
        verify(configMapper, never()).insert(any(EntityVersionConfig.class));
    }

    @Test
    void legacyPublishAdoptsDraftSavedByAnOldPod() throws Exception {
        String currentDocument = config.getConfigDocument();
        EntityVersionConfiguration candidate =
                v2Configuration("MANUAL", true);
        String draftDocument = objectMapper.writeValueAsString(candidate);
        EntityVersionRolloutState pending = rolloutState(
                "DRAFT", 8, draftDocument, currentDocument);
        when(rolloutBridgeMapper.findStateByEntityCode("asset"))
                .thenReturn(pending);
        config.setRevision(8);
        when(configMapper.updateCurrentIfRevision(
                eq("config-1"), eq(8), eq(true),
                anyString(), any()))
                .thenReturn(1);

        EntityVersionConfiguration published =
                service.publishLegacyDraft("asset", 8);

        assertEquals(9, published.getRevision());
        assertEquals("MANUAL", published.getTriggers().get(0)
                .getTriggerType());
        verify(rolloutBridgeMapper).insertCompatibilityRelease(
                anyString(), eq("config-1"), eq(1), eq(2),
                anyString(), any(), any(), any());
    }

    @Test
    void synchronizedLegacyPublishIsIdempotent() {
        String currentDocument = config.getConfigDocument();
        EntityVersionRolloutState synchronizedState = rolloutState(
                "PUBLISHED", 7, currentDocument, currentDocument);
        when(rolloutBridgeMapper.findStateByEntityCode("asset"))
                .thenReturn(synchronizedState);

        EntityVersionConfiguration result =
                service.publishLegacyDraft("asset", 7);

        assertEquals(7, result.getRevision());
        verify(configMapper, never()).updateCurrentIfRevision(
                anyString(), any(), any(), anyString(), any());
        verify(rolloutBridgeMapper, never())
                .insertCompatibilityRelease(
                        anyString(), anyString(), any(), any(), anyString(),
                        any(), any(), any());
    }

    @Test
    void concurrentCurrentSaveWinsOverLegacyPublishCas() throws Exception {
        EntityVersionConfiguration candidate =
                v2Configuration("MANUAL", true);
        when(rolloutBridgeMapper.findStateByEntityCode("asset"))
                .thenReturn(rolloutState(
                        "DRAFT", 8,
                        objectMapper.writeValueAsString(candidate),
                        config.getConfigDocument()));
        config.setRevision(9);

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> service.publishLegacyDraft("asset", 8));

        assertEquals("ENTITY_VERSION_CONFIG_REVISION_CONFLICT",
                exception.getErrorCode());
        assertTrue(exception.getMessage().contains("currentRevision=9"));
        verify(configMapper, never()).updateCurrentIfRevision(
                anyString(), any(), any(), anyString(), any());
    }

    @Test
    void currentReadHydratesOnlyTheCurrentRowEnvelope() {
        EntityVersionConfiguration result = service.getCurrent("asset")
                .orElseThrow();

        assertEquals("config-1", result.getId());
        assertEquals("asset", result.getEntityCode());
        assertEquals(7, result.getRevision());
        assertTrue(result.getEnabled());
        verify(definitionMapper, never()).findByEntityCode(anyString());
    }

    @Test
    void currentReadIgnoresLegacyReleaseEnvelopeProperties() {
        config.setConfigDocument("""
                {"schemaVersion":2,"enabled":true,
                 "status":"PUBLISHED","migrationState":"MIGRATED",
                 "activeReleaseId":"release-7","activeReleaseVersion":7,
                 "triggers":[]}
                """);

        EntityVersionConfiguration result = service.getCurrent("asset")
                .orElseThrow();

        assertEquals("config-1", result.getId());
        assertEquals(7, result.getRevision());
        assertEquals(2, result.getSchemaVersion());
    }

    @Test
    void listSummarizesTheSingleCurrentDocument() {
        EntityDefinition definition = new EntityDefinition();
        definition.setId("entity-1");
        definition.setEntityCode("asset");
        definition.setEntityName("资产");
        when(definitionMapper.findAllWithFields())
                .thenReturn(List.of(definition));

        var summary = service.list(null).get(0);

        assertEquals("asset", summary.entityCode());
        assertEquals(7, summary.revision());
        assertTrue(summary.enabled());
        assertTrue(summary.runtimeEnabled());
        assertEquals(1, summary.triggerCount());
        assertEquals(1, summary.scopeRelationCount());
    }

    @Test
    void listTreatsLegacyDraftOnlyRowAsDisabledAndNotRuntimeEnabled() {
        EntityDefinition definition = new EntityDefinition();
        definition.setId("entity-1");
        definition.setEntityCode("asset");
        definition.setEntityName("资产");
        config.setConfigDocument(null);
        config.setEnabled(true);
        when(definitionMapper.findAllWithFields())
                .thenReturn(List.of(definition));

        var summary = service.list(null).get(0);

        assertFalse(summary.enabled());
        assertFalse(summary.runtimeEnabled());
        assertEquals(7, summary.revision());
    }

    @Test
    void capabilitiesAreAllFalseWithoutCurrentConfigOrHistory() {
        when(configMapper.findByEntityCode("asset")).thenReturn(null);

        var capabilities = service.recordCapabilities("asset");

        assertFalse(capabilities.runtimeEnabled());
        assertFalse(capabilities.manualCaptureEnabled());
        assertFalse(capabilities.historyReadable());
    }

    @Test
    void disabledCurrentConfigKeepsExistingHistoryReadable()
            throws Exception {
        EntityVersionConfiguration disabled =
                v2Configuration("ROOT_MUTATION", true);
        disabled.setEnabled(false);
        setCurrentDocument(disabled);
        config.setEnabled(false);
        when(recordVersionMapper.existsByEntityCode("asset"))
                .thenReturn(true);

        var capabilities = service.recordCapabilities("asset");

        assertFalse(capabilities.runtimeEnabled());
        assertFalse(capabilities.manualCaptureEnabled());
        assertTrue(capabilities.historyReadable());
    }

    @Test
    void enabledV2ManualTriggerAdvertisesManualCapture()
            throws Exception {
        setCurrentDocument(v2Configuration("MANUAL", true));

        var capabilities = service.recordCapabilities("asset");

        assertTrue(capabilities.runtimeEnabled());
        assertTrue(capabilities.manualCaptureEnabled());
    }

    @Test
    void disabledManualTriggerDoesNotAdvertiseManualCapture()
            throws Exception {
        setCurrentDocument(v2Configuration("MANUAL", false));

        var capabilities = service.recordCapabilities("asset");

        assertTrue(capabilities.runtimeEnabled());
        assertFalse(capabilities.manualCaptureEnabled());
    }

    @Test
    void enabledV2WithoutManualTriggerDoesNotAdvertiseManualCapture() {
        var capabilities = service.recordCapabilities("asset");

        assertTrue(capabilities.runtimeEnabled());
        assertFalse(capabilities.manualCaptureEnabled());
    }

    @Test
    void legacyV1DocumentNeverAdvertisesManualCapture()
            throws Exception {
        EntityVersionConfiguration legacy =
                v2Configuration("MANUAL", true);
        legacy.setSchemaVersion(1);
        setCurrentDocument(legacy);

        var capabilities = service.recordCapabilities("asset");

        assertTrue(capabilities.runtimeEnabled());
        assertFalse(capabilities.manualCaptureEnabled());
    }

    @Test
    void rejectsEntityReleaseThatRemovesEnabledCurrentScopeRelation() {
        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> service.requireRelationScopeCompatible(
                        "asset", List.of()));

        assertEquals("ENTITY_VERSION_SCOPE_RELATION_REMOVED",
                exception.getErrorCode());
        assertTrue(exception.getMessage().contains("asset_lines"));
    }

    @Test
    void disabledCurrentConfigDoesNotBlockEntityRelationPublishing()
            throws Exception {
        EntityVersionConfiguration disabled =
                v2Configuration("ROOT_MUTATION", true);
        disabled.setEnabled(false);
        setCurrentDocument(disabled);
        config.setEnabled(false);

        assertDoesNotThrow(() -> service.requireRelationScopeCompatible(
                "asset", List.of()));
    }

    @Test
    void rejectsPublishingChangedRelationSelector() {
        EntityRelation changed = new EntityRelation();
        changed.setRelationCode("asset_lines");
        changed.setChildEntityCode("asset_line");
        changed.setChildRefFieldCode("changedAssetId");
        changed.setRelationType(EntityRelation.RelationType.ONE_TO_MANY);
        changed.setOwnershipType(EntityRelation.OwnershipType.COMPOSITION);
        changed.setDataKey("lines");
        changed.setEnabled(true);

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> service.requireRelationScopeDefinitionsCompatible(
                        "asset", List.of(changed)));

        assertEquals("ENTITY_VERSION_SCOPE_RELATION_INCOMPATIBLE",
                exception.getErrorCode());
    }

    private EntityVersionConfig currentConfig(
            EntityVersionConfiguration document) throws Exception {
        EntityVersionConfig value = new EntityVersionConfig();
        value.setId("config-1");
        value.setEntityId("entity-1");
        value.setEntityCode("asset");
        value.setEnabled(true);
        value.setRevision(7);
        value.setDeleted(0);
        value.setConfigDocument(
                objectMapper.writeValueAsString(document));
        return value;
    }

    private EntityVersionConfig currentConfigUnchecked(
            EntityVersionConfiguration document) {
        try {
            return currentConfig(document);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private EntityVersionRolloutState rolloutState(
            String status,
            int revision,
            String draftDocument,
            String currentDocument) {
        EntityVersionRolloutState value = new EntityVersionRolloutState();
        value.setId("config-1");
        value.setEntityId("entity-1");
        value.setEntityCode("asset");
        value.setEnabled(true);
        value.setRevision(revision);
        value.setStatus(status);
        value.setDraftDocument(draftDocument);
        value.setConfigDocument(currentDocument);
        return value;
    }

    private void setCurrentDocument(
            EntityVersionConfiguration document) throws Exception {
        config.setConfigDocument(
                objectMapper.writeValueAsString(document));
    }

    private EntityVersionConfiguration v2Configuration(
            String triggerType,
            boolean triggerEnabled) {
        EntityVersionConfiguration value =
                new EntityVersionConfiguration();
        value.setSchemaVersion(2);
        value.setEnabled(true);
        value.setRevision(7);
        EntityVersionConfiguration.CaptureTrigger trigger =
                new EntityVersionConfiguration.CaptureTrigger();
        trigger.setTriggerCode("ROOT_CHANGE");
        trigger.setTriggerName("根实体变化");
        trigger.setTriggerType(triggerType);
        trigger.setEnabled(triggerEnabled);
        value.setTriggers(List.of(trigger));
        EntityVersionConfiguration.RelationScope relation =
                new EntityVersionConfiguration.RelationScope();
        relation.setNodeCode("REL_LINES");
        relation.setRelationCode("asset_lines");
        relation.setParentEntityCode("asset");
        relation.setChildEntityCode("asset_line");
        relation.setChildRefFieldCode("assetId");
        relation.setRelationType("ONE_TO_MANY");
        relation.setDataKey("lines");
        relation.setEnabled(true);
        value.getSnapshotScope().setRelations(List.of(relation));
        return value;
    }
}
