package com.workflow.contracts.migration;

import com.workflow.dto.migration.ConfigMigrationPublishRequest;

public interface MigrationAssetRecorder {

    void recordEntity(String entityId, String publishHistoryId, ConfigMigrationPublishRequest request);

    void recordProcess(String processId, String versionHistoryId, ConfigMigrationPublishRequest request);
}
