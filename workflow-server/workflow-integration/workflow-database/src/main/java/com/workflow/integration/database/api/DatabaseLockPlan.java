package com.workflow.integration.database.api;

/**
 * 锁 SQL 及其调用约定。QUERY 的键绑定在第 1 位；INTEGER_CALL 在第 1 位注册整数返回值，
 * 第 2 位绑定键。rollbackOnRelease 表示使用专用持锁事务，releaseSql 此时为空。
 * 本模型只描述方言，绝不创建会话、执行语句或触发回滚。
 */
public record DatabaseLockPlan(
        String acquireSql, String releaseSql, Object key, Invocation invocation,
        int acquiredCode, int busyCode, int releasedCode, boolean rollbackOnRelease) {
    public enum Invocation { TEXT_QUERY, BOOLEAN_QUERY, INTEGER_CALL }
}
