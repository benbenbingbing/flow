package com.workflow.entity.migration;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("config_import_item")
public class ConfigImportItem {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String importPackageId;
    private String assetType;
    private String businessKey;
    private String assetName;
    private Integer sourceVersion;
    private String sourceHash;
    private Integer targetBeforeVersion;
    private String targetBeforeHash;
    private Integer targetAfterVersion;
    private String targetAfterHash;
    private String comparisonStatus;
    private String mappingStatus;
    private String publishStatus;
    private String snapshotJson;
    private String dependenciesJson;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
