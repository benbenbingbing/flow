package com.workflow.integration.database.schema.dialect;

import com.workflow.integration.database.api.DatabaseVendor;

/** OceanBase MySQL 租户方言，与 Oracle 租户分开选择。 */
public final class OceanBaseMySqlSchemaDdlDialect extends MySqlSchemaDdlDialect {
    /**
     * 处理供应商，并将结果传给后续步骤。
     *
     * @return 处理后的供应商结果，供调用方继续处理
     */
    @Override public DatabaseVendor vendor() { return DatabaseVendor.OCEANBASE_MYSQL; }
}
