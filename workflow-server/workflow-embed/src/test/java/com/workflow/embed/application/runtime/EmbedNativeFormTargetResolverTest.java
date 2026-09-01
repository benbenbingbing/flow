package com.workflow.embed.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.embed.EmbedNativeFormAccessPort;
import com.workflow.embed.application.port.EmbedRuntimeReleasePort;
import com.workflow.embed.domain.AuthenticatedEmbedSession;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.domain.EmbedNativeFormTarget;
import com.workflow.embed.domain.EmbedRuntimeReleaseSnapshot;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EmbedNativeFormTargetResolverTest {

    private final EmbedRuntimeReleasePort releasePort = mock(
            EmbedRuntimeReleasePort.class);
    private final EmbedNativeFormAccessPort accessPort = mock(
            EmbedNativeFormAccessPort.class);
    private final EmbedNativeFormTargetResolver resolver =
            new EmbedNativeFormTargetResolver(
                    releasePort, accessPort, new ObjectMapper());

    @Test
    void listOnlySessionAllowsNativeCreateWithoutEmbedCapabilityCeiling() {
        AuthenticatedEmbedSession session = listSession();
        when(releasePort.find(
                "session-1", "view-1", "view-release-1"))
                .thenReturn(listRelease());

        EmbedNativeFormTarget target = resolver.authorize(
                session, "create", null);

        assertEquals("CREATE", target.entryMode());
        assertEquals("form-1", target.formId());
        assertNull(target.recordId());
        verifyNoInteractions(accessPort);
    }

    @Test
    void listOnlySessionNormalizesEditAndRunsMappedUserViewDataScope() {
        AuthenticatedEmbedSession session = listSession();
        when(releasePort.find(
                "session-1", "view-1", "view-release-1"))
                .thenReturn(listRelease());
        when(accessPort.authorizeView(
                new EmbedNativeFormAccessPort.Target(
                        "work_order", "form-1", "form-release-4", 4,
                        "list-1", "list-release-7", 7),
                "record-9", Map.of()))
                .thenReturn(Optional.of(
                        new EmbedNativeFormAccessPort.ViewAccess(
                                "process-9")));

        EmbedNativeFormTarget target = resolver.authorize(
                session, "edit", "record-9");

        assertEquals("VIEW", target.entryMode());
        assertEquals("record-9", target.recordId());
        assertEquals("process-9", target.processInstanceId());
        verify(accessPort).authorizeView(
                new EmbedNativeFormAccessPort.Target(
                        "work_order", "form-1", "form-release-4", 4,
                        "list-1", "list-release-7", 7),
                "record-9", Map.of());
    }

    @Test
    void initialFormEntryStillRequiresPublishedEmbedCapability() {
        AuthenticatedEmbedSession session = formSession();
        when(releasePort.find(
                "session-1", "view-1", "view-release-1"))
                .thenReturn(formRelease());

        assertThrows(
                EmbedException.class,
                () -> resolver.authorize(session, "VIEW", "record-9"));
        verifyNoInteractions(accessPort);
    }

    @Test
    void signedNonRootFormUsesItsOwnRecordAndMappedUserDataScope() {
        AuthenticatedEmbedSession session = formSession();
        when(releasePort.find(
                "session-1", "view-1", "view-release-1"))
                .thenReturn(formRelease());
        EmbedNativeFormTarget child = new EmbedNativeFormTarget(
                "customer", "child-form", "child-release", 7,
                null, null, null, "VIEW", null, null,
                Map.of(), Map.of(), Map.of());
        when(accessPort.authorizeView(
                new EmbedNativeFormAccessPort.Target(
                        "customer", "child-form", "child-release", 7,
                        null, null, null),
                "child-record", Map.of()))
                .thenReturn(Optional.of(
                        new EmbedNativeFormAccessPort.ViewAccess(null)));

        EmbedNativeFormTarget authorized = resolver.authorizePinnedForm(
                session, child, "edit", "child-record");

        assertEquals("VIEW", authorized.entryMode());
        assertEquals("child-record", authorized.recordId());
    }

    @Test
    void signedApprovalFormUsesMappedUserViewDataScopeWithoutEmbedCapabilityCeiling() {
        AuthenticatedEmbedSession session = listSession();
        when(releasePort.find(
                "session-1", "view-1", "view-release-1"))
                .thenReturn(listRelease());
        EmbedNativeFormTarget taskForm = new EmbedNativeFormTarget(
                "work_order", "task-form", "task-release", 3,
                "list-1", "list-release-7", 7,
                "VIEW", null, null,
                Map.of(), Map.of(), Map.of());
        when(accessPort.authorizeView(
                new EmbedNativeFormAccessPort.Target(
                        "work_order", "task-form", "task-release", 3,
                        "list-1", "list-release-7", 7),
                "record-9", Map.of()))
                .thenReturn(Optional.of(
                        new EmbedNativeFormAccessPort.ViewAccess(
                                "process-9")));

        EmbedNativeFormTarget authorized = resolver.authorizePinnedForm(
                session, taskForm, "approve", "record-9");

        assertEquals("APPROVE", authorized.entryMode());
        assertEquals("record-9", authorized.recordId());
        assertEquals("process-9", authorized.processInstanceId());
        verify(accessPort).authorizeView(
                new EmbedNativeFormAccessPort.Target(
                        "work_order", "task-form", "task-release", 3,
                        "list-1", "list-release-7", 7),
                "record-9", Map.of());
    }

    private static AuthenticatedEmbedSession listSession() {
        return new AuthenticatedEmbedSession(
                "session-1", "app-1", "grant-1", "view-1",
                "view-release-1", "user-1", "alice",
                "https://portal.example", "channel-1234567890",
                "LIST", null, Map.of(), Set.of("LIST_QUERY"),
                Instant.parse("2026-09-01T00:30:00Z"),
                Instant.parse("2026-09-01T01:00:00Z"));
    }

    private static AuthenticatedEmbedSession formSession() {
        return new AuthenticatedEmbedSession(
                "session-1", "app-1", "grant-1", "view-1",
                "view-release-1", "user-1", "alice",
                "https://portal.example", "channel-1234567890",
                "VIEW", "record-9", Map.of(), Set.of(),
                Instant.parse("2026-09-01T00:30:00Z"),
                Instant.parse("2026-09-01T01:00:00Z"));
    }

    private static EmbedRuntimeReleaseSnapshot listRelease() {
        return release(
                "LIST", "list-1", "list-release-7", 7,
                "[\"LIST_QUERY\"]",
                "{\"resolved\":{\"defaultFormId\":\"form-1\"},"
                        + "\"entryModes\":[\"LIST\"]}");
    }

    private static EmbedRuntimeReleaseSnapshot formRelease() {
        return release(
                "FORM", null, null, null,
                "[\"RECORD_VIEW\"]",
                "{\"resolved\":{\"defaultFormId\":\"form-1\"},"
                        + "\"entryModes\":[\"VIEW\"]}");
    }

    private static EmbedRuntimeReleaseSnapshot release(
            String surfaceType,
            String listKey,
            String listReleaseId,
            Integer listReleaseVersion,
            String capabilities,
            String config) {
        return new EmbedRuntimeReleaseSnapshot(
                "view-release-1", "view-1", "view-key", "View", 1,
                surfaceType, "work_order", listKey,
                listReleaseId, listReleaseVersion,
                "form-release-4", 4,
                capabilities, "{}", "{}", "[]", "{}", config,
                "Alice", "zh-CN", "light");
    }
}
