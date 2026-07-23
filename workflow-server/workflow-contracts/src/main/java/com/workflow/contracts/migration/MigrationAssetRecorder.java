package com.workflow.contracts.migration;

import com.workflow.dto.migration.ConfigMigrationPublishRequest;

/**
 * 配置迁移资产记录端口。
 * 在配置发布时记录实体与流程资产的迁移元信息，便于迁移审计与追溯。
 */
public interface MigrationAssetRecorder {

    /**
     * 记录实体资产的迁移信息。
     *
     * @param entityId         实体ID
     * @param publishHistoryId 发布历史ID
     * @param request          发布请求
     */
    void recordEntity(String entityId, String publishHistoryId, ConfigMigrationPublishRequest request);

    /**
     * 记录流程资产的迁移信息。
     *
     * @param processId        流程ID
     * @param versionHistoryId 版本历史ID
     * @param request          发布请求
     */
    void recordProcess(String processId, String versionHistoryId, ConfigMigrationPublishRequest request);
}
