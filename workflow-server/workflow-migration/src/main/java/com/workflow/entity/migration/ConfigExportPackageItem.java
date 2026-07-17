package com.workflow.entity.migration;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("config_export_package_item")
public class ConfigExportPackageItem {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String packageId;
    private String assetId;
    private String assetType;
    private String businessKey;
    private Integer sourceVersion;
    private String contentHash;
    private String selectionJson;
    private LocalDateTime createdAt;
}
