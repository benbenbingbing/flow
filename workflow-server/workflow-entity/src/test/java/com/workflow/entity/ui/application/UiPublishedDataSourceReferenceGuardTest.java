package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.mutationpolicy.infrastructure.persistence.mapper.EntityMutationPolicyReleaseMapper;
import com.workflow.entity.mutationpolicy.infrastructure.persistence.record.EntityMutationPolicyRelease;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityVersionConfigReleaseMapper;
import com.workflow.entity.version.infrastructure.persistence.record.EntityVersionConfigRelease;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UiPublishedDataSourceReferenceGuardTest {

    private UiConfigReleaseMapper releaseMapper;
    private EntityMutationPolicyReleaseMapper mutationReleaseMapper;
    private EntityVersionConfigReleaseMapper legacyMutationReleaseMapper;
    private UiConfigReleaseService releaseService;
    private UiPublishedDataSourceReferenceGuard guard;

    @BeforeEach
    void setUp() {
        releaseMapper = mock(UiConfigReleaseMapper.class);
        mutationReleaseMapper = mock(
                EntityMutationPolicyReleaseMapper.class);
        legacyMutationReleaseMapper = mock(
                EntityVersionConfigReleaseMapper.class);
        releaseService = mock(UiConfigReleaseService.class);
        guard = new UiPublishedDataSourceReferenceGuard(
                releaseMapper,
                mutationReleaseMapper,
                legacyMutationReleaseMapper,
                releaseService,
                new JsonDocumentCodec(new ObjectMapper()));
        when(releaseMapper.findExecutableDataSourceReferenceCandidates(
                anyString())).thenReturn(List.of());
        when(mutationReleaseMapper
                .findActiveDataSourceReferenceCandidates(anyString()))
                .thenReturn(List.of());
        when(legacyMutationReleaseMapper
                .findActiveDataSourceReferenceCandidates(anyString()))
                .thenReturn(List.of());
    }

    @Test
    void blocksExactEventStepReferenceInActiveSnapshot() {
        UiConfigRelease release = release("FORM", "form-1", 3);
        release.setStatus("SUPERSEDED");
        candidates(release);
        when(releaseService.verifiedReleaseSnapshot(release))
                .thenReturn(Map.of(
                        "eventBindings",
                        List.of(Map.of(
                                "steps",
                                List.of(Map.of(
                                        "serviceId", "source-1",
                                        "operationCode", "load"))))));

        BusinessConflictException error = assertThrows(
                BusinessConflictException.class,
                () -> guard.requireNoExecutableReferences("source-1"));

        assertEquals("UI_DATA_SOURCE_EXECUTABLE_RELEASE_REFERENCED",
                error.getErrorCode());
    }

    @Test
    void blocksDirectListQueryReferenceAsWellAsEventReferences() {
        UiConfigRelease release = release("LIST", "list-1", 5);
        candidates(release);
        when(releaseService.verifiedReleaseSnapshot(release))
                .thenReturn(Map.of(
                        "list",
                        Map.of(
                                "queryDataSourceId", "source-1",
                                "queryOperationCode", "query")));

        BusinessConflictException error = assertThrows(
                BusinessConflictException.class,
                () -> guard.requireNoExecutableReferences("source-1"));

        assertEquals("UI_DATA_SOURCE_EXECUTABLE_RELEASE_REFERENCED",
                error.getErrorCode());
    }

    @Test
    void parsesNestedFormBindingDocumentBeforeExactMatching() {
        UiConfigRelease release = release("FORM", "form-1", 6);
        candidates(release);
        when(releaseService.verifiedReleaseSnapshot(release))
                .thenReturn(Map.of(
                        "form",
                        Map.of(
                                "dataSourceBindingsDocument",
                                "{\"bindings\":[{\"serviceId\":\"source-1\","
                                        + "\"operationCode\":\"load\"}]}")));

        BusinessConflictException error = assertThrows(
                BusinessConflictException.class,
                () -> guard.requireNoExecutableReferences("source-1"));

        assertEquals("UI_DATA_SOURCE_EXECUTABLE_RELEASE_REFERENCED",
                error.getErrorCode());
    }

    @Test
    void blocksManagedInterfaceInActiveMutationPolicyRelease() {
        EntityMutationPolicyRelease release =
                new EntityMutationPolicyRelease();
        release.setId("mutation-release-1");
        release.setConfigId("mutation-config-1");
        release.setVersion(4);
        release.setConfigDocument("{\"enabled\":true,\"steps\":[{"
                + "\"stepType\":\"MANAGED_INTERFACE\","
                + "\"providerCode\":\"source-other\","
                + "\"config\":{\"dataSourceId\":\"source-1\","
                + "\"operationCode\":\"mutate\"}}]}");
        when(mutationReleaseMapper
                .findActiveDataSourceReferenceCandidates(anyString()))
                .thenReturn(List.of(release));

        BusinessConflictException error = assertThrows(
                BusinessConflictException.class,
                () -> guard.requireNoExecutableReferences("source-1"));

        assertEquals("UI_DATA_SOURCE_EXECUTABLE_RELEASE_REFERENCED",
                error.getErrorCode());
    }

    @Test
    void blocksManagedInterfaceInActiveLegacyMutationRelease() {
        EntityVersionConfigRelease release =
                new EntityVersionConfigRelease();
        release.setId("legacy-release-1");
        release.setConfigId("legacy-config-1");
        release.setVersion(9);
        release.setConfigDocument("{\"enabled\":true,\"steps\":[{"
                + "\"stepType\":\"MANAGED_INTERFACE\","
                + "\"providerCode\":\"source-1\","
                + "\"config\":{\"operationCode\":\"mutate\"}}]}");
        when(legacyMutationReleaseMapper
                .findActiveDataSourceReferenceCandidates(anyString()))
                .thenReturn(List.of(release));

        BusinessConflictException error = assertThrows(
                BusinessConflictException.class,
                () -> guard.requireNoExecutableReferences("source-1"));

        assertEquals("UI_DATA_SOURCE_EXECUTABLE_RELEASE_REFERENCED",
                error.getErrorCode());
    }

    @Test
    void ignoresManagedInterfaceWhenMutationPolicyRootIsDisabled() {
        EntityMutationPolicyRelease release =
                new EntityMutationPolicyRelease();
        release.setConfigId("mutation-config-disabled");
        release.setConfigDocument("{\"enabled\":false,\"steps\":[{"
                + "\"stepType\":\"MANAGED_INTERFACE\","
                + "\"providerCode\":\"source-1\"}]}");
        when(mutationReleaseMapper
                .findActiveDataSourceReferenceCandidates(anyString()))
                .thenReturn(List.of(release));

        assertDoesNotThrow(() ->
                guard.requireNoExecutableReferences("source-1"));
    }

    @Test
    void ignoresExplicitlyDisabledManagedInterfaceStep() {
        EntityMutationPolicyRelease release =
                new EntityMutationPolicyRelease();
        release.setConfigId("mutation-config-disabled-step");
        release.setConfigDocument("{\"enabled\":true,\"steps\":[{"
                + "\"enabled\":false,"
                + "\"stepType\":\"MANAGED_INTERFACE\","
                + "\"providerCode\":\"source-1\"}]}");
        when(mutationReleaseMapper
                .findActiveDataSourceReferenceCandidates(anyString()))
                .thenReturn(List.of(release));

        assertDoesNotThrow(() ->
                guard.requireNoExecutableReferences("source-1"));
    }

    @Test
    void nativeActiveReleaseShadowsLegacyFallbackForSameEntity() {
        EntityVersionConfigRelease release =
                new EntityVersionConfigRelease();
        release.setConfigId("legacy-config-1");
        release.setConfigDocument("{\"enabled\":true,\"steps\":[{"
                + "\"stepType\":\"MANAGED_INTERFACE\","
                + "\"providerCode\":\"source-1\"}]}");
        when(legacyMutationReleaseMapper
                .findActiveDataSourceReferenceCandidates(anyString()))
                .thenReturn(List.of(release));
        when(legacyMutationReleaseMapper
                .countActiveNativePolicyForLegacyConfig(
                        "legacy-config-1"))
                .thenReturn(1L);

        assertDoesNotThrow(() ->
                guard.requireNoExecutableReferences("source-1"));
    }

    @Test
    void danglingNativePointerDoesNotShadowExecutableLegacyFallback() {
        EntityVersionConfigRelease release =
                new EntityVersionConfigRelease();
        release.setConfigId("legacy-config-with-dangling-native");
        release.setConfigDocument("{\"enabled\":true,\"steps\":[{"
                + "\"stepType\":\"MANAGED_INTERFACE\","
                + "\"providerCode\":\"source-1\"}]}");
        when(legacyMutationReleaseMapper
                .findActiveDataSourceReferenceCandidates(anyString()))
                .thenReturn(List.of(release));
        // SQL 只有在 native active_release_id 能实际 join 发布行时才返回 > 0。
        when(legacyMutationReleaseMapper
                .countActiveNativePolicyForLegacyConfig(
                        "legacy-config-with-dangling-native"))
                .thenReturn(0L);

        BusinessConflictException error = assertThrows(
                BusinessConflictException.class,
                () -> guard.requireNoExecutableReferences("source-1"));

        assertEquals("UI_DATA_SOURCE_EXECUTABLE_RELEASE_REFERENCED",
                error.getErrorCode());
    }

    @Test
    void malformedMutationCandidateFailsClosed() {
        EntityMutationPolicyRelease release =
                new EntityMutationPolicyRelease();
        release.setConfigId("mutation-config-broken");
        release.setConfigDocument("{\"enabled\":true,\"steps\":[");
        when(mutationReleaseMapper
                .findActiveDataSourceReferenceCandidates(anyString()))
                .thenReturn(List.of(release));

        BusinessConflictException error = assertThrows(
                BusinessConflictException.class,
                () -> guard.requireNoExecutableReferences("source-1"));

        assertEquals("UI_DATA_SOURCE_PUBLISHED_REFERENCE_UNVERIFIABLE",
                error.getErrorCode());
    }

    @Test
    void candidateSubstringDoesNotCountAsExactReference() {
        UiConfigRelease release = release("FORM", "form-1", 1);
        candidates(release);
        when(releaseService.verifiedReleaseSnapshot(release))
                .thenReturn(Map.of(
                        "serviceId", "source-10",
                        "operationCode", "load"));

        assertDoesNotThrow(() ->
                guard.requireNoExecutableReferences("source-1"));
    }

    @Test
    void failsClosedWhenCandidateActiveSnapshotCannotBeVerified() {
        UiConfigRelease release = release("FORM", "form-1", 2);
        candidates(release);
        when(releaseService.verifiedReleaseSnapshot(release))
                .thenThrow(new IllegalArgumentException("hash mismatch"));

        BusinessConflictException error = assertThrows(
                BusinessConflictException.class,
                () -> guard.requireNoExecutableReferences("source-1"));

        assertEquals("UI_DATA_SOURCE_PUBLISHED_REFERENCE_UNVERIFIABLE",
                error.getErrorCode());
    }

    private void candidates(UiConfigRelease release) {
        when(releaseMapper.findExecutableDataSourceReferenceCandidates(
                anyString())).thenReturn(List.of(release));
    }

    private UiConfigRelease release(
            String configType,
            String configId,
            int version) {
        UiConfigRelease release = new UiConfigRelease();
        release.setId("release-" + configId);
        release.setConfigType(configType);
        release.setConfigId(configId);
        release.setVersion(version);
        release.setStatus("ACTIVE");
        return release;
    }
}
