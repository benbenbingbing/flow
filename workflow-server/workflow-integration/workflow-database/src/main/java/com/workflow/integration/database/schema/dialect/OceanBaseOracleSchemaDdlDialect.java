package com.workflow.integration.database.schema.dialect;

import com.workflow.integration.database.api.DatabaseVendor;

/** OceanBase Oracle 租户方言，要求支持 Oracle 兼容 DDL 和触发器。 */
public final class OceanBaseOracleSchemaDdlDialect extends OracleSchemaDdlDialect {
    /**
     * 处理供应商，并将结果传给后续步骤。
     *
     * @return 处理后的供应商结果，供调用方继续处理
     */
    @Override public DatabaseVendor vendor() { return DatabaseVendor.OCEANBASE_ORACLE; }
}
