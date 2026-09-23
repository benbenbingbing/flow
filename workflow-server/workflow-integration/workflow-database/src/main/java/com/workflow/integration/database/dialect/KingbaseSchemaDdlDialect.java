package com.workflow.integration.database.dialect;

import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.SchemaColumn;
import com.workflow.integration.database.schema.AuditTimestampDdl;
import java.util.List;

/** KingbaseES V8/V9 的 PostgreSQL 兼容模式。 */
public final class KingbaseSchemaDdlDialect extends PostgresSchemaDdlDialect {
    @Override public DatabaseVendor vendor() { return DatabaseVendor.KINGBASE; }
    @Override protected List<String> auditTimestamp(String table, SchemaColumn column) {
        // Kingbase 使用内置 plsql 语言，不要求额外安装 PostgreSQL 的兼容语言或系统视图。
        return column.refreshTimestampOnUpdate() ? AuditTimestampDdl.kingbase(quoteIdentifier(table),
                quoteIdentifier(column.name()), quoteIdentifier(objectName("fn", table, column.name())),
                quoteIdentifier(objectName("tr", table, column.name()))) : List.of();
    }
}
