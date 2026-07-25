package com.workflow.process.publish;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.entity.ProcessVersionHistory;
import com.workflow.mapper.ProcessVersionHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 幂等回填存量流程发布快照中的 UI release 绑定。
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "workflow.ui-hotfix",
        name = "binding-backfill-enabled",
        havingValue = "true",
        matchIfMissing = true)
@Order(Ordered.LOWEST_PRECEDENCE - 20)
@RequiredArgsConstructor
public class ProcessUiReleaseBindingBackfillRunner
        implements ApplicationRunner {

    private final ProcessVersionHistoryMapper historyMapper;
    private final ProcessUiReleaseBindingService bindingService;

    @Override
    public void run(ApplicationArguments args) {
        final long pageSize = 200L;
        long pageNumber = 1L;
        long histories = 0L;
        int inserted = 0;
        int updated = 0;
        int missing = 0;
        int invalid = 0;
        int skipped = 0;
        while (true) {
            Page<ProcessVersionHistory> page =
                    historyMapper.selectPage(
                            new Page<>(pageNumber, pageSize, false),
                            new LambdaQueryWrapper<ProcessVersionHistory>()
                                    .and(query -> query.eq(
                                                    ProcessVersionHistory::getDeleted,
                                                    0)
                                            .or()
                                            .isNull(
                                                    ProcessVersionHistory::getDeleted))
                                    .orderByAsc(
                                            ProcessVersionHistory::getPublishedAt)
                                    .orderByAsc(
                                            ProcessVersionHistory::getId));
            if (page.getRecords().isEmpty()) {
                break;
            }
            for (ProcessVersionHistory history : page.getRecords()) {
                ProcessUiReleaseBindingService.BackfillResult result =
                        bindingService.backfill(history);
                inserted += result.inserted();
                updated += result.updated();
                missing += result.missingRelease();
                invalid += result.invalidSnapshot();
                skipped += result.skippedExisting() ? 1 : 0;
            }
            histories += page.getRecords().size();
            if (page.getRecords().size() < pageSize) {
                break;
            }
            pageNumber++;
        }
        log.info(
                "流程UI发布绑定回填完成: histories={}, inserted={}, updated={}, "
                        + "missingRelease={}, invalidSnapshot={}, skippedExisting={}",
                histories,
                inserted,
                updated,
                missing,
                invalid,
                skipped);
    }
}
