package com.workflow.dto.migration;

import lombok.Data;

@Data
public class ConfigMigrationPublishRequest {

    private String versionDescription;
    private Boolean markForExport = Boolean.TRUE;
    private String migrationTag;
}
