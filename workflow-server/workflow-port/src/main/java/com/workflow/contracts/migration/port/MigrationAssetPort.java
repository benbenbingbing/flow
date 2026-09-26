package com.workflow.contracts.migration.port;

import com.workflow.contracts.migration.model.ConfigMigrationPublishRequest;

/** 发布流程中登记迁移资产的跨模块端口。 */
public interface MigrationAssetPort {

    /**
     * 登记实体发布资产。
     *
     * @param entityId 实体ID，后续用于记录实体时定位或关联目标
     * @param publishHistoryId 发布历史ID，后续用于记录实体时定位或关联目标
     * @param request 本次请求，后续经校验后用于记录实体
     */
    void recordEntity(
            String entityId,
            String publishHistoryId,
            ConfigMigrationPublishRequest request);

    /**
     * 登记自定义实体表单或列表发布后的完整迁移快照。
     *
     * @param entityId 实体ID，后续用于记录实体界面时定位或关联目标
     * @param releaseId 发布版本ID，后续用于记录实体界面时定位或关联目标
     * @param request 本次请求，后续经校验后用于记录实体界面
     */
    void recordEntityUi(
            String entityId,
            String releaseId,
            ConfigMigrationPublishRequest request);

    /**
     * 登记流程发布资产。
     *
     * @param processId 流程ID，后续用于记录流程时定位或关联目标
     * @param versionHistoryId 版本历史ID，后续用于记录流程时定位或关联目标
     * @param request 本次请求，后续经校验后用于记录流程
     */
    void recordProcess(
            String processId,
            String versionHistoryId,
            ConfigMigrationPublishRequest request);

    /**
     * 登记系统实体 UI 发布资产。
     *
     * @param entityId 实体ID，后续用于记录系统实体界面时定位或关联目标
     * @param releaseId 发布版本ID，后续用于记录系统实体界面时定位或关联目标
     * @param request 本次请求，后续经校验后用于记录系统实体界面
     */
    void recordSystemEntityUi(
            String entityId,
            String releaseId,
            ConfigMigrationPublishRequest request);

    /**
     * 登记工作日历发布资产。
     *
     * @param calendarId 日历ID，后续用于记录工作日历时定位或关联目标
     * @param request 本次请求，后续经校验后用于记录工作日历
     */
    void recordWorkCalendar(
            String calendarId,
            ConfigMigrationPublishRequest request);

    /**
     * 登记任务 SLA 策略发布资产。
     *
     * @param policyId 策略ID，后续用于记录任务SLA策略时定位或关联目标
     * @param request 本次请求，后续经校验后用于记录任务SLA策略
     */
    void recordTaskSlaPolicy(
            String policyId,
            ConfigMigrationPublishRequest request);
}
