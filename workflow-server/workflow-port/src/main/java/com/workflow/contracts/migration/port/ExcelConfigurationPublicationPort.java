package com.workflow.contracts.migration.port;

/** Excel 方案或默认配置发布后更新所属 ENTITY 迁移资产，确保冲突比较能识别独立配置变更。 */
public interface ExcelConfigurationPublicationPort {
    void recordPublished(String entityId);
}
