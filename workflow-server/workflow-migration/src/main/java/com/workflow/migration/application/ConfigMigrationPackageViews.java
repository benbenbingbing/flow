package com.workflow.migration.application;

import com.workflow.migration.infrastructure.persistence.record.ConfigExportPackage;
import com.workflow.migration.infrastructure.persistence.record.ConfigImportPackage;
import java.util.LinkedHashMap;
import java.util.Map;

/** 列表查询与导入/导出操作共用的轻量响应投影，不读取包内容或大快照。 */
final class ConfigMigrationPackageViews {
    private ConfigMigrationPackageViews() {}

    /**
     * 整理导出摘要数据，供调用方遍历或继续处理。
     *
     * @param value 待处理导出摘要的原始输入，结果供调用方继续使用
     * @return 导出摘要键值结果，供调用方继续处理
     */
    static Map<String, Object> exportSummary(ConfigExportPackage value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", value.getId());
        result.put("packageNo", value.getPackageNo());
        result.put("migrationTag", value.getMigrationTag());
        result.put("fileName", value.getFileName());
        result.put("checksum", value.getChecksum());
        result.put("status", value.getStatus());
        result.put("assetCount", value.getAssetCount());
        result.put("createdBy", value.getCreatedBy());
        result.put("createdAt", value.getCreatedAt());
        result.put("downloadCount", value.getDownloadCount());
        result.put("lastDownloadAt", value.getLastDownloadAt());
        return result;
    }

    /**
     * 整理导入摘要数据，供调用方遍历或继续处理。
     *
     * @param value 待处理导入摘要的原始输入，结果供调用方继续使用
     * @return 导入摘要键值结果，供调用方继续处理
     */
    static Map<String, Object> importSummary(ConfigImportPackage value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", value.getId());
        result.put("packageNo", value.getPackageNo());
        result.put("sourceEnvironment", value.getSourceEnvironment());
        result.put("signatureStatus", value.getSignatureStatus());
        result.put("signatureConfirmedBy", value.getSignatureConfirmedBy());
        result.put("signatureConfirmedAt", value.getSignatureConfirmedAt());
        result.put("migrationTag", value.getMigrationTag());
        result.put("fileName", value.getFileName());
        result.put("checksum", value.getChecksum());
        result.put("status", value.getStatus());
        result.put("importedBy", value.getImportedBy());
        result.put("importedAt", value.getImportedAt());
        result.put("publishedBy", value.getPublishedBy());
        result.put("publishedAt", value.getPublishedAt());
        result.put("errorMessage", value.getErrorMessage());
        return result;
    }
}
