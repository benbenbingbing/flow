package com.workflow.core.database.port;

import java.time.LocalDateTime;

/** 数据库 UTC 时钟；返回无时区的 UTC 墙钟值，用于现有队列时间列，不使用应用节点时钟。 */
public interface DatabaseClockPort {
    LocalDateTime utcNow();
}
