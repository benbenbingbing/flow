package com.workflow.process.query.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/** 无时区的旧业务表日期按明确存储时区转换；引擎 Date 已是时间点，不重复叠加时区。 */
@Component
public class ProcessQueryTime {
    private final ZoneId storageZone;

    /** 部署使用非中国标准时间存储旧 DATETIME 时，应配置该值与历史数据一致。 */
    public ProcessQueryTime(@Value("${workflow.process.storage-zone:Asia/Shanghai}") String storageZone) {
        this.storageZone = ZoneId.of(storageZone);
    }

    public Instant instant(LocalDateTime value) { return value == null ? null : value.atZone(storageZone).toInstant(); }
    public Instant instant(Date value) { return value == null ? null : value.toInstant(); }

    /** 包含 from、不含 to；空时间仅在未指定任何时间条件时匹配。 */
    public boolean inRange(Instant value, Instant from, Instant to) {
        return (from == null || value != null && !value.isBefore(from))
                && (to == null || value != null && value.isBefore(to));
    }
}
