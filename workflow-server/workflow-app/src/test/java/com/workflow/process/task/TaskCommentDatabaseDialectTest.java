package com.workflow.process.task;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.workflow.process.task.application.model.TaskInboxQuery;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskMapper;
import com.workflow.process.task.infrastructure.persistence.mapper.TaskInboxMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 渲染生产 Mapper，确保任务备注在 Oracle/MySQL 下引用正确，备注内容仍使用参数绑定。 */
class TaskCommentDatabaseDialectTest {
    @Test
    void inboxAndCompletionQuoteReservedCommentWithTheConfiguredDatabase() {
        for (String database : new String[]{"MYSQL", "OCEANBASE_MYSQL", "ORACLE", "OCEANBASE_ORACLE", "POSTGRESQL"}) {
            var configuration = new MybatisConfiguration(); configuration.setDatabaseId(database);
            configuration.addMapper(TaskInboxMapper.class); configuration.addMapper(ProcessTaskMapper.class);
            String quoted = switch (database) {
                case "MYSQL", "OCEANBASE_MYSQL" -> "`comment`";
                case "ORACLE", "OCEANBASE_ORACLE" -> "\"COMMENT\"";
                default -> "\"comment\"";
            };
            var query = new TaskInboxQuery("reader", "todo", 1, 10, null, null, null, null, null);
            String select = configuration.getMappedStatement(TaskInboxMapper.class.getName() + ".selectPage")
                    .getBoundSql(Map.of("q", query)).getSql();
            assertTrue(select.contains("pt." + quoted), database + ": " + select);
            var parameters = Map.of("id", 1L, "status", "done", "action", "APPROVE", "comment", "O'Reilly approval", "duration", 10L);
            var update = configuration.getMappedStatement(ProcessTaskMapper.class.getName() + ".completeTask").getBoundSql(parameters);
            assertTrue(update.getSql().contains(quoted + " = ?"), database + ": " + update.getSql());
            assertFalse(update.getSql().contains("O'Reilly"));
            assertTrue(update.getParameterMappings().stream().anyMatch(mapping -> mapping.getProperty().equals("comment")));
        }
    }
    @Test
    void baseMapperAndLambdaMetadataQuoteOracleColumnsWithoutChangingMysql() throws Exception {
        for (String vendor : new String[]{"MYSQL", "OCEANBASE_MYSQL", "ORACLE", "OCEANBASE_ORACLE", "POSTGRESQL"}) {
            var database = new com.workflow.config.database.DatabaseMybatisConfiguration();
            var dialect = com.workflow.integration.database.api.DatabaseDialects.forVendor(
                    com.workflow.integration.database.api.DatabaseVendor.valueOf(vendor));
            var properties = new com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties();
            database.oracleIdentifierMappings(dialect).customize(properties);
            var configuration = new MybatisConfiguration(); configuration.setDatabaseId(vendor);
            configuration.setMapUnderscoreToCamelCase(true);
            com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils.setGlobalConfig(configuration, properties.getGlobalConfig());
            var factory = new com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean();
            factory.setDataSource(new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:comment_metadata", "sa", ""));
            factory.setGlobalConfig(properties.getGlobalConfig()); factory.setConfiguration(configuration);
            configuration.addMapper(ProcessTaskMapper.class); factory.getObject();
            var row = new com.workflow.process.task.infrastructure.persistence.record.ProcessTask();
            row.setId(1L); row.setComment("bound O'Reilly"); row.setProcessInstanceId("instance-1");
            String prefix = ProcessTaskMapper.class.getName() + ".";
            var wrapper = com.baomidou.mybatisplus.core.toolkit.Wrappers.<com.workflow.process.task.infrastructure.persistence.record.ProcessTask>lambdaQuery()
                    .select(com.workflow.process.task.infrastructure.persistence.record.ProcessTask::getComment)
                    .eq(com.workflow.process.task.infrastructure.persistence.record.ProcessTask::getId, 1L);
            var statements = java.util.List.of(
                    configuration.getMappedStatement(prefix + "selectById").getBoundSql(1L),
                    configuration.getMappedStatement(prefix + "selectList").getBoundSql(Map.of("ew", wrapper)),
                    configuration.getMappedStatement(prefix + "insert").getBoundSql(row),
                    configuration.getMappedStatement(prefix + "updateById").getBoundSql(Map.of("et", row)));
            boolean oracle = vendor.equals("ORACLE") || vendor.equals("OCEANBASE_ORACLE");
            for (var statement : statements) {
                String sql = statement.getSql();
                assertEquals(oracle, sql.contains("\"COMMENT\""), vendor + ": " + sql);
                assertFalse(sql.contains("bound O'Reilly"), sql);
                if (oracle) assertFalse(sql.matches("(?is).*\\bAS\\s+comment\\b.*"), sql);
            }
            if (!oracle) {
                assertNull(properties.getGlobalConfig().getDbConfig().getColumnFormat());
                assertNull(properties.getGlobalConfig().getDbConfig().getPropertyFormat());
            }
        }
    }

}
