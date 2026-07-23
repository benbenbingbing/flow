package com.workflow.entity.migration;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 配置导出包。
 *
 * <p>记录从当前环境打包生成的 wfpack 发布包元数据与二进制内容，
 * 包括包编号、校验和、HMAC 签名、资产数量与下载统计等。</p>
 */
@Data
@TableName("config_export_package")
public class ConfigExportPackage {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;                          // 主键
    private String packageNo;                   // 发布包编号(唯一)
    private String migrationTag;                // 迁移标签
    private String fileName;                    // 下载文件名
    private String checksum;                    // 发布包整体校验和
    private String signatureValue;              // 发布包HMAC签名
    private String status;                      // 状态(READY)
    private Integer assetCount;                 // 包含资产数量
    @TableField("package_data")
    private byte[] packageData;                 // 发布包二进制内容
    private String createdBy;                   // 创建人
    private LocalDateTime createdAt;             // 创建时间
    private Integer downloadCount;              // 累计下载次数
    private LocalDateTime lastDownloadAt;        // 最后下载时间
    @TableLogic
    private Integer deleted;                    // 逻辑删除标记
}
