package com.workflow.process.status.application;

import com.workflow.outbox.api.OutboxPublishRequest;
import com.workflow.outbox.api.OutboxPublisher;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ProcessStatusSyncPublisherTest {

    @Test
    void reconciliationRequeuesFailedProcessEndEvent() {
        OutboxPublisher outboxPublisher =
                mock(OutboxPublisher.class);
        ProcessStatusSyncPublisher publisher =
                new ProcessStatusSyncPublisher(outboxPublisher);

        publisher.republishProcessEnd(
                "process-1",
                "expense",
                "record-1",
                "COMPLETED",
                "APPROVED");

        verify(outboxPublisher).publishOrRequeueFailed(
                argThat(this::isExpectedProcessEnd));
        verify(outboxPublisher, never()).publish(
                argThat(this::isExpectedProcessEnd));
    }

    private boolean isExpectedProcessEnd(
            OutboxPublishRequest request) {
        return request != null
                && ProcessStatusSyncPublisher.TOPIC.equals(
                        request.topic())
                && "process-1:PROCESS_END:END".equals(
                        request.eventKey())
                && "process-1".equals(request.aggregateId())
                && request.maxRetries() == 20;
    }
}
