package com.workflow.entity.migration;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 配置导入批次。
 *
 * <p>记录从源环境导出的 wfpack 发布包导入到目标环境后的批次信息，
 * 包括原始包内容、校验结果、分析/发布/回滚状态流转以及操作人记录。</p>
 */
@Data
@TableName("config_import_package")
public class ConfigImportPackage {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;                          // 主键
    private String packageNo;                   // 发布包编号(源环境生成)
    private String sourceEnvironment;           // 源环境名称
    private String migrationTag;               // 迁移标签
    private String fileName;                   // 上传的原始文件名
    private String checksum;                   // 发布包整体校验和
    private String status;                     // 批次状态(UPLOADED/ANALYZED/BLOCKED/PUBLISHED/ROLLED_BACK)
    private String validationReportJson;       // 分析校验报告(JSON)
    @TableField("package_data")
    private byte[] packageData;                 // 发布包二进制内容
    private String importedBy;                  // 导入操作人
    private LocalDateTime importedAt;           // 导入时间
    private String publishedBy;                 // 发布操作人
    private LocalDateTime publishedAt;          // 发布时间
    private String errorMessage;               // 异常或阻断原因
    @TableLogic
    private Integer deleted;                    // 逻辑删除标记
}
