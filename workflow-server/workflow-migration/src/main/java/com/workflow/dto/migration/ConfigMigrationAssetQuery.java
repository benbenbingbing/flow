package com.workflow.dto.migration;

import lombok.Data;

/**
 * 配置迁移资产查询条件。
 *
 * <p>作为迁移资产列表查询的过滤参数，所有字段均为可选条件。</p>
 */
@Data
public class ConfigMigrationAssetQuery {

    private String assetType;               // 资产类型(ENTITY/PROCESS)
    private String businessKey;             // 资产业务编码(模糊匹配)
    private String migrationTag;           // 迁移标签
    private Boolean markForExport;          // 是否标记待导出
    private String exportStatus;            // 导出状态
    private String snapshotCompleteness;    // 快照完整度
}
