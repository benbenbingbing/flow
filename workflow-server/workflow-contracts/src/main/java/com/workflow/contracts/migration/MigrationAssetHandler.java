package com.workflow.contracts.migration;

import com.workflow.contracts.migration.ConfigMigrationPublishRequest;

/**
 * 发布流程中登记迁移资产的跨模块端口。
 */
public interface MigrationAssetHandler {

    void recordEntity(
            String entityId,
            String publishHistoryId,
            ConfigMigrationPublishRequest request);

    void recordProcess(
            String processId,
            String versionHistoryId,
            ConfigMigrationPublishRequest request);
}
