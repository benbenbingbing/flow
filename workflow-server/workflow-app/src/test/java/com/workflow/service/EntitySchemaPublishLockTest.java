package com.workflow.service;

import com.workflow.core.database.port.DatabaseLockPort;
import com.workflow.entity.definition.application.EntitySchemaPublishLock;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntitySchemaPublishLockTest {
    @Test
    void acquiresWithoutWaitingAndReleasesOnlyTheMatchingHandle() {
        var port = mock(DatabaseLockPort.class);
        var handle = mock(DatabaseLockPort.Handle.class);
        when(port.tryAcquire("flow:entity", "entity-1")).thenReturn(Optional.of(handle));
        var lock = new EntitySchemaPublishLock(port);
        assertTrue(lock.tryAcquire("entity-1"));
        assertFalse(lock.tryAcquire("entity-1"));
        lock.release("entity-2");
        verifyNoInteractions(handle);
        lock.release("entity-1");
        lock.release("entity-1");
        verify(handle).close();
        verify(port).tryAcquire("flow:entity", "entity-1");
    }

    @Test
    void reportsBusyWithoutOwningAnotherPublishersHandle() {
        var port = mock(DatabaseLockPort.class);
        when(port.tryAcquire("flow:entity", "entity-1")).thenReturn(Optional.empty());
        var lock = new EntitySchemaPublishLock(port);
        assertFalse(lock.tryAcquire("entity-1"));
        lock.release("entity-1");
        verify(port).tryAcquire("flow:entity", "entity-1");
        verifyNoMoreInteractions(port);
    }

    @Test
    void releaseFailureDoesNotMaskPublishResultAndClearsThreadState() {
        var port = mock(DatabaseLockPort.class);
        var handle = mock(DatabaseLockPort.Handle.class);
        when(port.tryAcquire("flow:entity", "entity-1")).thenReturn(Optional.of(handle));
        doThrow(new IllegalStateException("disconnected")).when(handle).close();
        var lock = new EntitySchemaPublishLock(port);
        assertTrue(lock.tryAcquire("entity-1"));
        assertDoesNotThrow(() -> lock.release("entity-1"));
        assertTrue(lock.tryAcquire("entity-1"));
        lock.release("entity-1");
    }
}
