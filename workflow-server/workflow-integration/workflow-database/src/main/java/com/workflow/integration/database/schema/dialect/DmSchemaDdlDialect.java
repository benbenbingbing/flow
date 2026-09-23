package com.workflow.integration.database.schema.dialect;

import com.workflow.integration.database.api.DatabaseVendor;

/** 达梦 DM8，使用 Oracle 兼容的类型、标识符和 DDL。 */
public final class DmSchemaDdlDialect extends OracleSchemaDdlDialect {
    /**
     * 处理供应商，并将结果传给后续步骤。
     *
     * @return 处理后的供应商结果，供调用方继续处理
     */
    @Override public DatabaseVendor vendor() { return DatabaseVendor.DM; }
}
