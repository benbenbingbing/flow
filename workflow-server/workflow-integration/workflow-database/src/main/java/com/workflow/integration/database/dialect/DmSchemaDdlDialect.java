package com.workflow.integration.database.dialect;

import com.workflow.integration.database.api.DatabaseVendor;

/** 达梦 DM8，使用 Oracle 兼容的类型、标识符和 DDL。 */
public final class DmSchemaDdlDialect extends OracleSchemaDdlDialect {
    @Override public DatabaseVendor vendor() { return DatabaseVendor.DM; }
}
