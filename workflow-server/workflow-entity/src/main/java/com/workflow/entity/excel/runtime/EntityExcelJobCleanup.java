package com.workflow.entity.excel.runtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.workflow.entity.excel.infrastructure.persistence.mapper.EntityExcelJobMapper;
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
    private final EntityExcelJobMapper jobs;
    private final EntityExcelFileStore files;
    private final PlatformTransactionManager transactions;

    /** 每次最多100个过期任务；行锁避免将正在提交的批次误判为中断，清理失败可重试。 */
    @Scheduled(fixedDelayString = "${entity.excel.cleanup-delay-ms:3600000}")
    public void clean() {
        var cutoff = LocalDateTime.now(ZoneOffset.UTC).minusHours(24);
        // LocalDateTimeTypeHandler 保持 UTC 墙上时间；不转换为受 JVM 时区影响的 Timestamp。
        for (String id : jobs.expired(cutoff)) {
            try {
                new TransactionTemplate(transactions).executeWithoutResult(status -> {
                    var record = jobs.lock(id);
                    if (record == null) return;
                    if ("RUNNING".equals(record.get("status"))) {
                        // 分批导入可能已经提交部分记录，保留与业务同事务写入的进度，禁止伪报零成功。
                        // 后续批次必须再锁定并检查状态，因此不会在清理后恢复写入。
                        jobs.failInterrupted(id, "任务执行已超时或中断，已提交记录保留，请核对实际新增数量后再导入",
                                "{\"savedRowCount\":0}", LocalDateTime.now(ZoneOffset.UTC));
                    }
                    for (String field : List.of("input_file_id", "output_file_id", "error_file_id"))
                        if (record.get(field) != null) files.delete(String.valueOf(record.get(field)));
                    jobs.deleteErrors(id);
                    jobs.clearFiles(id);
                });
            } catch (RuntimeException ex) {
                log.warn("Excel任务清理失败: jobId={}, failureType={}", id, ex.getClass().getSimpleName());
            }
        }
    }
}
