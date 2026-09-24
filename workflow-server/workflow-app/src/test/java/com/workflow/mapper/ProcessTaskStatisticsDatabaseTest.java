package com.workflow.mapper;

import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskMapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 执行 Mapper 的真实聚合和映射，防止 NULL 分母、身份别名及逻辑删除改变统计口径。 */
class ProcessTaskStatisticsDatabaseTest {
    private EmbeddedDatabase database;
    private SqlSessionFactory factory;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true).build();
        var jdbc = new JdbcTemplate(database);
        jdbc.execute("CREATE TABLE sys_user (id VARCHAR(64), username VARCHAR(64), deleted INT)");
        jdbc.execute("CREATE TABLE process_task (assignee_id VARCHAR(64), status VARCHAR(16), deleted INT, duration BIGINT)");
        jdbc.update("INSERT INTO sys_user VALUES ('user-1', 'alice', 0)");
        jdbc.update("INSERT INTO process_task VALUES ('user-1', 'done', 0, 3600000), "
                + "('alice', 'done', 0, NULL), ('alice', 'todo', 0, 7200000), "
                + "('alice', 'done', 1, 7200000), ('bob', 'done', 0, 7200000)");
        var configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), database));
        configuration.setDatabaseId("MYSQL");
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(ProcessTaskMapper.class);
        factory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    @ParameterizedTest
    @ValueSource(strings = {"alice", "user-1"})
    void includesNullDurationsInDenominatorAndMatchesBothIdentities(String identity) {
        try (var session = factory.openSession()) {
            var aggregate = session.getMapper(ProcessTaskMapper.class).aggregateDoneByUser(identity);
            assertEquals(2L, aggregate.getTaskCount());
            assertEquals(0, aggregate.getDurationTotal().compareTo(new java.math.BigDecimal("3600000")));
            assertEquals(0.5, aggregate.averageHours());
        }
    }

    @Test
    void emptyHistoryReturnsZero() {
        try (var session = factory.openSession()) {
            var aggregate = session.getMapper(ProcessTaskMapper.class).aggregateDoneByUser("nobody");
            assertEquals(0L, aggregate.getTaskCount());
            assertEquals(0.0, aggregate.averageHours());
        }
    }
}
