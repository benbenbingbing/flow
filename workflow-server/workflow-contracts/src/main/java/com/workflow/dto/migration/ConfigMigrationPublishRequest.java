package com.workflow.dto.migration;

import lombok.Data;

/**
 * 配置迁移发布请求。
 * 描述一次配置发布/迁移的版本说明、是否标记为导出资产及迁移标签等信息。
 */
@Data
public class ConfigMigrationPublishRequest {

    /** 版本说明 */
    private String versionDescription;
    /** 是否标记为可导出资产，默认 true */
    private Boolean markForExport = Boolean.TRUE;
    /** 迁移标签 */
    private String migrationTag;
}
