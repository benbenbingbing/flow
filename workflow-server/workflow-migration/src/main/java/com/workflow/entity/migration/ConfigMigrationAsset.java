package com.workflow.entity.migration;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 配置迁移资产。
 *
 * <p>记录实体/流程配置在每次发布时生成的可迁移快照版本，包含快照内容、内容哈希、
 * 依赖清单、快照完整度(PARTIAL/COMPLETE)以及导出标记与统计，是配置迁移的核心实体。</p>
 */
@Data
@TableName("config_migration_asset")
public class ConfigMigrationAsset {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;                          // 主键
    private String assetType;                   // 资产类型(ENTITY/PROCESS)
    private String businessKey;                 // 资产业务编码(实体编码或流程Key)
    private String assetName;                   // 资产名称
    private String sourceHistoryId;             // 关联的源发布历史ID
    private Integer sourceVersion;              // 源版本号
    private String versionDescription;          // 版本描述
    private String migrationTag;                // 迁移标签
    private Boolean markForExport;              // 是否标记待导出
    private String snapshotCompleteness;        // 快照完整度(COMPLETE/PARTIAL)
    private Integer snapshotSchemaVersion;      // 快照结构版本
    private String snapshotJson;                // 快照内容(JSON)
    private String contentHash;                 // 快照内容哈希(SHA-256)
    private String dependenciesJson;            // 依赖清单(JSON)
    private Integer dependencyCount;            // 依赖总数
    private Integer missingDependencyCount;     // 缺失依赖数(预留)
    private String exportStatus;                // 导出状态(PENDING/EXPORTED)
    private LocalDateTime publishedAt;           // 发布时间
    private String publishedBy;                 // 发布人
    private LocalDateTime lastExportAt;          // 最近一次导出时间
    private Integer exportCount;                // 累计导出次数
    private LocalDateTime createdAt;            // 创建时间
    private LocalDateTime updatedAt;            // 更新时间
    @TableLogic
    private Integer deleted;                    // 逻辑删除标记
}
