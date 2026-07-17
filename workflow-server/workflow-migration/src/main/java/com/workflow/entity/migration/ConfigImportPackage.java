package com.workflow.entity.migration;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("config_import_package")
public class ConfigImportPackage {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String packageNo;
    private String sourceEnvironment;
    private String migrationTag;
    private String fileName;
    private String checksum;
    private String status;
    private String validationReportJson;
    @TableField("package_data")
    private byte[] packageData;
    private String importedBy;
    private LocalDateTime importedAt;
    private String publishedBy;
    private LocalDateTime publishedAt;
    private String errorMessage;
    @TableLogic
    private Integer deleted;
}
