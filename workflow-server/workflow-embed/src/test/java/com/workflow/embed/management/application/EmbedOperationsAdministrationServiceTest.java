package com.workflow.embed.management.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.workflow.contracts.identity.CurrentActor;
import com.workflow.contracts.identity.CurrentActorProvider;
import com.workflow.embed.application.audit.EmbedLifecycleAudit;
import com.workflow.embed.application.audit.EmbedAuditCorrelation;
import com.workflow.embed.application.audit.EmbedLifecycleAudit.Operator;
import com.workflow.embed.application.session.EmbedSessionTerminationService;
import com.workflow.embed.domain.EmbedSessionTermination;
import com.workflow.embed.domain.EmbedSessionTerminationResult;
import com.workflow.embed.management.api.EmbedManagementException;
import com.workflow.embed.management.domain.EmbedOperationsModel.LaunchQuery;
import com.workflow.embed.management.domain.EmbedOperationsModel.LaunchSummary;
import com.workflow.embed.management.domain.EmbedOperationsModel.Page;
import com.workflow.embed.management.port.EmbedOperationsRepository;
import com.workflow.embed.management.port.EmbedOperationsRepository.LaunchRevokeOutcome;
import com.workflow.embed.management.port.EmbedOperationsRepository.Scope;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmbedOperationsAdministrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-27T09:00:00Z");
    private static final EmbedAuditCorrelation CORRELATION =
            EmbedAuditCorrelation.of("trace-admin", "request-admin");

    private EmbedOperationsRepository repository;
    private EmbedSessionTerminationService terminationService;
    private EmbedLifecycleAudit audit;
    private EmbedOperationsAdministrationService service;

    @BeforeEach
    void setUp() {
        repository = mock(EmbedOperationsRepository.class);
        terminationService = mock(EmbedSessionTerminationService.class);
        audit = mock(EmbedLifecycleAudit.class);
        CurrentActorProvider actorProvider = () -> new CurrentActor("admin-1", "alice");
        service = new EmbedOperationsAdministrationService(
                repository, terminationService, actorProvider, audit,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void launchQueryUsesBoundedWindowWhitelistAndOpaqueSeekCursor() {
        LaunchSummary newest = launch("lch_3", NOW.minusSeconds(3));
        LaunchSummary second = launch("lch_2", NOW.minusSeconds(4));
        LaunchSummary extra = launch("lch_1", NOW.minusSeconds(5));
        when(repository.findLaunches(
                eq("app-1"), isNull(), eq("ISSUED"), any(), any(),
                isNull(), isNull(), eq(3)))
                .thenReturn(List.of(newest, second, extra));

        Page<LaunchSummary> page = service.findLaunches(new LaunchQuery(
                "app-1", null, "issued", null, null, null, 2));

        assertEquals(List.of(newest, second), page.items());
        assertNotNull(page.nextCursor());
        assertFalse(page.nextCursor().contains("lch_2"));

        when(repository.findLaunches(
                eq("app-1"), isNull(), eq("ISSUED"), any(), any(),
                eq(second.createTime()), eq("lch_2"), eq(3)))
                .thenReturn(List.of());
        service.findLaunches(new LaunchQuery(
                "app-1", null, "ISSUED", null, null, page.nextCursor(), 2));
        verify(repository).findLaunches(
                eq("app-1"), isNull(), eq("ISSUED"), any(), any(),
                eq(second.createTime()), eq("lch_2"), eq(3));
    }

    @Test
    void queryRejectsUnscopedOversizedOrUnknownFiltersBeforePersistence() {
        assertThrows(EmbedManagementException.class,
                () -> service.findLaunches(new LaunchQuery(
                        null, null, null, null, null, null, 50)));
        assertThrows(EmbedManagementException.class,
                () -> service.findLaunches(new LaunchQuery(
                        "app-1", null, "DELETED", null, null, null, 50)));
        assertThrows(EmbedManagementException.class,
                () -> service.findLaunches(new LaunchQuery(
                        "app-1", null, null,
                        NOW.minusSeconds(32L * 86_400L), NOW, null, 50)));

        verify(repository, never()).findLaunches(
                any(), any(), any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void bulkRevocationIsBoundedCursorBasedAndUsesPerSessionTerminationUseCase() {
        when(repository.findActiveSessionIds(
                Scope.VIEW, "view-1", null, 3))
                .thenReturn(List.of("session-1", "session-2", "session-3"));
        when(terminationService.terminateById(
                eq("session-1"), eq("REVOKED"), eq("ADMINISTRATIVE_REVOKE"),
                any(), any(), any(), eq(CORRELATION)))
                .thenReturn(result("session-1", EmbedSessionTermination.TERMINATED, "REVOKED"));
        when(terminationService.terminateById(
                eq("session-2"), eq("REVOKED"), eq("ADMINISTRATIVE_REVOKE"),
                any(), any(), any(), eq(CORRELATION)))
                .thenReturn(result("session-2", EmbedSessionTermination.REVOKED, "REVOKED"));

        var first = service.revokeViewSessions(
                "view-1", null, null, 2, CORRELATION);

        assertEquals(2, first.processed());
        assertEquals(1, first.revoked());
        assertEquals(1, first.alreadyTerminal());
        assertNotNull(first.nextCursor());
        verify(terminationService, never()).terminateById(
                eq("session-3"), any(), any(), any(), any(), any(), any());

        when(repository.findActiveSessionIds(
                Scope.VIEW, "view-1", "session-2", 3)).thenReturn(List.of());
        service.revokeViewSessions(
                "view-1", null, first.nextCursor(), 2, CORRELATION);
        verify(repository).findActiveSessionIds(
                Scope.VIEW, "view-1", "session-2", 3);
    }

    @Test
    void launchRevocationOnlyTransitionsIssuedAndAlreadyRevokedIsIdempotent() {
        LaunchSummary revoked = new LaunchSummary(
                "lch_1", "app-1", "grant-1", "view-1", "release-1",
                "REVOKED", "LIST", NOW.plusSeconds(60), null, NOW, NOW.minusSeconds(1));
        when(repository.revokeIssuedLaunch("lch_1", NOW))
                .thenReturn(LaunchRevokeOutcome.ALREADY_REVOKED);
        when(repository.findLaunch("lch_1")).thenReturn(Optional.of(revoked));

        var result = service.revokeLaunch("lch_1", CORRELATION);

        assertFalse(result.revoked());
        assertEquals(true, result.idempotent());
        verify(audit, never()).launchRevoked(any(), any(), any());

        LaunchSummary newlyRevoked = new LaunchSummary(
                "lch_issued", "app-1", "grant-1", "view-1", "release-1",
                "REVOKED", "LIST", NOW.plusSeconds(60), null, NOW, NOW.minusSeconds(1));
        when(repository.revokeIssuedLaunch("lch_issued", NOW))
                .thenReturn(LaunchRevokeOutcome.REVOKED);
        when(repository.findLaunch("lch_issued")).thenReturn(Optional.of(newlyRevoked));
        service.revokeLaunch("lch_issued", CORRELATION);
        verify(audit).launchRevoked(
                "lch_issued", new Operator("admin-1", "alice"), CORRELATION);

        when(repository.revokeIssuedLaunch("lch_2", NOW))
                .thenReturn(LaunchRevokeOutcome.CONSUMED);
        EmbedManagementException error = assertThrows(
                EmbedManagementException.class,
                () -> service.revokeLaunch("lch_2", CORRELATION));
        assertEquals(409, error.status());
    }

    private static LaunchSummary launch(String id, Instant createdAt) {
        return new LaunchSummary(
                id, "app-1", "grant-1", "view-1", "release-1", "ISSUED", "LIST",
                NOW.plusSeconds(60), null, null, createdAt);
    }

    private static EmbedSessionTerminationResult result(
            String id, EmbedSessionTermination outcome, String status) {
        return new EmbedSessionTerminationResult(
                outcome, outcome == EmbedSessionTermination.TERMINATED,
                id, "app-1", "view-1", "user-1", status);
    }
}
