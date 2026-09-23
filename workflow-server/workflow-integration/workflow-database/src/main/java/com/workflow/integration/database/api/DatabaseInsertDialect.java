package com.workflow.integration.database.api;

import java.util.Map;
import java.util.List;

/** 单行插入语法及唯一冲突分类；生成结果由调用方执行，本接口没有连接或事务操作。 */
public interface DatabaseInsertDialect {
    /** 表/列来自服务端固定结构；所有值均绑定，允许 NULL，不接受 SQL 表达式值。 */
    BoundSqlStatement insert(String table, Map<String, ?> values);

    /**
     * 创建缺失行并锁定的执行计划；已有行不使用 initialValues 覆盖业务值。
     * keyColumns 必须对应即时主键/唯一约束，且其值非 NULL；调用方负责事务及锁顺序。
     * 不用于带额外写入副作用触发器的表，no-op UPDATE 可能触发该表的 UPDATE 触发器。
     */
    DatabaseRowLockPlan rowLock(String table, Map<String, ?> initialValues, List<String> keyColumns);

    /** 只识别明确的唯一约束错误，不把其他完整性约束、超时或死锁归为重复请求。 */
    boolean isUniqueViolation(String sqlState, int vendorCode);
}
