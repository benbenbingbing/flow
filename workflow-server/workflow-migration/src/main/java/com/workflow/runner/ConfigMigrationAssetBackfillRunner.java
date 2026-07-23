package com.workflow.runner;

import com.workflow.service.migration.ConfigMigrationAssetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 配置迁移历史资产回填启动器。
 *
 * <p>应用启动时为缺少迁移资产记录的历史实体/流程发布版本补建 PARTIAL 快照资产，
 * 使其能在迁移资产列表中可见并提示重新发布以获得完整快照。</p>
 */
@Slf4j
@Component
@Order(30)
@RequiredArgsConstructor
public class ConfigMigrationAssetBackfillRunner implements ApplicationRunner {

    private final ConfigMigrationAssetService assetService;

    /**
     * 应用启动回调：执行历史资产回填并在有新建资产时输出日志。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        int created = assetService.backfillLegacyAssets();
        if (created > 0) {
            log.info("配置迁移历史资产回填完成: created={}", created);
        }
    }
}
