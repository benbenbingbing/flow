package com.workflow.process.status.application;

import com.workflow.port.platform.outbox.model.OutboxPublishRequest;
import com.workflow.port.platform.WorkflowOutboxEventPort;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ProcessStatusSyncPublisherTest {

    @Test
    void reconciliationPreservesExistingRetryBudgetAndDeadLetters() {
        WorkflowOutboxEventPort outboxPublisher =
                mock(WorkflowOutboxEventPort.class);
        ProcessStatusSyncPublisher publisher =
                new ProcessStatusSyncPublisher(outboxPublisher);

        publisher.republishProcessEnd(
                "process-1",
                "expense",
                "record-1",
                "COMPLETED",
                "APPROVED");

        verify(outboxPublisher).publish(
                argThat(this::isExpectedProcessEnd));
        verify(outboxPublisher, never()).publishOrRequeueFailed(
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
