package com.workflow.runner;

import com.workflow.service.migration.ConfigMigrationAssetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(30)
@RequiredArgsConstructor
public class ConfigMigrationAssetBackfillRunner implements ApplicationRunner {

    private final ConfigMigrationAssetService assetService;

    @Override
    public void run(ApplicationArguments args) {
        int created = assetService.backfillLegacyAssets();
        if (created > 0) {
            log.info("配置迁移历史资产回填完成: created={}", created);
        }
    }
}
