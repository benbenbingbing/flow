package com.workflow.project.custom;

import com.workflow.contracts.migration.ConfigMigrationPublishRequest;
import com.workflow.contracts.migration.MigrationAssetHandler;
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
        implements MigrationAssetHandler {

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
