package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
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
    private UiConfigReleaseService releaseService;
    private UiPublishedDataSourceReferenceGuard guard;

    @BeforeEach
    void setUp() {
        releaseMapper = mock(UiConfigReleaseMapper.class);
        releaseService = mock(UiConfigReleaseService.class);
        guard = new UiPublishedDataSourceReferenceGuard(
                releaseMapper,
                releaseService,
                new JsonDocumentCodec(new ObjectMapper()));
        when(releaseMapper.findExecutableDataSourceReferenceCandidates(
                anyString())).thenReturn(List.of());
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
