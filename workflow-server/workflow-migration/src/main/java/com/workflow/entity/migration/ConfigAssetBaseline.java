package com.workflow.entity.migration;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("config_asset_baseline")
public class ConfigAssetBaseline {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String assetType;
    private String businessKey;
    private Integer sourceVersion;
    private String sourceHash;
    private Integer targetVersion;
    private String targetHash;
    private String importPackageId;
    private LocalDateTime updatedAt;
}
