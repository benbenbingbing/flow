package com.workflow.integration.database.schema.dialect;

import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.schema.SchemaColumn;
import com.workflow.integration.database.schema.template.AuditTimestampDdl;
import java.util.List;

/** KingbaseES V8/V9 的 PostgreSQL 兼容模式。 */
public final class KingbaseSchemaDdlDialect extends PostgresSchemaDdlDialect {
    /**
     * 处理供应商，并将结果传给后续步骤。
     *
     * @return 处理后的供应商结果，供调用方继续处理
     */
    @Override public DatabaseVendor vendor() { return DatabaseVendor.KINGBASE; }
    /**
     * 审计时间戳；供后续追溯或审计使用。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param column 列，作为 {@code quoteIdentifier} 的输入影响后续处理
     * @return {@code kingbase}结构DDL方言集合，供调用方遍历或展示
     */
    @Override protected List<String> auditTimestamp(String table, SchemaColumn column) {
        // Kingbase 使用内置 plsql 语言，不要求额外安装 PostgreSQL 的兼容语言或系统视图。
        return column.refreshTimestampOnUpdate() ? AuditTimestampDdl.kingbase(quoteIdentifier(table),
                quoteIdentifier(column.name()), quoteIdentifier(objectName("fn", table, column.name())),
                quoteIdentifier(objectName("tr", table, column.name()))) : List.of();
    }
}
