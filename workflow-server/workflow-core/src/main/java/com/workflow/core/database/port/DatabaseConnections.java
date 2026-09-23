package com.workflow.core.database.port;

import javax.sql.DataSource;

/**
 * 不参与业务连接池和事务的专用连接入口。
 * 每次 getConnection 都必须创建独立物理连接；close 必须关闭会话，而非归还业务池。
 */
public interface DatabaseConnections {
    /** 普通数据库身份，用于发布互斥、队列入库与结果轮询。 */
    DataSource application();
    /** 具有运行时结构发布权限的身份，用于 DDL。 */
    DataSource schema();
}
