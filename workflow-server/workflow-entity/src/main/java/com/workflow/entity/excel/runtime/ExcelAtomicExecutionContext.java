package com.workflow.entity.excel.runtime;

import java.util.Map;

/**
 * Excel 原子导入和无副作用预检的服务端执行约束。
 * 资格检查后接口仍可能被管理员修改，因此实际调用也必须检查同一份内存定义；
 * 该上下文只限制导入，不改变普通表单接口的行为。
 */
public final class ExcelAtomicExecutionContext implements AutoCloseable {
    private static final ThreadLocal<Boolean> CURRENT = new ThreadLocal<>();
    private final Boolean previous;

    private ExcelAtomicExecutionContext(boolean enabled) {
        previous = CURRENT.get();
        if (enabled) CURRENT.set(Boolean.TRUE); else CURRENT.remove();
    }

    /** 必须配合 try-with-resources，防止请求线程或 Provider 工作线程遗留限制。 */
    public static ExcelAtomicExecutionContext open() { return new ExcelAtomicExecutionContext(true); }

    /** 显式传播调用方约束到受控工作线程；不使用可泄露到线程池的 InheritableThreadLocal。 */
    public static ExcelAtomicExecutionContext openIf(boolean enabled) { return new ExcelAtomicExecutionContext(enabled); }

    public static boolean active() { return Boolean.TRUE.equals(CURRENT.get()); }

    /** 在实际 Provider 调用之前检验接口方向及失败策略，不能被缓存或回退绕过。 */
    public static void requireRead(String kind, Map<String, Object> policy) {
        if (!active()) return;
        if (!"READ".equalsIgnoreCase(kind)
                || !"FAIL".equalsIgnoreCase(String.valueOf(policy.getOrDefault("failurePolicy", "FAIL")))) {
            throw new IllegalArgumentException("原子Excel导入仅支持READ且失败即终止的接口");
        }
    }

    @Override public void close() {
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }
}
