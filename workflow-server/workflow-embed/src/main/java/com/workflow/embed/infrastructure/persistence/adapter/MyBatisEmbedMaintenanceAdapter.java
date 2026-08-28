package com.workflow.embed.infrastructure.persistence.adapter;

import com.workflow.embed.application.port.EmbedMaintenancePort;
import com.workflow.embed.infrastructure.persistence.mapper.EmbedMaintenanceMapper;
import com.workflow.embed.infrastructure.persistence.record.EmbedSessionCounterObservationRow;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** MyBatis 维护适配器；每次调用只执行一个有界 SQL 和一个独立小事务。 */
@Repository
@ConditionalOnProperty(prefix = "workflow.embed", name = "enabled", havingValue = "true")
public class MyBatisEmbedMaintenanceAdapter implements EmbedMaintenancePort {

    private static final int MAX_BATCH_SIZE = 1_000;

    private final EmbedMaintenanceMapper mapper;

    public MyBatisEmbedMaintenanceAdapter(EmbedMaintenanceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int deleteExpiredAssertionReplays(Instant now, int limit) {
        return write(() -> mapper.deleteExpiredAssertionReplays(local(now), batch(limit)));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int expireIssuedLaunches(Instant now, int limit) {
        return write(() -> mapper.expireIssuedLaunches(local(now), batch(limit)));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int eraseTerminalSessionContexts(Instant cutoff, Instant now, int limit) {
        return write(() -> mapper.eraseTerminalSessionContexts(
                local(cutoff), local(now), batch(limit)));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<CounterObservation> inspectStoredCounterPage(CounterCursor after, int limit) {
        return read(() -> mapper.inspectStoredCounterPage(
                afterGrantId(after), afterFlowUserId(after), batch(limit)));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<CounterObservation> inspectActiveSessionPairPage(
            CounterCursor after,
            int limit) {
        return read(() -> mapper.inspectActiveSessionPairPage(
                afterGrantId(after), afterFlowUserId(after), batch(limit)));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int deleteOrphanOperationReceipts(Instant cutoff, int limit) {
        return write(() -> mapper.deleteOrphanOperationReceipts(local(cutoff), batch(limit)));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int deleteTerminalSessions(Instant cutoff, int limit) {
        return write(() -> mapper.deleteTerminalSessions(local(cutoff), batch(limit)));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int deleteUnreferencedTerminalLaunches(Instant cutoff, int limit) {
        return write(() -> mapper.deleteUnreferencedTerminalLaunches(
                local(cutoff), batch(limit)));
    }

    private static int write(WriteOperation operation) {
        try {
            return operation.execute();
        } catch (DataAccessException error) {
            throw unavailable(error);
        }
    }

    private static List<CounterObservation> read(ReadOperation operation) {
        try {
            List<EmbedSessionCounterObservationRow> rows = operation.execute();
            return rows == null
                    ? List.of()
                    : rows.stream().map(MyBatisEmbedMaintenanceAdapter::map).toList();
        } catch (DataAccessException error) {
            throw unavailable(error);
        }
    }

    private static CounterObservation map(EmbedSessionCounterObservationRow row) {
        return new CounterObservation(
                row.grantId(), row.flowUserId(), row.storedCount(), row.actualCount());
    }

    private static String afterGrantId(CounterCursor after) {
        return after == null ? "" : after.grantId();
    }

    private static String afterFlowUserId(CounterCursor after) {
        return after == null ? "" : after.flowUserId();
    }

    private static int batch(int limit) {
        if (limit < 1 || limit > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("maintenance batch size is out of range");
        }
        return limit;
    }

    private static LocalDateTime local(Instant value) {
        if (value == null) {
            throw new IllegalArgumentException("maintenance timestamp is required");
        }
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static IllegalStateException unavailable(DataAccessException cause) {
        return new IllegalStateException("Embed maintenance persistence is unavailable", cause);
    }

    @FunctionalInterface
    private interface WriteOperation {
        int execute();
    }

    @FunctionalInterface
    private interface ReadOperation {
        List<EmbedSessionCounterObservationRow> execute();
    }
}
