package com.workflow.contracts.audit;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * 同步调用链中的操作上下文作用域。
 *
 * <p>作用域关闭时会恢复父上下文，避免线程池复用造成串线。提交后任务、消息和
 * 其他异步边界必须把 {@link OperationContext} 写入消息载荷后重新打开作用域。</p>
 */
public final class OperationContextHolder {

    private static final ThreadLocal<OperationContext> CURRENT =
            new ThreadLocal<>();

    private OperationContextHolder() {
    }

    public static Optional<OperationContext> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static Scope open(OperationContext context) {
        if (context == null) {
            throw new IllegalArgumentException("操作上下文不能为空");
        }
        OperationContext previous = CURRENT.get();
        CURRENT.set(context);
        return new Scope(previous);
    }

    public static <T> T call(
            OperationContext context,
            Supplier<T> action) {
        try (Scope ignored = open(context)) {
            return action.get();
        }
    }

    /**
     * 可关闭作用域；重复关闭不会覆盖后来打开的其他作用域。
     */
    public static final class Scope implements AutoCloseable {

        private final OperationContext previous;
        private final OperationContext opened;
        private boolean closed;

        private Scope(OperationContext previous) {
            this.previous = previous;
            this.opened = CURRENT.get();
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            // 非栈顶关闭意味着调用方破坏了作用域顺序。此时清理当前值比恢复
            // 错误父上下文更安全，避免后续审计被错误串联。
            if (CURRENT.get() != opened) {
                CURRENT.remove();
                return;
            }
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
