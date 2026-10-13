package com.workflow.entity.excel.model;

/** 管理接口 DTO，草稿与发布内容独立返回，避免保存草稿影响运行按钮。 */
public final class ExcelConfigurationModels {
    private ExcelConfigurationModels() {}
    public record Defaults(int schemaVersion, String defaultImportSchemeCode, String defaultExportSchemeCode) {}
    public record SettingsView(int revision, Defaults draftConfig, Defaults publishedConfig,
                               String importHash, String exportHash) {}
    public record SchemeView(String id, String schemeCode, String name, int revision, String status,
                             ExcelSchemeDocument draftConfig, ExcelSchemeDocument publishedConfig,
                             String contentHash, boolean defaultImport, boolean defaultExport,
                             boolean draftDefaultImport, boolean draftDefaultExport, int settingsRevision) {
        /** 迁移适配器等旧调用只携带发布信息；线上管理查询会补齐实体默认草稿及其修订号。 */
        public SchemeView(String id, String schemeCode, String name, int revision, String status,
                          ExcelSchemeDocument draftConfig, ExcelSchemeDocument publishedConfig,
                          String contentHash, boolean defaultImport, boolean defaultExport) {
            this(id, schemeCode, name, revision, status, draftConfig, publishedConfig, contentHash,
                    defaultImport, defaultExport, defaultImport, defaultExport, 0);
        }
    }
    /** 默认勾选随方案草稿保存；null 表示旧调用未提交此项，不能被解释为取消默认。 */
    public record SaveScheme(String id, Integer expectedRevision, String schemeCode, String name,
                             ExcelSchemeDocument config, Boolean defaultImport, Boolean defaultExport,
                             Integer expectedSettingsRevision) {
        public SaveScheme(String id, Integer expectedRevision, String schemeCode, String name, ExcelSchemeDocument config) {
            this(id, expectedRevision, schemeCode, name, config, null, null, null);
        }
    }
    public record SaveSettings(Integer expectedRevision, String defaultImportSchemeCode,
                               String defaultExportSchemeCode) {}
    /** 新版方案发布同时校验默认草稿修订号；旧版和迁移调用省略它时仅发布方案内容。 */
    public record RevisionRequest(Integer expectedRevision, Integer expectedSettingsRevision) {
        public RevisionRequest(Integer expectedRevision) { this(expectedRevision, null); }
    }
    public record StatusRequest(Integer expectedRevision, String status) {}
    public record GenerateRequest(String schemeCode, String name) {}
    public record Reference(String ownerType, String ownerId, String operation, String mode, boolean published) {}
}
