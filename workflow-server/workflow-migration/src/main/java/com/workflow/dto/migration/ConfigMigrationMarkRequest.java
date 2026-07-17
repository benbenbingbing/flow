package com.workflow.dto.migration;

import lombok.Data;

@Data
public class ConfigMigrationMarkRequest {

    private Boolean markForExport;
    private String migrationTag;
}
