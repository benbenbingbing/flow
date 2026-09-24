package com.workflow.core.concurrent;

import com.workflow.core.database.ExecutionDeadlineInterceptor;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.session.ResultHandler;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 预算必须约束正在执行的 IO，并在工作线程复用时解除上下文。 */
class ExecutionDeadlineTest {
    @Test
    void nestedBudgetCannotExtendParentAndScopesAreRestored() {
        assertNull(ExecutionDeadline.current());
        try (var parent = ExecutionDeadline.afterMillis(500); var scope = parent.attach()) {
            try (var child = ExecutionDeadline.afterMillis(5000); var nested = child.attach()) {
                assertSame(child, ExecutionDeadline.current());
                assertTrue(child.remainingMillis() <= 500);
                parent.cancel();
                assertThrows(ExecutionDeadline.ExceededException.class, child::check);
            }
            assertSame(parent, ExecutionDeadline.current());
        }
        assertNull(ExecutionDeadline.current());
    }

    @Test
    void deadlineCancelsBlockedJdbcAndReleasesWorker() throws Exception {
        Statement statement = mock(Statement.class);
        StatementHandler handler = mock(StatementHandler.class);
        CountDownLatch cancelled = new CountDownLatch(1);
        doAnswer(call -> { cancelled.countDown(); return null; }).when(statement).cancel();
        when(handler.query(eq(statement), any())).thenAnswer(call -> {
            assertTrue(cancelled.await(3, TimeUnit.SECONDS), "阻塞语句必须收到 cancel");
            return List.of();
        });
        StatementHandler intercepted = (StatementHandler) new ExecutionDeadlineInterceptor().plugin(handler);
        var worker = Executors.newSingleThreadExecutor();
        try {
            worker.submit(() -> {
                try (var deadline = ExecutionDeadline.afterMillis(150); var scope = deadline.attach()) {
                    assertThrows(ExecutionDeadline.ExceededException.class,
                            () -> intercepted.query(statement, (ResultHandler<?>) null));
                }
            }).get(4, TimeUnit.SECONDS);
            verify(statement).setQueryTimeout(1);
            verify(statement).cancel();
            assertNull(worker.submit(ExecutionDeadline::current).get(1, TimeUnit.SECONDS));
        } finally {
            worker.shutdownNow();
        }
    }

    @Test
    void normalCompletionDoesNotCancelAndKeepsStricterJdbcTimeout() throws Exception {
        Statement statement = mock(Statement.class);
        when(statement.getQueryTimeout()).thenReturn(1);
        StatementHandler handler = mock(StatementHandler.class);
        when(handler.query(eq(statement), any())).thenReturn(List.of("ok"));
        StatementHandler intercepted = (StatementHandler) new ExecutionDeadlineInterceptor().plugin(handler);
        try (var deadline = ExecutionDeadline.afterMillis(5000); var scope = deadline.attach()) {
            assertEquals(List.of("ok"), intercepted.query(statement, null));
            deadline.cancel();
        }
        verify(statement, never()).setQueryTimeout(anyInt());
        verify(statement, never()).cancel();
    }
}
