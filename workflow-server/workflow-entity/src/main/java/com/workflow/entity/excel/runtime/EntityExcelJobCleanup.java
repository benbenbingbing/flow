package com.workflow.entity.excel.runtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** 按24小时保留期回收原文件、结果与行明细；保留任务统计作为长期审计摘要。 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EntityExcelJobCleanup {
    private final JdbcTemplate jdbc;
    private final EntityExcelFileStore files;
    private final PlatformTransactionManager transactions;

    /** 每次最多100个过期任务；行锁避免将正在提交的批次误判为中断，清理失败可重试。 */
    @Scheduled(fixedDelayString = "${entity.excel.cleanup-delay-ms:3600000}")
    public void clean() {
        var cutoff = LocalDateTime.now(ZoneOffset.UTC).minusHours(24);
        var jobs = jdbc.query(connection -> {
            var query = connection.prepareStatement("""
                    SELECT id FROM entity_excel_job WHERE created_at < ?
                    AND (status='RUNNING' OR input_file_id IS NOT NULL OR output_file_id IS NOT NULL OR error_file_id IS NOT NULL
                      OR EXISTS (SELECT 1 FROM entity_excel_job_row r WHERE r.job_id=entity_excel_job.id))
                    ORDER BY created_at
                    """);
            // created_at 与作业写入统一保存 UTC LocalDateTime。Timestamp.valueOf 会先套用 JVM 时区，
            // 再被 JDBC 转到连接时区；例如 Asia/Shanghai + UTC 连接会把24小时边界错移8小时。
            query.setObject(1, cutoff); query.setMaxRows(100); return query;
        }, new ColumnMapRowMapper());
        for (var candidate : jobs) {
            String id = String.valueOf(candidate.get("id"));
            try {
                new TransactionTemplate(transactions).executeWithoutResult(status -> {
                    var records = jdbc.queryForList("SELECT * FROM entity_excel_job WHERE id=? FOR UPDATE", id);
                    if (records.isEmpty()) return;
                    var record = records.get(0);
                    if ("RUNNING".equals(record.get("status"))) {
                        // 分批导入可能已经提交部分记录，保留与业务同事务写入的进度，禁止伪报零成功。
                        // 后续批次必须再锁定并检查状态，因此不会在清理后恢复写入。
                        jdbc.update("UPDATE entity_excel_job SET status='FAILED',error_message=?,counters_json=COALESCE(counters_json,?),updated_at=? WHERE id=?",
                                "任务执行已超时或中断，已提交记录保留，请核对实际新增数量后再导入", "{\"savedRowCount\":0}",
                                LocalDateTime.now(ZoneOffset.UTC), id);
                    }
                    for (String field : List.of("input_file_id", "output_file_id", "error_file_id"))
                        if (record.get(field) != null) files.delete(String.valueOf(record.get(field)));
                    jdbc.update("DELETE FROM entity_excel_job_row WHERE job_id=?", id);
                    jdbc.update("UPDATE entity_excel_job SET input_file_id=NULL,output_file_id=NULL,error_file_id=NULL WHERE id=?", id);
                });
            } catch (RuntimeException ex) {
                log.warn("Excel任务清理失败: jobId={}, failureType={}", id, ex.getClass().getSimpleName());
            }
        }
    }
}
