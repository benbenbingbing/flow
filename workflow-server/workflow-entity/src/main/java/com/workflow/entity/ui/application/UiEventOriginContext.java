package com.workflow.entity.ui.application;

import com.workflow.entity.ui.api.request.UiEventExecuteRequest;

/**
 * 已鉴权按钮事件的线程内来源。Provider 只能在事件执行期间读取此上下文，
 * 从而避免把接口 input 中可由映射改写的字段当作按钮或发布身份。
 */
public final class UiEventOriginContext implements AutoCloseable {
    private static final ThreadLocal<Origin> CURRENT = new ThreadLocal<>();
    private final Origin previous;

    private UiEventOriginContext(Origin origin) {
        previous = CURRENT.get();
        CURRENT.set(origin);
    }

    /** 嵌套事件按栈恢复来源，调用方必须使用 try-with-resources。 */
    public static UiEventOriginContext open(UiEventExecuteRequest request,
            UiEventBindingService.ResolvedEventChain chain) {
        return new UiEventOriginContext(new Origin(request, chain));
    }

    /** 没有真实按钮事件时返回 null；Excel Provider 必须拒绝这种调用。 */
    public static Origin current() { return CURRENT.get(); }

    @Override
    public void close() {
        if (previous == null) CURRENT.remove();
        else CURRENT.set(previous);
    }

    public record Origin(UiEventExecuteRequest request,
                         UiEventBindingService.ResolvedEventChain chain) { }
}
