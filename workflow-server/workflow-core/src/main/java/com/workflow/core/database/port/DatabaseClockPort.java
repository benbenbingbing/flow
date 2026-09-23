package com.workflow.core.database.port;

import java.time.LocalDateTime;

/** 数据库 UTC 时钟；返回无时区的 UTC 墙钟值，用于现有队列时间列，不使用应用节点时钟。 */
public interface DatabaseClockPort {
    /**
     * 处理UTC当前时间，并将结果传给后续步骤。
     *
     * @return 处理后的UTC当前时间结果，供调用方继续处理
     */
    LocalDateTime utcNow();
}
