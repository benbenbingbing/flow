package com.workflow.dto.migration;

import lombok.Data;

@Data
public class ConfigMigrationAssetQuery {

    private String assetType;
    private String businessKey;
    private String migrationTag;
    private Boolean markForExport;
    private String exportStatus;
    private String snapshotCompleteness;
}
