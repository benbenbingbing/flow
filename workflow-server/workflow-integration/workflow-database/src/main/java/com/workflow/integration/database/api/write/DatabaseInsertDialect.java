package com.workflow.integration.database.api.write;

import com.workflow.integration.database.api.sql.BoundSqlStatement;

import java.util.Map;
import java.util.List;

/** 单行插入语法及唯一冲突分类；生成结果由调用方执行，本接口没有连接或事务操作。 */
public interface DatabaseInsertDialect {
    /**
     * 表/列来自服务端固定结构；所有值均绑定，允许 NULL，不接受 SQL 表达式值。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 插入后的数据库{@code insert}方言结果，供调用方继续处理
     */
    BoundSqlStatement insert(String table, Map<String, ?> values);

    /**
     * 创建缺失行并锁定的执行计划；已有行不使用 initialValues 覆盖业务值。
     * keyColumns 必须对应即时主键/唯一约束，且其值非 NULL；调用方负责事务及锁顺序。
     * 不用于带额外写入副作用触发器的表，no-op UPDATE 可能触发该表的 UPDATE 触发器。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param initialValues 缺失行首次创建时的列值；已有行的业务值不会被覆盖
     * @param keyColumns 对应主键或唯一约束的列，后续用于锁定目标行
     * @return 处理后的行锁定结果，供调用方继续处理
     */
    DatabaseRowLockPlan rowLock(String table, Map<String, ?> initialValues, List<String> keyColumns);

    /**
     * 只识别明确的唯一约束错误，不把其他完整性约束、超时或死锁归为重复请求。
     *
     * @param sqlState 数据库 SQLState，后续用于识别唯一约束冲突
     * @param vendorCode 数据库供应商错误码，与 SQLState 一起判断唯一约束冲突
     * @return 唯一冲突条件成立时为 true，否则为 false
     */
    boolean isUniqueViolation(String sqlState, int vendorCode);
}
