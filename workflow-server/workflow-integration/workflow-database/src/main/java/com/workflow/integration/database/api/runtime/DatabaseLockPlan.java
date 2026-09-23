package com.workflow.integration.database.api.runtime;

/**
 * 锁 SQL 及其调用约定。QUERY 的键绑定在第 1 位；INTEGER_CALL 在第 1 位注册整数返回值，
 * 第 2 位绑定键。rollbackOnRelease 表示使用专用持锁事务，releaseSql 此时为空。
 * 本模型只描述方言，绝不创建会话、执行语句或触发回滚。
 *
 * @param acquireSql 获取SQL，保存在对象中供后续校验、查询或展示
 * @param releaseSql 发布版本SQL，保存在对象中供后续校验、查询或展示
 * @param key 键，后续用于授权校验、关联或幂等去重
 * @param invocation 调用，保存在对象中供后续校验、查询或展示
 * @param acquiredCode {@code acquired}编码，后续用于处理数据库锁定方案时定位或关联目标
 * @param busyCode {@code busy}编码，后续用于处理数据库锁定方案时定位或关联目标
 * @param releasedCode {@code released}编码，后续用于处理数据库锁定方案时定位或关联目标
 * @param rollbackOnRelease 回滚发布版本，保存在对象中供后续校验、查询或展示
 */
public record DatabaseLockPlan(
        String acquireSql, String releaseSql, Object key, Invocation invocation,
        int acquiredCode, int busyCode, int releasedCode, boolean rollbackOnRelease) {
    /**
     * 定义调用的可选值；调用方据此选择对应的处理分支。
     */
    public enum Invocation { TEXT_QUERY, BOOLEAN_QUERY, INTEGER_CALL }
}
