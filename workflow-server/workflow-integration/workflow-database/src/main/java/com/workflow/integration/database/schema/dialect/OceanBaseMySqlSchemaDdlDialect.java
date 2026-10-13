package com.workflow.integration.database.schema.dialect;

import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.schema.SchemaColumn;
import com.workflow.integration.database.api.schema.SchemaDefault;
import com.workflow.integration.database.api.schema.SchemaType;

/** OceanBase MySQL 租户方言，与 Oracle 租户分开选择。 */
public final class OceanBaseMySqlSchemaDdlDialect extends MySqlSchemaDdlDialect {
    /**
     * 处理供应商，并将结果传给后续步骤。
     *
     * @return 处理后的供应商结果，供调用方继续处理
     */
    @Override public DatabaseVendor vendor() { return DatabaseVendor.OCEANBASE_MYSQL; }

    /**
     * OB MySQL 不支持 TEXT/LONGTEXT 的非 NULL 数据库默认值，在生成计划时拒绝，
     * 让发布接口返回可修正的参数错误，避免执行到一半才因 SQL 失败留下部分结构。
     * 不能转成有长度上限的 VARCHAR、移除默认值或用 NULL 触发器替代：这些做法会改变
     * 大文本容量或“省略字段”和“显式写入 NULL”的业务语义。
     *
     * @param column 待发布的完整列定义；错误消息仅暴露字段名，不回显默认值内容
     * @return 经兼容性校验的列 DDL，普通字符列及无默认值的大文本沿用 MySQL 实现
     * @throws IllegalArgumentException 大文本列配置了当前 OceanBase 不支持的非 NULL 默认值
     */
    @Override public String columnDefinition(SchemaColumn column) {
        if ((column.type().kind() == SchemaType.Kind.TEXT || column.type().kind() == SchemaType.Kind.LARGE_TEXT)
                && column.defaultValue().kind() != SchemaDefault.Kind.NULL) {
            throw new IllegalArgumentException("字段「" + column.name() + "」：OceanBase MySQL 的 TEXT/LONGTEXT "
                    + "不支持非 NULL 数据库默认值，请清除字段默认值，并在表单或新增请求中显式提供该值");
        }
        return super.columnDefinition(column);
    }
}
