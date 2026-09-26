package com.workflow.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.annotation.DbType;
import com.workflow.admin.audit.infrastructure.persistence.mapper.SystemOperationLogMapper;
import com.workflow.outbox.infrastructure.persistence.mapper.OutboxRecordMapper;
import com.workflow.core.database.BoundedRetentionRunner;
import com.workflow.core.database.port.DatabaseLockPort;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 实库事务与 Mapper 测试：验证单批上限、保留期、状态保护及失败批次独立回滚。 */
class RetentionBatchDatabaseTest {
    private EmbeddedDatabase database;
    private JdbcTemplate jdbc;
    private SqlSessionTemplate session;
    private DatabaseLockPort locks;
    private DatabaseLockPort.Handle lock;
    private BoundedRetentionRunner runner;
    private DataSourceTransactionManager transactions;
    private final LocalDateTime cutoff = LocalDateTime.of(2026, 1, 1, 0, 0);

    @BeforeEach
    void setUp() throws Exception {
        database = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).generateUniqueName(true).build();
        jdbc = new JdbcTemplate(database);
        jdbc.execute("CREATE TABLE system_operation_log (id VARCHAR(64) PRIMARY KEY, create_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE workflow_outbox_event (id VARCHAR(64) PRIMARY KEY, status VARCHAR(16), processed_time TIMESTAMP)");
        var config = new MybatisConfiguration();
        config.setDatabaseId("MYSQL");
        config.addMapper(SystemOperationLogMapper.class);
        config.addMapper(OutboxRecordMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(database);
        factory.setConfiguration(config);
        var pagination = new MybatisPlusInterceptor();
        pagination.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        factory.setPlugins(pagination);
        session = new SqlSessionTemplate(factory.getObject());
        transactions = new DataSourceTransactionManager(database);
        locks = mock(DatabaseLockPort.class);
        lock = mock(DatabaseLockPort.Handle.class);
        when(locks.tryAcquire(eq("retention"), anyString())).thenReturn(Optional.of(lock));
        runner = new BoundedRetentionRunner(locks, transactions);
    }

    @AfterEach
    void tearDown() { database.shutdown(); }

    @Test
    void deletesOnlyBudgetedOldAuditRowsInStableOrder() {
        for (int i = 0; i < 7; i++) jdbc.update("INSERT INTO system_operation_log VALUES (?, ?)",
                "old-" + i, cutoff.minusDays(1));
        jdbc.update("INSERT INTO system_operation_log VALUES ('new', ?)", cutoff.plusDays(1));
        var mapper = session.getMapper(SystemOperationLogMapper.class);
        assertEquals(5, runner.run("audit", 3, 5, 10, limit -> mapper.deleteExpiredBatch(cutoff, limit)));
        assertEquals(List.of("new", "old-5", "old-6"), jdbc.queryForList(
                "SELECT id FROM system_operation_log ORDER BY id", String.class));
        verify(lock).close();
    }

    @Test
    void outboxPreservesUnfinishedAndRecentEvents() {
        for (int i = 0; i < 5; i++) jdbc.update("INSERT INTO workflow_outbox_event VALUES (?, 'PROCESSED', ?)",
                "old-" + i, cutoff.minusDays(1));
        for (String status : List.of("PENDING", "PROCESSING", "FAILED")) {
            jdbc.update("INSERT INTO workflow_outbox_event VALUES (?, ?, ?)", status, status, cutoff.minusDays(1));
        }
        jdbc.update("INSERT INTO workflow_outbox_event VALUES ('recent', 'PROCESSED', ?)", cutoff.plusDays(1));
        var mapper = session.getMapper(OutboxRecordMapper.class);
        assertEquals(3, runner.run("outbox", 2, 3, 10, limit -> mapper.deleteProcessedBatchBefore(cutoff, limit)));
        assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM workflow_outbox_event", Integer.class));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM workflow_outbox_event WHERE status <> 'PROCESSED'", Integer.class));
    }

    @Test
    void failedBatchRollsBackAloneAndOuterTransactionCannotUndoCommittedBatch() {
        jdbc.update("INSERT INTO system_operation_log VALUES ('1', ?), ('2', ?)", cutoff, cutoff);
        var batch = new AtomicInteger();
        var outer = new TransactionTemplate(transactions);
        outer.executeWithoutResult(status -> {
            assertThrows(IllegalStateException.class, () -> runner.run("audit", 1, 2, 10, limit -> {
                int current = batch.incrementAndGet();
                jdbc.update("DELETE FROM system_operation_log WHERE id = ?", Integer.toString(current));
                if (current == 2) throw new IllegalStateException("模拟第二批失败");
                return 1;
            }));
            status.setRollbackOnly();
        });
        assertEquals(List.of("2"), jdbc.queryForList("SELECT id FROM system_operation_log", String.class));
        verify(lock).close();
    }

    @Test
    void anotherInstanceHoldingLockSkipsCleanup() {
        when(locks.tryAcquire("retention", "audit")).thenReturn(Optional.empty());
        assertEquals(0, runner.run("audit", 10, 100, 10, limit -> { throw new AssertionError("竞争实例不能清理"); }));
        verifyNoInteractions(lock);
    }

    @Test
    void retentionIndexMigrationPreservesRowsAndSupportsBoundedOrderedRead() {
        jdbc.update("INSERT INTO workflow_outbox_event VALUES ('b', 'PROCESSED', ?), ('a', 'PROCESSED', ?),"
                + " ('pending', 'PENDING', ?), ('recent', 'PROCESSED', ?)",
                cutoff.minusDays(1), cutoff.minusDays(1), cutoff.minusDays(1), cutoff.plusDays(1));
        // 执行交付的实际迁移，验证升级只增加访问路径，不删除、改写或重排业务记录。
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new org.springframework.core.io.ClassPathResource("db/migration/V106__outbox_retention_index.sql"))
                .execute(database);
        assertEquals(List.of("STATUS", "PROCESSED_TIME", "ID"), jdbc.queryForList("""
                SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.INDEX_COLUMNS
                WHERE INDEX_NAME = 'IDX_WORKFLOW_OUTBOX_RETENTION' ORDER BY ORDINAL_POSITION
                """, String.class));
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM workflow_outbox_event", Integer.class));
        assertEquals(List.of("a", "b"), jdbc.queryForList("""
                SELECT id FROM workflow_outbox_event WHERE status = 'PROCESSED' AND processed_time < ?
                ORDER BY processed_time, id LIMIT 2
                """, String.class, cutoff));
        assertTrue(jdbc.queryForObject("""
                EXPLAIN SELECT id FROM workflow_outbox_event WHERE status = 'PROCESSED' AND processed_time < ?
                ORDER BY processed_time, id LIMIT 2
                """, String.class, cutoff).contains("IDX_WORKFLOW_OUTBOX_RETENTION"));
    }
}
