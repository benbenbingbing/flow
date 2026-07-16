package com.workflow.entity.migration;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("config_migration_asset")
public class ConfigMigrationAsset {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String assetType;
    private String businessKey;
    private String assetName;
    private String sourceHistoryId;
    private Integer sourceVersion;
    private String versionDescription;
    private String migrationTag;
    private Boolean markForExport;
    private String snapshotCompleteness;
    private Integer snapshotSchemaVersion;
    private String snapshotJson;
    private String contentHash;
    private String dependenciesJson;
    private Integer dependencyCount;
    private Integer missingDependencyCount;
    private String exportStatus;
    private LocalDateTime publishedAt;
    private String publishedBy;
    private LocalDateTime lastExportAt;
    private Integer exportCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
