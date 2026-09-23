package com.workflow.integration.database.api.runtime;

import com.workflow.integration.database.api.DatabaseVendor;

/** 数据库时钟、结构读取及会话锁的语法标准；只返回描述，不持有连接或执行 SQL。 */
public interface DatabaseRuntimeDialect {
    /**
     * 处理供应商，并将结果传给后续步骤。
     *
     * @return 处理后的供应商结果，供调用方继续处理
     */
    DatabaseVendor vendor();

    /**
     * 查询当前数据库 UTC 时间，返回单列无时区时间值。
     *
     * @return 处理后的UTC当前时间SQL文本，供调用方比较或展示
     */
    String utcNowSql();

    /**
     * DML 中的数据库当前 UTC 时间（无时区值）；不得使用事务开始时间代替实时租约检查。
     *
     * @return 处理后的UTC时间戳表达式文本，供调用方比较或展示
     */
    String utcTimestampExpression();

    /**
     * 当前数据库 UTC 时间加秒数；seconds 只接受整数、命名/MyBatis 绑定或单个 JDBC 问号。
     *
     * @param seconds 秒数，供本方法处理UTC之后秒数时使用
     * @return 处理后的UTC之后秒数文本，供调用方比较或展示
     */
    String utcAfterSeconds(String seconds);

    /**
     * 数据库会话时区中的当前时间，供原有本地墙钟列使用，不能替换 UTC 租约时钟。
     *
     * @return 处理后的当前时间戳表达式文本，供调用方比较或展示
     */
    String currentTimestampExpression();

    /**
     * 数据库会话时间加秒数，参数限制同 utcAfterSeconds；秒数占位符只出现一次。
     *
     * @param seconds 秒数，供本方法处理当前之后秒数时使用
     * @return 处理后的当前之后秒数文本，供调用方比较或展示
     */
    String currentAfterSeconds(String seconds);

    /**
     * 统计行数查询；唯一绑定参数为 physicalName 转换后的表名。
     *
     * @return 处理后的{@code estimated}行SQL文本，供调用方比较或展示
     */
    String estimatedRowsSql();

    /**
     * 将应用内部规范名称转换为物理名称，不改变业务值。
     *
     * @param name 名称，后续用于处理物理名称时匹配或展示
     * @return 处理后的物理名称文本，供调用方比较或展示
     */
    String physicalName(String name);

    /**
     * 元数据读取的目录范围；调用方负责读取连接属性并拒绝未知范围。
     *
     * @return 处理后的元数据作用域结果，供调用方继续处理
     */
    MetadataScope metadataScope();

    /**
     * 生成互斥锁调用描述；锁名兼容既有发布器，调用方负责连接生命周期及事务。
     *
     * @param namespace 命名空间，供本方法锁定方案时使用
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @return 锁定后的方案结果，供调用方继续处理
     */
    DatabaseLockPlan lockPlan(String namespace, String key);

    /**
     * 定义元数据作用域的可选值；调用方据此选择对应的处理分支。
     */
    enum MetadataScope { CATALOG_ONLY, CURRENT_SCHEMA, CURRENT_SCHEMA_OR_USER }
}
