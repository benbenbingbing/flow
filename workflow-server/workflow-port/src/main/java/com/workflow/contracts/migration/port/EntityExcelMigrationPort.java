package com.workflow.contracts.migration.port;

import java.util.Map;

/** Excel 配置迁移边界。文档只含稳定编码和发布规则，不允许任务、文件或运行令牌进入迁移包。 */
public interface EntityExcelMigrationPort {
    String SECTION = "excelConfiguration";

    /** 应用包内配置时不登记中间默认/状态快照；迁移服务会在最后统一登记最终资产。 */
    default boolean isPublicationPending() { return false; }

    /** 返回已发布配置；目标环境以 entityCode/schemeCode/formKey 重新建立本地身份。 */
    Map<String, Object> exportPublished(String entityCode);

    /** 对包内实体字段、写入表单和 Excel 引用做只读预检；不得保存草稿或发布。 */
    void validate(String entityCode, Map<String, Object> entitySnapshot);

    /** 仅在已启动的迁移事务内，暂用已预检配置验证发布环中的按钮引用。 */
    PublicationScope openPublicationScope(Map<String, Map<String, Object>> entitySnapshots);

    /** 实体与写入表单发布后，复用正常管理入口保存并发布配置，再核验按钮引用。 */
    void applyPublished(String entityCode, Map<String, Object> entitySnapshot);

    /** 回滚时仅清除本次新增的方案；正常引用保护仍生效，不删除任务或业务数据。 */
    void removeAddedSchemes(String entityCode, Map<String, Object> previousSnapshot, Map<String, Object> importedSnapshot);

    interface PublicationScope extends AutoCloseable {
        PublicationScope NONE = () -> { };
        @Override void close();
    }
}
