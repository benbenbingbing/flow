package com.workflow.contracts.audit.context;

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

    /**
     * 初始化操作上下文持有者，保存构造参数供后续方法使用。
     */
    private OperationContextHolder() {
    }

    /**
     * 处理当前，并将结果传给后续步骤。
     *
     * @return 匹配的当前；未找到时为空
     */
    public static Optional<OperationContext> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /**
     * 处理打开，并将结果传给后续步骤。
     *
     * @param context 执行上下文，向后续打开步骤传递身份、配置或状态
     * @return 处理后的打开结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    public static Scope open(OperationContext context) {
        if (context == null) {
            throw new IllegalArgumentException("操作上下文不能为空");
        }
        OperationContext previous = CURRENT.get();
        CURRENT.set(context);
        return new Scope(previous);
    }

    /**
     * 处理{@code call}，并将结果传给后续步骤。
     *
     * @param context 执行上下文，向后续{@code call}步骤传递身份、配置或状态
     * @param action 动作标识，决定后续{@code call}采用的处理分支
     * @return 处理后的{@code call}结果，供调用方继续处理
     */
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

        /**
         * 初始化作用域，保存构造参数供后续方法使用。
         *
         * @param previous 上一项依赖，保存到当前对象供后续业务方法调用
         */
        private Scope(OperationContext previous) {
            this.previous = previous;
            this.opened = CURRENT.get();
        }

        /**
         * 处理关闭，并将结果传给后续步骤。
         */
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
