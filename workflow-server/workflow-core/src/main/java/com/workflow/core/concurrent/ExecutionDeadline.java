package com.workflow.core.concurrent;

import com.workflow.contracts.execution.port.ExecutionControlPort;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * 跨 HTTP、数据库与 Provider 的单调时钟预算。线程绑定只在明确的执行作用域内存在；
 * 取消回调使用独立有界线程，防止驱动的 cancel 阻塞返回超时的请求线程。
 */
public final class ExecutionDeadline implements ExecutionControlPort, AutoCloseable {
    private static final ThreadLocal<ExecutionDeadline> CURRENT = new ThreadLocal<>();
    private static final ScheduledThreadPoolExecutor EXPIRATIONS = expirationExecutor();
    private static final ThreadPoolExecutor CANCELLATIONS = new ThreadPoolExecutor(2, 2, 30,
            TimeUnit.SECONDS, new ArrayBlockingQueue<>(128), work -> {
                Thread thread = new Thread(work, "workflow-resource-cancel");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private final long expiresAt;
    private final Set<Callback> callbacks = new LinkedHashSet<>();
    private final ExecutionDeadline parent;
    private final Registration parentRegistration;
    private final ScheduledFuture<?> expiration;
    private volatile boolean cancelled;

    private ExecutionDeadline(long timeoutMillis, ExecutionDeadline parent) {
        long duration = TimeUnit.MILLISECONDS.toNanos(Math.max(1, Math.min(86_400_000, timeoutMillis)));
        long now = System.nanoTime();
        this.expiresAt = parent == null ? now + duration : Math.min(now + duration, parent.expiresAt);
        this.parent = parent;
        this.parentRegistration = parent == null ? () -> {} : parent.onCancellation(this::cancel);
        // 驱动阻塞期间不会主动 check，因此截止时间本身也必须触发资源取消。
        this.expiration = EXPIRATIONS.schedule(this::cancel,
                Math.max(0, expiresAt - System.nanoTime()), TimeUnit.NANOSECONDS);
    }

    private static ScheduledThreadPoolExecutor expirationExecutor() {
        var executor = new ScheduledThreadPoolExecutor(1, work -> {
            Thread thread = new Thread(work, "workflow-execution-deadline");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    /** 子调用只能收紧当前预算，不能重新开始一份更长的预算。 */
    public static ExecutionDeadline afterMillis(long timeoutMillis) {
        return new ExecutionDeadline(timeoutMillis, CURRENT.get());
    }

    public static ExecutionDeadline current() { return CURRENT.get(); }

    /** 显式将预算带入工作线程，退出时恢复先前作用域，避免线程池复用时串调用。 */
    public Registration attach() {
        ExecutionDeadline previous = CURRENT.get();
        CURRENT.set(this);
        return () -> {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        };
    }

    @Override public void check() {
        if (cancelled || (parent != null && parent.cancelled)
                || Thread.currentThread().isInterrupted() || System.nanoTime() >= expiresAt) {
            cancel();
            throw new ExceededException();
        }
    }

    @Override public long remainingMillis() {
        check();
        return Math.max(1, TimeUnit.NANOSECONDS.toMillis(Math.max(0, expiresAt - System.nanoTime())));
    }

    @Override public Registration onCancellation(Runnable action) {
        Callback callback = new Callback(action);
        boolean dispatch;
        synchronized (callbacks) {
            dispatch = cancelled;
            if (!dispatch) callbacks.add(callback);
        }
        if (dispatch) dispatch(callback);
        return () -> {
            callback.active.set(false);
            synchronized (callbacks) { callbacks.remove(callback); }
        };
    }

    /** 标记取消立即生效，回调异步执行；底层查询/连接超时是驱动取消失效时的第二道边界。 */
    public void cancel() {
        ArrayList<Callback> pending;
        synchronized (callbacks) {
            if (cancelled) return;
            cancelled = true;
            pending = new ArrayList<>(callbacks);
            callbacks.clear();
        }
        pending.forEach(this::dispatch);
    }

    private void dispatch(Callback callback) {
        try {
            CANCELLATIONS.execute(() -> {
                if (!callback.active.compareAndSet(true, false)) return;
                try { callback.action.run(); }
                catch (RuntimeException failure) {
                    org.slf4j.LoggerFactory.getLogger(ExecutionDeadline.class)
                            .warn("底层资源取消失败，等待其查询或连接超时", failure);
                }
            });
        } catch (RejectedExecutionException saturated) {
            org.slf4j.LoggerFactory.getLogger(ExecutionDeadline.class)
                    .warn("资源取消队列已满，等待底层查询或连接超时");
        }
    }

    /** 正常结束只解除父预算和资源引用，不取消已完成的操作。 */
    @Override public void close() {
        expiration.cancel(false);
        parentRegistration.close();
        synchronized (callbacks) {
            callbacks.forEach(callback -> callback.active.set(false));
            callbacks.clear();
        }
    }

    private record Callback(Runnable action, AtomicBoolean active) {
        private Callback(Runnable action) { this(action, new AtomicBoolean(true)); }
    }

    public static final class ExceededException extends RuntimeException {
        public ExceededException() { super("执行已取消或超过总超时"); }
    }
}
