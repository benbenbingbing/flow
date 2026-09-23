package com.workflow.entity.data;

import com.workflow.entity.data.infrastructure.schema.SchemaChangeQueuePort;
import com.workflow.entity.data.infrastructure.QueuedSchemaDdlExecutor;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QueuedSchemaDdlExecutorTest {
    private final SchemaChangeQueuePort queue = mock(SchemaChangeQueuePort.class);

    @Test
    void waitsForAppliedAndPreservesQueueFailure() {
        when(queue.enqueue("ddl")).thenReturn("request");
        when(queue.state("request")).thenReturn(Optional.of(new SchemaChangeQueuePort.State("RUNNING", null)),
                Optional.of(new SchemaChangeQueuePort.State("APPLIED", null)));
        assertDoesNotThrow(() -> executor(Duration.ofSeconds(1)).execute("ddl"));
        when(queue.state("request")).thenReturn(Optional.of(new SchemaChangeQueuePort.State("FAILED", "type mismatch")));
        assertTrue(assertThrows(IllegalStateException.class, () -> executor(Duration.ofSeconds(1)).execute("ddl"))
                .getMessage().contains("type mismatch"));
    }

    @Test
    void missingRequestAndTimeoutCannotBeReportedAsSuccess() {
        when(queue.enqueue("ddl")).thenReturn("request");
        when(queue.state("request")).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> executor(Duration.ofSeconds(1)).execute("ddl"));
        when(queue.state("request")).thenReturn(Optional.of(new SchemaChangeQueuePort.State("PENDING", null)));
        assertTrue(assertThrows(IllegalStateException.class, () -> executor(Duration.ofMillis(5)).execute("ddl"))
                .getMessage().contains("Timed out"));
    }

    @Test
    void interruptionKeepsThreadInterruptFlag() {
        when(queue.enqueue("ddl")).thenReturn("request");
        when(queue.state("request")).thenReturn(Optional.of(new SchemaChangeQueuePort.State("PENDING", null)));
        Thread.currentThread().interrupt();
        try {
            assertThrows(IllegalStateException.class, () -> executor(Duration.ofSeconds(1)).execute("ddl"));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }

    private QueuedSchemaDdlExecutor executor(Duration timeout) {
        return new QueuedSchemaDdlExecutor(queue, timeout, Duration.ofMillis(1));
    }
}
