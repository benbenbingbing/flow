package com.workflow.integration.database.api.runtime;

import java.util.List;

/**
 * 锁 SQL 及其调用约定。QUERY 的键绑定在第 1 位；INTEGER_CALL 在第 1 位注册整数返回值，
 * 第 2 位绑定键。rollbackOnRelease 表示使用专用持锁事务；事务插入锁的 releaseSql
 * 只检查本事务标记仍存在，实际释放始终通过回滚完成。
 * 本模型只描述方言，绝不创建会话、执行语句或触发回滚。
 *
 * @param acquireSql 获取锁的语句；事务插入锁要求恰好插入一行
 * @param releaseSql 释放锁或检查事务锁标记的语句，由调用方式决定执行规则
 * @param key 方言生成的稳定锁键，作为参数绑定，隔离不同业务操作
 * @param invocation JDBC调用方式及锁键的绑定类型
 * @param acquiredCode 明确表示获取成功的返回码
 * @param busyCode 明确表示其他会话持锁的返回码
 * @param releasedCode 表示释放成功或事务标记仍存在的返回码
 * @param rollbackOnRelease 是否通过回滚专用事务释放锁，不能用于业务事务
 * @param sessionInitSql 仅在独立持锁会话执行的初始化，不能应用到业务连接池
 * @param busyErrorCode 事务插入时唯一可视为竞争的厂商错误码；0表示不按异常判断竞争
 */
public record DatabaseLockPlan(
        String acquireSql, String releaseSql, Object key, Invocation invocation,
        int acquiredCode, int busyCode, int releasedCode, boolean rollbackOnRelease,
        List<String> sessionInitSql, int busyErrorCode) {
    public DatabaseLockPlan {
        sessionInitSql = List.copyOf(sessionInitSql);
    }

    /** 原生命名锁没有额外会话初始化，保留既有厂商的调用约定。 */
    public DatabaseLockPlan(String acquireSql, String releaseSql, Object key, Invocation invocation,
                            int acquiredCode, int busyCode, int releasedCode, boolean rollbackOnRelease) {
        this(acquireSql, releaseSql, key, invocation, acquiredCode, busyCode, releasedCode,
                rollbackOnRelease, List.of(), 0);
    }
    /**
     * 定义调用的可选值；调用方据此选择对应的处理分支。
     */
    public enum Invocation { TEXT_QUERY, BOOLEAN_QUERY, INTEGER_CALL, TEXT_TRANSACTION_INSERT }
}
