package com.workflow.integration.database.api;

/** 数据库产品及兼容模式；OceanBase 必须明确租户模式，不能只凭产品名推断。 */
public enum DatabaseVendor {
    MYSQL, ORACLE, POSTGRESQL, KINGBASE, DM, OCEANBASE_MYSQL, OCEANBASE_ORACLE
}
