package com.workflow.entity.migration;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 配置导出包条目。
 *
 * <p>记录一个导出包中包含的每个迁移资产及其在导出时使用的选择配置，
 * 用于追溯导出包的资产清单与快照选择范围。</p>
 */
@Data
@TableName("config_export_package_item")
public class ConfigExportPackageItem {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;                          // 主键
    private String packageId;                   // 所属导出包ID
    private String assetId;                     // 迁移资产ID
    private String assetType;                   // 资产类型(ENTITY/PROCESS)
    private String businessKey;                 // 资产业务编码
    private Integer sourceVersion;              // 导出时资产版本
    private String contentHash;                 // 导出时资产内容哈希
    private String selectionJson;              // 该资产导出时的快照选择配置(JSON)
    private LocalDateTime createdAt;           // 条目创建时间
}
