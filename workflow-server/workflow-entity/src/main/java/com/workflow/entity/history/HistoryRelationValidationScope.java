package com.workflow.entity.history;

/**
 * 历史组按父后子顺序创建，必填关系需等整组写完再核验。
 * 仅实体导入服务能够开启；其注册的事务提交前校验保证缺少子记录时整组回滚。
 * 客户端字段不能建立此作用域，关闭时恢复线程状态。
 */
public final class HistoryRelationValidationScope implements AutoCloseable {
    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<>();
    private final Boolean previous = ACTIVE.get();
    private HistoryRelationValidationScope() { ACTIVE.set(true); }
    static HistoryRelationValidationScope open() { return new HistoryRelationValidationScope(); }
    public static boolean deferred() { return Boolean.TRUE.equals(ACTIVE.get()); }
    public void close() { if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous); }
}
