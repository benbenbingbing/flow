package com.workflow.config.database;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.workflow.integration.database.api.SchemaDdlDialect;
import com.workflow.integration.database.api.DatabaseQueryDialects;
import com.workflow.integration.database.api.DatabaseQueryDialect;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.MyBatisExceptionTranslator;
import org.mybatis.spring.SqlSessionTemplate;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties;
import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.ClobTypeHandler;
import org.apache.ibatis.type.NClobTypeHandler;
import org.apache.ibatis.type.StringTypeHandler;
import org.apache.ibatis.type.NStringTypeHandler;
import org.apache.ibatis.type.TypeHandler;
import org.springframework.jdbc.support.SQLExceptionTranslator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 数据库相关 MyBatis 装配，业务审计字段填充仍由应用层提供。 */
@Configuration(proxyBeanMethods = false)
public class DatabaseMybatisConfiguration {
    /**
     * 未声明类型的空参数使用标准 SQL NULL，避免 MyBatis 默认 OTHER 被 Oracle 等驱动拒绝。
     * 已声明的 JDBC 类型及数字布尔处理器仍优先；独立的 Flowable 工厂不受此配置影响。
     * 单独出现的“参数 IS NULL”等无法由 SQL 上下文推断类型的位置，仍须在 Mapper 声明类型。
     */
    @Bean
    public ConfigurationCustomizer nullParameterBindings() {
        return configuration -> configuration.setJdbcTypeForNull(JdbcType.NULL);
    }

    /**
     * 动态 Map 的属性类型是 Object，不能只注册 String 的 CLOB 处理器：驱动暴露厂商
     * Clob 类时 MyBatis 可能退回 getObject。为大文本的 Object 属性复用框架内置的
     * String 处理器，业务层始终取得字符串；普通 Object、JSON 和二进制列不受影响。
     */
    @Bean
    public ConfigurationCustomizer largeTextBindings() {
        return configuration -> {
            var registry = configuration.getTypeHandlerRegistry();
            java.util.Map<JdbcType, TypeHandler<String>> handlers = java.util.Map.of(
                    JdbcType.CLOB, new ClobTypeHandler(),
                    JdbcType.NCLOB, new NClobTypeHandler(),
                    JdbcType.LONGVARCHAR, new StringTypeHandler(),
                    JdbcType.LONGNVARCHAR, new NStringTypeHandler());
            handlers.forEach((type, handler) -> {
                registry.register(String.class, type, handler);
                registry.register(Object.class, type, handler);
            });
        };
    }

    /**
     * 在解析业务 Mapper 之前注册数字布尔绑定，覆盖默认及显式 BOOLEAN/BIT 映射。
     * 不改写连接或 Flowable 的独立配置，避免把第三方原生布尔列误当成本系统的数值列。
     */
    @Bean
    public ConfigurationCustomizer numericBooleanBindings() {
        return configuration -> {
            var registry = configuration.getTypeHandlerRegistry();
            var handler = new NumericBooleanTypeHandler();
            for (Class<Boolean> type : java.util.List.of(Boolean.class, boolean.class)) {
                registry.register(type, handler);
                registry.register(type, JdbcType.BOOLEAN, handler);
                registry.register(type, JdbcType.BIT, handler);
            }
        };
    }

    /** Mapper 与共享 JdbcTemplate 使用同一产品规则，避免厂商产品名或默认错误表漏掉国产库。 */
    @Bean
    @ConditionalOnMissingBean(SqlSessionTemplate.class)
    public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory, MybatisPlusProperties properties,
                                                 SQLExceptionTranslator translator) {
        var executor = properties.getExecutorType() == null
                ? factory.getConfiguration().getDefaultExecutorType() : properties.getExecutorType();
        return new SqlSessionTemplate(factory, executor, new MyBatisExceptionTranslator(() -> translator, false));
    }

    /** 向普通 JDBC 业务服务暴露同一产品的纯查询方言，执行仍留在调用方。 */
    @Bean
    @ConditionalOnMissingBean(DatabaseQueryDialect.class)
    public DatabaseQueryDialect databaseQueryDialect(SchemaDdlDialect dialect) {
        return DatabaseQueryDialects.forVendor(dialect.vendor());
    }

    /** databaseId 跟随显式产品配置，不能仅凭驱动产品名混淆 OceanBase 的租户模式。 */
    @Bean
    @ConditionalOnMissingBean(DatabaseIdProvider.class)
    public DatabaseIdProvider databaseIdProvider(SchemaDdlDialect dialect) {
        return dataSource -> dialect.vendor().name();
    }

    /** 使用与 DDL 相同的产品配置，尤其不能仅按 JDBC 驱动把 OB Oracle 当作 MySQL。 */
    @Bean
    @ConditionalOnMissingBean(MybatisPlusInterceptor.class)
    public MybatisPlusInterceptor mybatisPlusInterceptor(SchemaDdlDialect dialect) {
        DbType dbType = switch (dialect.vendor()) {
            case MYSQL, OCEANBASE_MYSQL -> DbType.MYSQL;
            case POSTGRESQL -> DbType.POSTGRE_SQL;
            case KINGBASE -> DbType.KINGBASE_ES;
            case DM -> DbType.DM;
            case ORACLE, OCEANBASE_ORACLE -> DbType.ORACLE_12C;
        };
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(dbType));
        return interceptor;
    }
}
