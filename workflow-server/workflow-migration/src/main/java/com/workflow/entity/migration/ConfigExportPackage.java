package com.workflow.entity.migration;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("config_export_package")
public class ConfigExportPackage {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String packageNo;
    private String migrationTag;
    private String fileName;
    private String checksum;
    private String signatureValue;
    private String status;
    private Integer assetCount;
    @TableField("package_data")
    private byte[] packageData;
    private String createdBy;
    private LocalDateTime createdAt;
    private Integer downloadCount;
    private LocalDateTime lastDownloadAt;
    @TableLogic
    private Integer deleted;
}
