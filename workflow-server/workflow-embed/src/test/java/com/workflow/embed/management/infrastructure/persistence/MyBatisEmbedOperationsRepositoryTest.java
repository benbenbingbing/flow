package com.workflow.embed.management.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.workflow.embed.management.infrastructure.persistence.record.EmbedOperationsRows.LaunchRow;
import com.workflow.embed.management.port.EmbedOperationsRepository.LaunchRevokeOutcome;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MyBatisEmbedOperationsRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-08-27T10:00:00Z");
    private static final LocalDateTime LOCAL_NOW = LocalDateTime.parse("2026-08-27T10:00:00");

    @Test
    void onlyFutureIssuedLaunchTransitionsToRevoked() {
        EmbedOperationsMapper mapper = mock(EmbedOperationsMapper.class);
        when(mapper.lockLaunch("lch_1")).thenReturn(row("ISSUED", LOCAL_NOW.plusMinutes(1)));
        when(mapper.revokeIssuedLaunch("lch_1", LOCAL_NOW)).thenReturn(1);
        MyBatisEmbedOperationsRepository repository =
                new MyBatisEmbedOperationsRepository(mapper);

        assertEquals(LaunchRevokeOutcome.REVOKED,
                repository.revokeIssuedLaunch("lch_1", NOW));

        verify(mapper).revokeIssuedLaunch("lch_1", LOCAL_NOW);
        verify(mapper, never()).expireIssuedLaunch("lch_1", LOCAL_NOW);
    }

    @Test
    void logicalExpiryIsPersistedAndConsumedOrRevokedRowsRemainIdempotent() {
        EmbedOperationsMapper mapper = mock(EmbedOperationsMapper.class);
        when(mapper.lockLaunch("expired"))
                .thenReturn(row("ISSUED", LOCAL_NOW));
        when(mapper.lockLaunch("consumed"))
                .thenReturn(row("CONSUMED", LOCAL_NOW.plusMinutes(1)));
        when(mapper.lockLaunch("revoked"))
                .thenReturn(row("REVOKED", LOCAL_NOW.plusMinutes(1)));
        MyBatisEmbedOperationsRepository repository =
                new MyBatisEmbedOperationsRepository(mapper);

        assertEquals(LaunchRevokeOutcome.EXPIRED,
                repository.revokeIssuedLaunch("expired", NOW));
        assertEquals(LaunchRevokeOutcome.CONSUMED,
                repository.revokeIssuedLaunch("consumed", NOW));
        assertEquals(LaunchRevokeOutcome.ALREADY_REVOKED,
                repository.revokeIssuedLaunch("revoked", NOW));

        verify(mapper).expireIssuedLaunch("expired", LOCAL_NOW);
        verify(mapper, never()).revokeIssuedLaunch("consumed", LOCAL_NOW);
        verify(mapper, never()).revokeIssuedLaunch("revoked", LOCAL_NOW);
    }

    private static LaunchRow row(String status, LocalDateTime expiresAt) {
        return new LaunchRow(
                "lch_1", "app-1", "grant-1", "view-1", "release-1",
                status, "LIST", expiresAt,
                "CONSUMED".equals(status) ? LOCAL_NOW : null,
                "REVOKED".equals(status) ? LOCAL_NOW : null,
                LOCAL_NOW.minusSeconds(1));
    }
}
