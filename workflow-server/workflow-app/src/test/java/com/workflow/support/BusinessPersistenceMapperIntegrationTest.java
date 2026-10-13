package com.workflow.support;

import com.workflow.bootstrap.infrastructure.persistence.mapper.BootstrapJobMapper;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigMigrationAssetDependencyMapper;
import com.workflow.observability.infrastructure.persistence.mapper.AsyncQueueMetricsMapper;
import com.workflow.process.configuration.infrastructure.persistence.mapper.NodeConfigDocumentMapper;
import com.workflow.process.assignment.infrastructure.persistence.mapper.AssigneeIncidentMapper;
import com.workflow.core.database.mybatis.ParameterizedSql;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** 使用真实 Mapper 验证列映射、CLOB、队列聚合以及与现有 Spring JDBC 事务共享回滚。 */
class BusinessPersistenceMapperIntegrationTest {
    @Test
    void preservesMappingsAndRollsBackStateWithTheBusinessTransaction() {
        var database = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true).build();
        try {
            var jdbc = new JdbcTemplate(database);
            jdbc.execute("CREATE TABLE process_node_config(id VARCHAR(64) PRIMARY KEY, config_json CLOB)");
            jdbc.execute("CREATE TABLE config_migration_asset(id VARCHAR(64) PRIMARY KEY, asset_type VARCHAR(30), business_key VARCHAR(100), source_version INT, deleted INT)");
            jdbc.execute("CREATE TABLE workflow_bootstrap_job(job_name VARCHAR(100) PRIMARY KEY, completed_version INT, owner_id VARCHAR(100), completed_at TIMESTAMP, update_time TIMESTAMP)");
            for (String table : java.util.List.of("workflow_outbox_event", "process_action_execution")) {
                jdbc.execute("CREATE TABLE " + table + "(status VARCHAR(30), create_time TIMESTAMP, next_retry_time TIMESTAMP)");
            }
            jdbc.update("INSERT INTO process_node_config VALUES (?, ?)", "node", "{\"previous\":true}");
            jdbc.update("INSERT INTO config_migration_asset VALUES (?, ?, ?, ?, 0)", "asset", "FORM", "leave", 7);
            jdbc.update("INSERT INTO workflow_bootstrap_job(job_name, completed_version) VALUES ('catalog', 1)");
            var node = BusinessMapperTestSupport.mapper(database, NodeConfigDocumentMapper.class);
            var dependency = BusinessMapperTestSupport.mapper(database, ConfigMigrationAssetDependencyMapper.class);
            var bootstrap = BusinessMapperTestSupport.mapper(database, BootstrapJobMapper.class);
            var metrics = BusinessMapperTestSupport.mapper(database, AsyncQueueMetricsMapper.class);
            var incidents = BusinessMapperTestSupport.mapper(database, AssigneeIncidentMapper.class);
            // H2 默认大写列名，模拟 Oracle 的结果标签；保留显式 NULL 与大文本字符串。
            var row = incidents.selectRows(ParameterizedSql.of(
                    "SELECT id, config_json, CAST(NULL AS VARCHAR) AS OPTIONAL_TEXT FROM process_node_config")).get(0);
            assertInstanceOf(org.springframework.util.LinkedCaseInsensitiveMap.class, row);
            assertEquals("node", row.get("id"));
            assertEquals("{\"previous\":true}", row.get("config_json"));
            assertTrue(row.containsKey("optional_text"));
            assertNull(row.get("optional_text"));
            assertEquals("{\"previous\":true}", node.findDocument("node"));
            assertEquals("FORM", dependency.findSourceAsset("asset").get("assetType"));
            assertEquals("leave", dependency.findSourceAsset("asset").get("businessKey"));
            assertEquals(7, dependency.findSourceAsset("asset").get("sourceVersion"));
            assertNull(dependency.findSourceAsset("missing"));
            var now = LocalDateTime.of(2026, 10, 13, 2, 0);
            assertEquals(0, metrics.outbox(now).ready());
            assertNull(metrics.outbox(now).oldestReadyAt());
            jdbc.update("INSERT INTO workflow_outbox_event VALUES ('PENDING', ?, ?)", now.minusMinutes(1), now);
            jdbc.update("INSERT INTO workflow_outbox_event VALUES ('FAILED', ?, ?)", now.minusMinutes(2), now.plusMinutes(1));
            jdbc.update("INSERT INTO process_action_execution VALUES ('RUNNING', ?, NULL)", now);
            assertEquals(1, metrics.outbox(now).ready());
            assertEquals(now.minusMinutes(1), metrics.outbox(now).oldestReadyAt());
            assertEquals(1, metrics.flowAction(now).running());
            var tx = new TransactionTemplate(new DataSourceTransactionManager(database));
            tx.executeWithoutResult(status -> {
                assertEquals(1, bootstrap.completedVersion("catalog"));
                assertEquals(1, bootstrap.complete("catalog", 2, "owner", now));
                assertEquals(2, bootstrap.completedVersion("catalog"));
                assertEquals(1, node.updateDocument("{\"next\":true}", "node"));
                status.setRollbackOnly();
            });
            assertEquals(1, bootstrap.completedVersion("catalog"));
            assertEquals("{\"previous\":true}", node.findDocument("node"));
        } finally {
            database.shutdown();
        }
    }
}
