package com.workflow.integration.database.dialect;

import com.workflow.integration.database.api.DatabaseVendor;

/** OceanBase MySQL 租户方言，与 Oracle 租户分开选择。 */
public final class OceanBaseMySqlSchemaDdlDialect extends MySqlSchemaDdlDialect {
    @Override public DatabaseVendor vendor() { return DatabaseVendor.OCEANBASE_MYSQL; }
}
