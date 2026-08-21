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

    /**
     * 自定义实体的表单或列表发布后，按实体当前完整配置登记新的迁移快照。
     */
    void recordEntityUi(
            String entityId,
            String releaseId,
            ConfigMigrationPublishRequest request);

    void recordProcess(
            String processId,
            String versionHistoryId,
            ConfigMigrationPublishRequest request);

    void recordSystemEntityUi(
            String entityId,
            String releaseId,
            ConfigMigrationPublishRequest request);

    void recordWorkCalendar(
            String calendarId,
            ConfigMigrationPublishRequest request);

    void recordTaskSlaPolicy(
            String policyId,
            ConfigMigrationPublishRequest request);
}
