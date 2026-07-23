package com.workflow.dto.migration;

import lombok.Data;

/**
 * 配置迁移资产标记请求。
 *
 * <p>用于更新迁移资产的待导出标记与迁移标签。</p>
 */
@Data
public class ConfigMigrationMarkRequest {

    private Boolean markForExport;   // 是否标记待导出(为空则不修改)
    private String migrationTag;     // 迁移标签(为空则不修改)
}
