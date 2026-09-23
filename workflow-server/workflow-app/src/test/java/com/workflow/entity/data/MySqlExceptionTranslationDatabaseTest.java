package com.workflow.entity.data;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.workflow.config.GlobalExceptionHandler;
import com.workflow.config.database.DatabaseConfiguration;
import com.workflow.config.database.DatabaseMybatisConfiguration;
import com.workflow.core.database.DatabaseExceptionClassifier;
import com.workflow.core.database.JdbcWriteAttempt;
import com.workflow.core.database.port.DatabaseConnections;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.application.EntityUniqueValueService;
import com.workflow.integration.database.api.*;
import com.workflow.entity.data.MySqlRuntimePaginationDatabaseTest.Fixture;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.sql.DataSource;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.dao.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import static com.workflow.entity.data.MySqlIdempotentInsertDatabaseTest.concurrent;
import static com.workflow.integration.database.api.DatabaseErrorKind.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 使用真实 Boot JDBC/MyBatis 装配和随机表，验证驱动错误到事务及 HTTP 响应的完整路径。 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlExceptionTranslationDatabaseTest {
    public interface ProbeMapper {
        @Insert("INSERT INTO db_error_child (id, required_value, parent_id, score) VALUES (#{id}, #{value}, #{parent}, #{score})")
        int insert(@Param("id") int id, @Param("value") String value, @Param("parent") int parent, @Param("score") int score);
        @Insert("INSERT INTO db_error_child (id, parent_id, score) VALUES (2, 1, 1)")
        int missingDefault();
    }

    @Test void jdbcAndMapperProduceTheSameKindsForRealConstraintFailures() {
        database(context -> {
            var jdbc = context.getBean(JdbcTemplate.class);
            var errors = context.getBean(DatabaseExceptionClassifier.class);
            var mapper = context.getBean(SqlSessionTemplate.class).getMapper(ProbeMapper.class);
            var modes = jdbc.queryForObject("SELECT @@session.sql_mode", String.class);
            assertTrue(modes.contains("STRICT_TRANS_TABLES") || modes.contains("STRICT_ALL_TABLES"), "测试要求严格写入模式");
            Object[][] cases = {{1, "ok", 1, 1, UNIQUE}, {2, null, 1, 1, NOT_NULL},
                    {2, "too-long", 1, 1, VALUE_TOO_LONG}, {2, "ok", 99, 1, FOREIGN_KEY},
                    {2, "ok", 1, -1, CHECK}, {2, "ok", 1, 1000, NUMERIC_RANGE}};
            for (Object[] row : cases) {
                var expected = (DatabaseErrorKind) row[4];
                List<Runnable> attempts = List.of(
                        () -> jdbc.update("INSERT INTO db_error_child (id,required_value,parent_id,score) VALUES (?,?,?,?)",
                                row[0], row[1], row[2], row[3]),
                        () -> mapper.insert((int) row[0], (String) row[1], (int) row[2], (int) row[3]));
                for (Runnable attempt : attempts) {
                    var failure = assertThrows(DataIntegrityViolationException.class, attempt::run);
                    assertEquals(expected, errors.classify(failure));
                    assertEquals(expected == UNIQUE, failure instanceof DuplicateKeyException);
                }
            }
            assertEquals(MISSING_DEFAULT, errors.classify(assertThrows(DataIntegrityViolationException.class, mapper::missingDefault)));
            assertEquals(MISSING_DEFAULT, errors.classify(assertThrows(DataIntegrityViolationException.class,
                    () -> jdbc.update("INSERT INTO db_error_child (id,parent_id,score) VALUES (2,1,1)"))));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM db_error_child", Integer.class));
        });
    }

    @Test void frameworkTranslationPreservesSavepointAndOuterRollback() {
        database(context -> {
            var jdbc = context.getBean(JdbcTemplate.class);
            var tx = transaction(context);
            var mapper = context.getBean(SqlSessionTemplate.class).getMapper(ProbeMapper.class);
            var attempt = context.getBean(JdbcWriteAttempt.class);
            tx.executeWithoutResult(status -> {
                mapper.insert(2, "ok", 1, 1);
                assertThrows(DuplicateKeyException.class, () -> attempt.execute(() -> mapper.insert(1, "ok", 1, 1)));
                mapper.insert(3, "ok", 1, 1);
            });
            assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM db_error_child", Integer.class));
            tx.executeWithoutResult(status -> {
                mapper.insert(4, "ok", 1, 1);
                assertThrows(DuplicateKeyException.class, () -> attempt.execute(() -> mapper.insert(1, "ok", 1, 1)));
                status.setRollbackOnly();
            });
            assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM db_error_child", Integer.class));
        });
    }

    @Test void existingBusinessConflictStillRollsBackEarlierReservationWork() throws Exception {
        try (var f = new Fixture()) {
            MySqlRemainingConflictDatabaseTest.table(f, "entity_unique_value", "V050__entity_schema_operation_and_unique_value.sql");
            database(f, context -> {
                var jdbc = context.getBean(JdbcTemplate.class);
                var service = new EntityUniqueValueService(jdbc); var tx = transaction(context);
                tx.executeWithoutResult(status -> service.replace("entity", "original", Map.of("name", "claimed")));
                var failure = assertThrows(BusinessConflictException.class, () -> tx.executeWithoutResult(status -> {
                    service.replace("entity", "second", Map.of("name", "temporary"));
                    service.replace("entity", "second", Map.of("name", "claimed"));
                }));
                assertEquals("ENTITY_UNIQUE_VALUE_CONFLICT", failure.getErrorCode());
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM entity_unique_value", Integer.class));
                assertEquals("original", jdbc.queryForObject("SELECT record_id FROM entity_unique_value", String.class));
            });
        }
    }

    @Test void mvcUsesClassifiedDatabaseFailureInsteadOfSqlDetails() {
        database(context -> {
            var mapper = context.getBean(SqlSessionTemplate.class).getMapper(ProbeMapper.class);
            var mvc = MockMvcBuilders.standaloneSetup(new ProbeController(mapper)).setControllerAdvice(
                    new GlobalExceptionHandler(context.getBean(DatabaseExceptionClassifier.class))).build();
            try {
                mvc.perform(get("/db-error-probe"))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                        .andExpect(jsonPath("$.message").value("字段内容过长，请缩短后重试"));
            } catch (Exception e) { throw new AssertionError(e); }
        });
    }

    @Test void realLockTimeoutIsNotUnique() {
        database(context -> {
            var jdbc = context.getBean(JdbcTemplate.class); var tx = transaction(context);
            var errors = context.getBean(DatabaseExceptionClassifier.class);
            tx.executeWithoutResult(status -> {
                jdbc.queryForObject("SELECT id FROM db_error_child WHERE id=1 FOR UPDATE", Integer.class);
                try {
                    var results = concurrent(1, ignored -> transaction(context).execute(inner -> {
                        jdbc.execute("SET SESSION innodb_lock_wait_timeout=1");
                        var failure = assertThrows(CannotAcquireLockException.class,
                                () -> jdbc.update("UPDATE db_error_child SET score=2 WHERE id=1"));
                        inner.setRollbackOnly(); return errors.classify(failure);
                    }));
                    assertEquals(List.of(LOCK_TIMEOUT), results);
                } catch (Exception e) { throw new AssertionError(e); }
                status.setRollbackOnly();
            });
        });
    }

    @Test void realDeadlockAbortsOneTransactionAndNeverBecomesDuplicate() {
        database(context -> {
            var jdbc = context.getBean(JdbcTemplate.class); var errors = context.getBean(DatabaseExceptionClassifier.class);
            jdbc.update("INSERT INTO db_error_child VALUES (2, 'ok', 1, 1)");
            var barrier = new CyclicBarrier(2);
            try {
                var outcomes = concurrent(2, worker -> {
                    try {
                        transaction(context).executeWithoutResult(status -> {
                            jdbc.update("UPDATE db_error_child SET score=score+1 WHERE id=?", worker + 1);
                            try { barrier.await(10, TimeUnit.SECONDS); }
                            catch (Exception e) { throw new AssertionError(e); }
                            jdbc.update("UPDATE db_error_child SET score=score+1 WHERE id=?", 2 - worker);
                        });
                        return "committed";
                    } catch (PessimisticLockingFailureException failure) {
                        assertEquals(DEADLOCK, errors.classify(failure)); return "deadlock";
                    }
                });
                assertEquals(1, outcomes.stream().filter("deadlock"::equals).count());
                assertEquals(1, outcomes.stream().filter("committed"::equals).count());
                assertEquals(4, jdbc.queryForObject("SELECT SUM(score) FROM db_error_child", Integer.class));
            } catch (Exception e) { throw new AssertionError(e); }
        });
    }

    /** 仅在本测试的独立 MVC 环境中注册；显式声明访问策略以兼容全仓接口扫描。 */
    @com.workflow.core.security.AuthenticatedApi
    @RestController
    static class ProbeController {
        private final ProbeMapper mapper;
        ProbeController(ProbeMapper mapper) { this.mapper = mapper; }
        @GetMapping("/db-error-probe") public void fail() { mapper.insert(2, "too-long", 1, 1); }
    }

    private static TransactionTemplate transaction(AssertableApplicationContext context) {
        var tx = new TransactionTemplate(new DataSourceTransactionManager(context.getBean(DataSource.class)));
        tx.setTimeout(15); return tx;
    }

    private static void database(Consumer<AssertableApplicationContext> test) {
        try (var f = new Fixture()) { database(f, test); }
    }

    private static void database(Fixture f, Consumer<AssertableApplicationContext> test) {
        String parent = f.table("db_error_parent", "id INT PRIMARY KEY");
        String child = f.table("db_error_child", "id INT PRIMARY KEY, required_value VARCHAR(3) NOT NULL, "
                + "parent_id INT NOT NULL, score TINYINT NOT NULL, "
                + "CONSTRAINT error_fk_" + f.suffix + " FOREIGN KEY (parent_id) REFERENCES " + parent + "(id), "
                + "CONSTRAINT error_check_" + f.suffix + " CHECK (score >= 0)");
        f.jdbc.update("INSERT INTO " + parent + " VALUES (1)");
        f.jdbc.update("INSERT INTO " + child + " VALUES (1, 'ok', 1, 1)");
        DataSource source = f.isolatedDataSource();
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                JdbcTemplateAutoConfiguration.class, MybatisPlusAutoConfiguration.class))
                .withUserConfiguration(DatabaseConfiguration.class, DatabaseMybatisConfiguration.class)
                .withBean(DataSource.class, () -> source)
                .withBean(SchemaDdlDialect.class, () -> DatabaseDialects.forVendor(DatabaseVendor.MYSQL))
                .withBean(DatabaseConnections.class, () -> new DatabaseConnections() {
                    public DataSource application() { return source; }
                    public DataSource schema() { return source; }
                }).run(context -> {
                    assertNull(context.getStartupFailure());
                    context.getBean(SqlSessionFactory.class).getConfiguration().addMapper(ProbeMapper.class);
                    test.accept(context);
                });
    }
}
