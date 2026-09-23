package com.workflow.integration.database.api;

/**
 * 事务锁行的初始化和锁定语句。执行方须在同一业务事务内依次执行两条语句，
 * 锁定查询必须精确返回一行；不能根据初始化影响行数判断锁定成功。
 * recoverInsertConflict 表示 MERGE 首次插入可能竞争唯一键，需用保存点恢复后再锁定目标行。
 */
public record DatabaseRowLockPlan(
        BoundSqlStatement initialize, BoundSqlStatement lock, boolean recoverInsertConflict) { }
