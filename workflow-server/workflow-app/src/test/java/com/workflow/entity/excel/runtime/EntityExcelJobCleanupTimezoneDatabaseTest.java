package com.workflow.entity.excel.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** 使用已初始化的独立 OB MySQL 测试库，验证真实 JDBC 时间转换不会改变24小时保留边界。 */
@EnabledIfEnvironmentVariable(named = "FLOW_OBMYSQL_TEST_URL", matches = ".+")
@ResourceLock(Resources.TIME_ZONE)
class EntityExcelJobCleanupTimezoneDatabaseTest {
    @Test
    void utcStoredJobsExpireAfter24HoursRegardlessOfJvmTimezone() {
        TimeZone previous = TimeZone.getDefault();
        String url = System.getenv("FLOW_OBMYSQL_TEST_URL");
        // clean() 会扫描整个库的到期任务，必须在任何连接和写入前拒绝普通业务库地址。
        if (!url.matches("jdbc:(?:mysql|oceanbase)://(?:127\\.0\\.0\\.1|localhost):[0-9]+/flow_ob_(?:mapper|excel)_[A-Za-z0-9_]+(?:\\?.*)?")) {
            throw new IllegalArgumentException("时区清理测试仅允许本机 flow_ob_mapper_* 或 flow_ob_excel_* 独立库");
        }
        if (url.matches(".*(?i:serverTimezone|connectionTimeZone)=.*")) {
            url = url.replaceAll("(?i)(serverTimezone|connectionTimeZone)=[^&]*", "$1=UTC");
        } else url += (url.contains("?") ? "&" : "?") + "serverTimezone=UTC";
        try {
            for (String zone : List.of("Asia/Shanghai", "UTC", "America/New_York")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone));
                var source = new DriverManagerDataSource(url, System.getenv("FLOW_OBMYSQL_TEST_USER"),
                        System.getenv("FLOW_OBMYSQL_TEST_PASSWORD"));
                var jdbc = new JdbcTemplate(source);
                assertThat(jdbc.queryForObject("SELECT VERSION()", String.class)).containsIgnoringCase("OceanBase");
                String oldId = UUID.randomUUID().toString();
                String recentId = UUID.randomUUID().toString();
                try {
                    insertJob(jdbc, oldId, LocalDateTime.now(ZoneOffset.UTC).minusHours(25), "{\"savedRowCount\":2}");
                    insertJob(jdbc, recentId, LocalDateTime.now(ZoneOffset.UTC).minusHours(23), null);
                    jdbc.update("INSERT INTO entity_excel_job_row(id,job_id,sheet_no,excel_row_number,errors_json) VALUES (?,?,0,2,?)",
                            UUID.randomUUID().toString(), oldId, "[{\"message\":\"过期错误明细\"}]");
                    var cleanup = new EntityExcelJobCleanup(jdbc, mock(EntityExcelFileStore.class),
                            new DataSourceTransactionManager(source));
                    cleanup.clean();
                    assertThat(jdbc.queryForObject("SELECT status FROM entity_excel_job WHERE id=?", String.class, oldId))
                            .as(zone + " 25小时任务应结束").isEqualTo("FAILED");
                    assertThat(jdbc.queryForObject("SELECT status FROM entity_excel_job WHERE id=?", String.class, recentId))
                            .as(zone + " 23小时任务不能提前结束").isEqualTo("RUNNING");
                    assertThat(jdbc.queryForObject("SELECT counters_json FROM entity_excel_job WHERE id=?", String.class, oldId))
                            .isEqualTo("{\"savedRowCount\":2}");
                    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM entity_excel_job_row WHERE job_id=?", Integer.class, oldId))
                            .isZero();
                } finally {
                    jdbc.update("DELETE FROM entity_excel_job_row WHERE job_id IN (?,?)", oldId, recentId);
                    jdbc.update("DELETE FROM entity_excel_job WHERE id IN (?,?)", oldId, recentId);
                }
            }
        } finally { TimeZone.setDefault(previous); }
    }

    /** 与生产认领相同，DATETIME 字段绑定 UTC LocalDateTime，任务不依赖真实用户或实体记录。 */
    private void insertJob(JdbcTemplate jdbc, String id, LocalDateTime createdAt, String counters) {
        jdbc.update("""
                INSERT INTO entity_excel_job(id,entity_id,entity_code,operation,status,user_id,origin_key,
                  request_id,payload_hash,config_json,counters_json,created_at,updated_at)
                VALUES (?, 'timezone-probe', 'timezone_probe', 'IMPORT', 'RUNNING', 'timezone-probe',
                  'timezone-probe', ?, ?, '{}', ?, ?, ?)
                """, id, id, "0".repeat(64), counters, createdAt, createdAt);
    }
}
