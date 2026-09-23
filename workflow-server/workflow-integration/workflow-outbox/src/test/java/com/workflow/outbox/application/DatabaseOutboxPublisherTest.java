package com.workflow.outbox.application;

import org.springframework.jdbc.core.JdbcTemplate;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.core.database.JdbcWriteAttempt;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.outbox.api.OutboxPublishRequest;
import com.workflow.outbox.infrastructure.persistence.mapper.OutboxRecordMapper;
import com.workflow.outbox.infrastructure.persistence.record.OutboxRecord;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DatabaseOutboxPublisherTest {

    @Test
    void normalDuplicatePublishRemainsStrictlyIdempotent() {
        OutboxRecordMapper mapper =
                mock(OutboxRecordMapper.class);
        doThrow(new DuplicateKeyException("duplicate"))
                .when(mapper)
                .insert(any(OutboxRecord.class));
        DatabaseOutboxPublisher publisher =
                new DatabaseOutboxPublisher(
                        mapper,
                        new ObjectMapper(),
                new JdbcWriteAttempt(new JdbcTemplate(), DatabaseDialects.insert(DatabaseVendor.MYSQL)), mock(com.workflow.core.database.JdbcLockedRow.class));

        publisher.publish(request());

        verify(mapper, never()).requeueFailedOrDead(
                anyString(),
                anyString(),
                any(),
                any(),
                anyString(),
                anyInt());
    }

    @Test
    void reconciliationCanRequeueFailedOrDeadDuplicate() {
        OutboxRecordMapper mapper =
                mock(OutboxRecordMapper.class);
        doThrow(new DuplicateKeyException("duplicate"))
                .when(mapper)
                .insert(any(OutboxRecord.class));
        DatabaseOutboxPublisher publisher =
                new DatabaseOutboxPublisher(
                        mapper,
                        new ObjectMapper(),
                new JdbcWriteAttempt(new JdbcTemplate(), DatabaseDialects.insert(DatabaseVendor.MYSQL)), mock(com.workflow.core.database.JdbcLockedRow.class));

        publisher.publishOrRequeueFailed(request());

        verify(mapper).requeueFailedOrDead(
                "PROCESS_STATUS_SYNC",
                "process-1:PROCESS_END:END",
                "PROCESS_INSTANCE",
                "process-1",
                "{\"status\":\"ENDED\"}",
                20);
    }

    private OutboxPublishRequest request() {
        return new OutboxPublishRequest(
                "PROCESS_STATUS_SYNC",
                "process-1:PROCESS_END:END",
                "PROCESS_INSTANCE",
                "process-1",
                Map.of("status", "ENDED"),
                20);
    }
}
