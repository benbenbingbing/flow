package com.workflow.integration.database.dialect;

import com.workflow.integration.database.api.DatabaseVendor;

/** OceanBase Oracle 租户方言，要求支持 Oracle 兼容 DDL 和触发器。 */
public final class OceanBaseOracleSchemaDdlDialect extends OracleSchemaDdlDialect {
    @Override public DatabaseVendor vendor() { return DatabaseVendor.OCEANBASE_ORACLE; }
}
