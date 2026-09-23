package com.workflow.biz.project.custom;

import com.workflow.contracts.migration.model.ConfigMigrationPublishRequest;
import com.workflow.contracts.migration.port.MigrationAssetPort;
import com.workflow.core.logging.LogValue;
import lombok.extern.slf4j.Slf4j;

/**
 * 配置迁移资产登记端口替换示例。
 *
 * <p>平台已有数据库实现，因此该类不注册为 Spring Bean。当前实现仅记录
 * 被发布的资产类型和 ID，不持久化可导出的迁移资产。</p>
 */
@Slf4j
public class ProjectCustomMigrationAssetHandler
        implements MigrationAssetPort {

    /**
     * 记录实体；供后续追溯或审计使用。
     *
     * @param entityId 实体ID，后续用于记录实体时定位或关联目标
     * @param publishHistoryId 发布历史ID，后续用于记录实体时定位或关联目标
     * @param request 本次请求，后续经校验后用于记录实体
     */
    @Override
    public void recordEntity(
            String entityId,
            String publishHistoryId,
            ConfigMigrationPublishRequest request) {
        logRecord(
                "ENTITY",
                entityId,
                publishHistoryId,
                request);
    }

    /**
     * 记录实体界面；供后续追溯或审计使用。
     *
     * @param entityId 实体ID，后续用于记录实体界面时定位或关联目标
     * @param releaseId 发布版本ID，后续用于记录实体界面时定位或关联目标
     * @param request 本次请求，后续经校验后用于记录实体界面
     */
    @Override
    public void recordEntityUi(
            String entityId,
            String releaseId,
            ConfigMigrationPublishRequest request) {
        logRecord(
                "ENTITY",
                entityId,
                releaseId,
                request);
    }

    /**
     * 记录流程；供后续追溯或审计使用。
     *
     * @param processId 流程ID，后续用于记录流程时定位或关联目标
     * @param versionHistoryId 版本历史ID，后续用于记录流程时定位或关联目标
     * @param request 本次请求，后续经校验后用于记录流程
     */
    @Override
    public void recordProcess(
            String processId,
            String versionHistoryId,
            ConfigMigrationPublishRequest request) {
        logRecord(
                "PROCESS",
                processId,
                versionHistoryId,
                request);
    }

    /**
     * 记录系统实体界面；供后续追溯或审计使用。
     *
     * @param entityId 实体ID，后续用于记录系统实体界面时定位或关联目标
     * @param releaseId 发布版本ID，后续用于记录系统实体界面时定位或关联目标
     * @param request 本次请求，后续经校验后用于记录系统实体界面
     */
    @Override
    public void recordSystemEntityUi(
            String entityId,
            String releaseId,
            ConfigMigrationPublishRequest request) {
        logRecord(
                "SYSTEM_ENTITY_UI",
                entityId,
                releaseId,
                request);
    }

    /**
     * 记录工作日历；供后续追溯或审计使用。
     *
     * @param calendarId 日历ID，后续用于记录工作日历时定位或关联目标
     * @param request 本次请求，后续经校验后用于记录工作日历
     */
    @Override
    public void recordWorkCalendar(
            String calendarId,
            ConfigMigrationPublishRequest request) {
        logRecord(
                "WORK_CALENDAR",
                calendarId,
                null,
                request);
    }

    /**
     * 记录任务SLA策略；供后续追溯或审计使用。
     *
     * @param policyId 策略ID，后续用于记录任务SLA策略时定位或关联目标
     * @param request 本次请求，后续经校验后用于记录任务SLA策略
     */
    @Override
    public void recordTaskSlaPolicy(
            String policyId,
            ConfigMigrationPublishRequest request) {
        logRecord(
                "TASK_SLA_POLICY",
                policyId,
                null,
                request);
    }

    /**
     * 处理日志记录，并将结果传给后续步骤。
     *
     * @param assetType 资产类型标识，决定后续日志记录采用的处理分支
     * @param assetId 资产ID，后续用于处理日志记录时定位或关联目标
     * @param releaseId 发布版本ID，后续用于处理日志记录时定位或关联目标
     * @param request 本次请求，后续经校验后用于处理日志记录
     */
    private void logRecord(
            String assetType,
            String assetId,
            String releaseId,
            ConfigMigrationPublishRequest request) {
        log.info(
                "项目迁移资产登记示例执行: assetType={}, assetId={}, releaseId={}, markForExport={}, migrationTag={}",
                assetType,
                LogValue.safe(assetId),
                LogValue.safe(releaseId),
                request == null
                        ? null : request.getMarkForExport(),
                LogValue.safe(request == null
                        ? null : request.getMigrationTag()));
    }
}
