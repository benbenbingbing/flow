package com.workflow.entity.migration;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("config_migration_asset_dependency")
public class ConfigMigrationAssetDependency {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String assetId;
    private String dependencyType;
    private String dependencyKey;
    private Boolean required;
    private String sourceDescription;
    private String dependencyDocument;

    @TableField("create_time")
    private LocalDateTime createdAt;
}
