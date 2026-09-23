package com.workflow.integration.database.api;

/** 数据库时钟、结构读取及会话锁的语法标准；只返回描述，不持有连接或执行 SQL。 */
public interface DatabaseRuntimeDialect {
    DatabaseVendor vendor();

    /** 查询当前数据库 UTC 时间，返回单列无时区时间值。 */
    String utcNowSql();

    /** DML 中的数据库当前 UTC 时间（无时区值）；不得使用事务开始时间代替实时租约检查。 */
    String utcTimestampExpression();

    /** 当前数据库 UTC 时间加秒数；seconds 只接受整数、命名/MyBatis 绑定或单个 JDBC 问号。 */
    String utcAfterSeconds(String seconds);

    /** 数据库会话时区中的当前时间，供原有本地墙钟列使用，不能替换 UTC 租约时钟。 */
    String currentTimestampExpression();

    /** 数据库会话时间加秒数，参数限制同 utcAfterSeconds；秒数占位符只出现一次。 */
    String currentAfterSeconds(String seconds);

    /** 统计行数查询；唯一绑定参数为 physicalName 转换后的表名。 */
    String estimatedRowsSql();

    /** 将应用内部规范名称转换为物理名称，不改变业务值。 */
    String physicalName(String name);

    /** 元数据读取的目录范围；调用方负责读取连接属性并拒绝未知范围。 */
    MetadataScope metadataScope();

    /** 生成互斥锁调用描述；锁名兼容既有发布器，调用方负责连接生命周期及事务。 */
    DatabaseLockPlan lockPlan(String namespace, String key);

    enum MetadataScope { CATALOG_ONLY, CURRENT_SCHEMA, CURRENT_SCHEMA_OR_USER }
}
