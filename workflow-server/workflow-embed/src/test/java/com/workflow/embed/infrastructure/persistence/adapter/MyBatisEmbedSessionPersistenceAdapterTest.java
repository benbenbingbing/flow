package com.workflow.embed.infrastructure.persistence.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.domain.EmbedSessionTermination;
import com.workflow.embed.domain.EmbedSessionTerminationResult;
import com.workflow.embed.infrastructure.persistence.mapper.EmbedSessionExchangeMapper;
import com.workflow.embed.infrastructure.persistence.mapper.EmbedSessionPersistenceMapper;
import com.workflow.embed.infrastructure.persistence.record.EmbedSessionCounterRow;
import com.workflow.embed.infrastructure.persistence.record.EmbedSessionTerminationRow;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class MyBatisEmbedSessionPersistenceAdapterTest {

    private static final Instant NOW = Instant.parse("2026-08-27T04:00:00Z");
    private static final LocalDateTime LOCAL_NOW = LocalDateTime.parse("2026-08-27T04:00:00");

    @Test
    void releasesCounterExactlyAfterGuardedActiveSessionTransition() {
        EmbedSessionPersistenceMapper mapper = mock(EmbedSessionPersistenceMapper.class);
        EmbedSessionExchangeMapper exchangeMapper = mock(EmbedSessionExchangeMapper.class);
        EmbedSessionTerminationRow active = active();
        when(mapper.findTerminationCandidate("digest")).thenReturn(active);
        when(exchangeMapper.lockCounter("grant-1", "user-1"))
                .thenReturn(new EmbedSessionCounterRow("grant-1", "user-1", 1, 2));
        when(mapper.lockSessionForTermination("session-1")).thenReturn(active);
        when(mapper.terminateActive(
                "session-1", "LOGGED_OUT", null, LOCAL_NOW)).thenReturn(1);
        when(mapper.decrementCounter("grant-1", "user-1", LOCAL_NOW)).thenReturn(1);
        MyBatisEmbedSessionPersistenceAdapter adapter =
                new MyBatisEmbedSessionPersistenceAdapter(mapper, exchangeMapper);

        assertEquals(EmbedSessionTermination.TERMINATED,
                adapter.terminate("digest", "LOGGED_OUT", null, NOW));

        verify(mapper).terminateActive("session-1", "LOGGED_OUT", null, LOCAL_NOW);
        verify(mapper).decrementCounter("grant-1", "user-1", LOCAL_NOW);
    }

    @Test
    void loggedOutReplayDoesNotLockOrDecrementCounterAgain() {
        EmbedSessionPersistenceMapper mapper = mock(EmbedSessionPersistenceMapper.class);
        EmbedSessionExchangeMapper exchangeMapper = mock(EmbedSessionExchangeMapper.class);
        when(mapper.findTerminationCandidate("digest")).thenReturn(
                new EmbedSessionTerminationRow(
                        "session-1", "app-1", "grant-1", "view-1", "user-1",
                        "LOGGED_OUT", true,
                        LOCAL_NOW.plusMinutes(1), LOCAL_NOW.plusHours(1)));
        MyBatisEmbedSessionPersistenceAdapter adapter =
                new MyBatisEmbedSessionPersistenceAdapter(mapper, exchangeMapper);

        assertEquals(EmbedSessionTermination.ALREADY_LOGGED_OUT,
                adapter.terminate("digest", "LOGGED_OUT", null, NOW));

        verify(exchangeMapper, never()).lockCounter("grant-1", "user-1");
        verify(mapper, never()).decrementCounter("grant-1", "user-1", LOCAL_NOW);
    }

    @Test
    void timedOutSessionTerminatesAsExpiredEvenWhenLogoutRaces() {
        EmbedSessionPersistenceMapper mapper = mock(EmbedSessionPersistenceMapper.class);
        EmbedSessionExchangeMapper exchangeMapper = mock(EmbedSessionExchangeMapper.class);
        EmbedSessionTerminationRow timedOut = new EmbedSessionTerminationRow(
                "session-1", "app-1", "grant-1", "view-1", "user-1", "ACTIVE", false,
                LOCAL_NOW, LOCAL_NOW.plusHours(1));
        when(mapper.findTerminationCandidate("digest")).thenReturn(timedOut);
        when(exchangeMapper.lockCounter("grant-1", "user-1"))
                .thenReturn(new EmbedSessionCounterRow("grant-1", "user-1", 1, 2));
        when(mapper.lockSessionForTermination("session-1")).thenReturn(timedOut);
        when(mapper.terminateActive(
                "session-1", "EXPIRED", null, LOCAL_NOW)).thenReturn(1);
        when(mapper.decrementCounter("grant-1", "user-1", LOCAL_NOW)).thenReturn(1);
        MyBatisEmbedSessionPersistenceAdapter adapter =
                new MyBatisEmbedSessionPersistenceAdapter(mapper, exchangeMapper);

        assertEquals(EmbedSessionTermination.EXPIRED,
                adapter.terminate("digest", "LOGGED_OUT", null, NOW));

        verify(mapper).terminateActive("session-1", "EXPIRED", null, LOCAL_NOW);
    }

    @Test
    void administrativeIdEntryUsesSameCounterThenSessionLockAndExactlyOnceGuard() {
        EmbedSessionPersistenceMapper mapper = mock(EmbedSessionPersistenceMapper.class);
        EmbedSessionExchangeMapper exchangeMapper = mock(EmbedSessionExchangeMapper.class);
        EmbedSessionTerminationRow active = active();
        when(mapper.findTerminationCandidateById("session-1")).thenReturn(active);
        when(exchangeMapper.lockCounter("grant-1", "user-1"))
                .thenReturn(new EmbedSessionCounterRow("grant-1", "user-1", 1, 2));
        when(mapper.lockSessionForTermination("session-1")).thenReturn(active);
        when(mapper.terminateActive(
                "session-1", "REVOKED", "ADMIN_REVOKE", LOCAL_NOW)).thenReturn(1);
        when(mapper.decrementCounter("grant-1", "user-1", LOCAL_NOW)).thenReturn(1);
        MyBatisEmbedSessionPersistenceAdapter adapter =
                new MyBatisEmbedSessionPersistenceAdapter(mapper, exchangeMapper);

        EmbedSessionTerminationResult result = adapter.terminateById(
                "session-1", "REVOKED", "ADMIN_REVOKE", NOW);

        assertEquals(EmbedSessionTermination.TERMINATED, result.outcome());
        assertEquals("app-1", result.applicationId());
        assertEquals("view-1", result.viewId());
        var ordered = org.mockito.Mockito.inOrder(exchangeMapper, mapper);
        ordered.verify(exchangeMapper).lockCounter("grant-1", "user-1");
        ordered.verify(mapper).lockSessionForTermination("session-1");
        ordered.verify(mapper).terminateActive(
                "session-1", "REVOKED", "ADMIN_REVOKE", LOCAL_NOW);
        ordered.verify(mapper).decrementCounter("grant-1", "user-1", LOCAL_NOW);
    }

    @Test
    void databaseReadFailureUsesStableRuntimeUnavailableError() {
        EmbedSessionPersistenceMapper mapper = mock(EmbedSessionPersistenceMapper.class);
        EmbedSessionExchangeMapper exchangeMapper = mock(EmbedSessionExchangeMapper.class);
        when(mapper.findByTokenDigest("digest"))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));
        MyBatisEmbedSessionPersistenceAdapter adapter =
                new MyBatisEmbedSessionPersistenceAdapter(mapper, exchangeMapper);

        EmbedException error = assertThrows(
                EmbedException.class,
                () -> adapter.findByTokenDigest("digest"));

        assertEquals(503, error.getStatus());
        assertEquals(EmbedErrorCode.EMBED_RUNTIME_UNAVAILABLE, error.getErrorCode());
    }

    private static EmbedSessionTerminationRow active() {
        return new EmbedSessionTerminationRow(
                "session-1", "app-1", "grant-1", "view-1", "user-1", "ACTIVE", false,
                LOCAL_NOW.plusMinutes(5), LOCAL_NOW.plusHours(1));
    }
}
