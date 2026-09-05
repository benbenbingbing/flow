package com.workflow.contracts.migration.port;

import com.workflow.contracts.migration.ConfigMigrationPublishRequest;

/** 发布流程中登记迁移资产的跨模块端口。 */
public interface MigrationAssetHandler {

    /** 登记实体发布资产。 */
    void recordEntity(
            String entityId,
            String publishHistoryId,
            ConfigMigrationPublishRequest request);

    /** 登记自定义实体表单或列表发布后的完整迁移快照。 */
    void recordEntityUi(
            String entityId,
            String releaseId,
            ConfigMigrationPublishRequest request);

    /** 登记流程发布资产。 */
    void recordProcess(
            String processId,
            String versionHistoryId,
            ConfigMigrationPublishRequest request);

    /** 登记系统实体 UI 发布资产。 */
    void recordSystemEntityUi(
            String entityId,
            String releaseId,
            ConfigMigrationPublishRequest request);

    /** 登记工作日历发布资产。 */
    void recordWorkCalendar(
            String calendarId,
            ConfigMigrationPublishRequest request);

    /** 登记任务 SLA 策略发布资产。 */
    void recordTaskSlaPolicy(
            String policyId,
            ConfigMigrationPublishRequest request);
}
