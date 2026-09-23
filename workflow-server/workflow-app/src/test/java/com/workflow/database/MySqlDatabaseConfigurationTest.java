package com.workflow.database;

import com.workflow.config.database.DatabaseConfiguration;
import com.workflow.config.database.DatabaseDataSourceConfiguration;
import com.workflow.config.database.DatabaseMybatisConfiguration;
import com.workflow.config.database.NumericBooleanTypeHandler;
import com.workflow.core.database.InitializedDriverDataSource;

import com.workflow.core.database.port.DatabaseConnections;
import com.workflow.integration.database.api.DatabaseQueryDialect;
import com.workflow.integration.database.api.DatabaseQueryDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import com.zaxxer.hikari.HikariDataSource;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MySqlDatabaseConfigurationTest {
    private ApplicationContextRunner context() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class, com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class))
                .withUserConfiguration(DatabaseConfiguration.class, DatabaseDataSourceConfiguration.class, DatabaseMybatisConfiguration.class)
                .withPropertyValues("spring.datasource.url=jdbc:mysql://localhost/workflow", "spring.datasource.username=test",
                        "spring.datasource.password=test", "spring.datasource.hikari.maximum-pool-size=3",
                        "workflow.schema-publisher.datasource.url=jdbc:mysql://localhost/workflow",
                        "workflow.schema-publisher.datasource.username=schema_test", "workflow.schema-publisher.datasource.password=test");
    }
    @Test
    void dynamicLargeTextReusesFrameworkStringHandlersWithoutChangingScalarMappings() {
        context().run(context -> {
            assertThat(context).hasNotFailed();
            var registry = context.getBean(org.apache.ibatis.session.SqlSessionFactory.class)
                    .getConfiguration().getTypeHandlerRegistry();
            var expected = java.util.Map.of(
                    org.apache.ibatis.type.JdbcType.CLOB, org.apache.ibatis.type.ClobTypeHandler.class,
                    org.apache.ibatis.type.JdbcType.NCLOB, org.apache.ibatis.type.NClobTypeHandler.class,
                    org.apache.ibatis.type.JdbcType.LONGVARCHAR, org.apache.ibatis.type.StringTypeHandler.class,
                    org.apache.ibatis.type.JdbcType.LONGNVARCHAR, org.apache.ibatis.type.NStringTypeHandler.class);
            expected.forEach((jdbcType, handlerType) -> {
                assertThat(registry.getTypeHandler(String.class, jdbcType)).isExactlyInstanceOf(handlerType);
                assertThat(registry.getTypeHandler(Object.class, jdbcType))
                        .isSameAs(registry.getTypeHandler(String.class, jdbcType));
            });
            assertThat(registry.getTypeHandler(Object.class)).isInstanceOf(org.apache.ibatis.type.UnknownTypeHandler.class);
            assertThat(registry.getTypeHandler(String.class)).isExactlyInstanceOf(org.apache.ibatis.type.StringTypeHandler.class);
            assertThat(registry.getTypeHandler(byte[].class)).isInstanceOf(org.apache.ibatis.type.ByteArrayTypeHandler.class);
        });
    }

    @Test
    void nullBindingDefaultIsInstalledOnlyInTheApplicationFactory() {
        context().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(org.apache.ibatis.session.SqlSessionFactory.class)
                    .getConfiguration().getJdbcTypeForNull()).isEqualTo(org.apache.ibatis.type.JdbcType.NULL);
            assertThat(new org.apache.ibatis.session.Configuration().getJdbcTypeForNull())
                    .isEqualTo(org.apache.ibatis.type.JdbcType.OTHER);
        });
    }

    @Test
    void applicationBooleanBindingIsInstalledBeforeMappersWithoutChangingIndependentFactories() {
        context().run(context -> {
            assertThat(context).hasNotFailed();
            var registry = context.getBean(org.apache.ibatis.session.SqlSessionFactory.class)
                    .getConfiguration().getTypeHandlerRegistry();
            for (Class<Boolean> type : java.util.List.of(Boolean.class, boolean.class)) {
                assertThat(registry.getTypeHandler(type)).isInstanceOf(NumericBooleanTypeHandler.class);
                for (var jdbcType : java.util.List.of(org.apache.ibatis.type.JdbcType.BOOLEAN,
                        org.apache.ibatis.type.JdbcType.BIT, org.apache.ibatis.type.JdbcType.SMALLINT,
                        org.apache.ibatis.type.JdbcType.NUMERIC)) {
                    assertThat(registry.getTypeHandler(type, jdbcType)).isInstanceOf(NumericBooleanTypeHandler.class);
                }
            }
            assertThat(new org.apache.ibatis.session.Configuration().getTypeHandlerRegistry()
                    .getTypeHandler(Boolean.class)).isNotInstanceOf(NumericBooleanTypeHandler.class);
        });
    }

    @Test
    void sharedJdbcAndMapperTemplatesUseTheSameErrorRulesAndKeepBootOptions() {
        context().withPropertyValues("spring.jdbc.template.fetch-size=17", "spring.jdbc.template.query-timeout=3s",
                "mybatis-plus.executor-type=REUSE").run(context -> {
            assertThat(context).hasNotFailed();
            var jdbc = context.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
            assertThat(jdbc.getFetchSize()).isEqualTo(17);
            assertThat(jdbc.getQueryTimeout()).isEqualTo(3);
            assertThat(jdbc.getExceptionTranslator()).isSameAs(context.getBean(org.springframework.jdbc.support.SQLExceptionTranslator.class));
            var session = context.getBean(org.mybatis.spring.SqlSessionTemplate.class);
            assertThat(session.getExecutorType()).isEqualTo(org.apache.ibatis.session.ExecutorType.REUSE);
            var sql = new java.sql.SQLException("arbitrary text", "23000", 1062);
            assertThat(session.getPersistenceExceptionTranslator().translateExceptionIfPossible(
                    new org.apache.ibatis.exceptions.PersistenceException(sql)))
                    .isInstanceOf(org.springframework.dao.DuplicateKeyException.class).hasCause(sql);
        });
    }

    @Test
    void applicationPoolAndDedicatedConnectionsShareVendorInitialization() {
        context().run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(DataSource.class);
            var pool = (HikariDataSource) context.getBean(DataSource.class);
            assertThat(pool.getMaximumPoolSize()).isEqualTo(3);
            assertThat(pool.getDriverClassName()).isEqualTo("com.mysql.cj.jdbc.Driver");
            assertThat(pool.getConnectionInitSql()).isEqualTo("SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci");
            var connections = context.getBean(DatabaseConnections.class);
            assertThat(connections.application()).isInstanceOf(InitializedDriverDataSource.class).isNotSameAs(pool);
            assertThat(connections.schema()).isInstanceOf(InitializedDriverDataSource.class).isNotSameAs(pool);
            assertThat(context).hasSingleBean(MybatisPlusInterceptor.class);
            var pagination = (PaginationInnerInterceptor) context.getBean(MybatisPlusInterceptor.class).getInterceptors().get(0);
            assertThat(pagination.getDbType()).isEqualTo(DbType.MYSQL);
            assertThat(context.getBean(DatabaseQueryDialect.class))
                    .isSameAs(DatabaseQueryDialects.forVendor(DatabaseVendor.MYSQL));
            try {
                assertThat(context.getBean(DatabaseIdProvider.class).getDatabaseId(pool)).isEqualTo("MYSQL");
            } catch (java.sql.SQLException error) { throw new AssertionError(error); }
        });
    }
    @Test
    void queryDialectRejectsMissingConfigurationAndUnsafePaginationTokens() {
        assertThrows(IllegalStateException.class, () -> DatabaseQueryDialects.forDatabaseId(null));
        assertThrows(IllegalArgumentException.class, () -> DatabaseQueryDialects.forDatabaseId("unknown"));
        var dialect = DatabaseQueryDialects.forVendor(DatabaseVendor.MYSQL);
        assertThrows(IllegalArgumentException.class, () -> dialect.paginate("SELECT id FROM sample", "?", "?"));
        assertThrows(IllegalArgumentException.class, () -> dialect.paginate("SELECT id FROM sample", "0", "1; DROP TABLE sample"));
        assertThrows(IllegalArgumentException.class, () -> dialect.quoteIdentifier("id` OR 1=1"));
        assertThrows(IllegalArgumentException.class, () -> dialect.quoteAlias("id` OR 1=1"));
    }
    @Test
    void explicitConnectionInitializationOverridesTheMysqlDefault() {
        context().withPropertyValues("spring.datasource.hikari.connection-init-sql=SET time_zone = '+00:00'").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(((HikariDataSource) context.getBean(DataSource.class)).getConnectionInitSql()).isEqualTo("SET time_zone = '+00:00'");
        });
    }
    @Test
    void refusesDedicatedDdlUrlFromAnotherDatabaseFamilyAndMissingCredentials() {
        context().withPropertyValues("workflow.schema-publisher.datasource.url=jdbc:postgresql://localhost/workflow").run(context -> {
            assertThat(context).hasNotFailed();
            assertThrows(IllegalArgumentException.class, () -> context.getBean(DatabaseConnections.class).schema());
        });
        context().withPropertyValues("workflow.schema-publisher.datasource.password=").run(context -> {
            assertThrows(IllegalStateException.class, () -> context.getBean(DatabaseConnections.class).schema());
        });
    }
}
