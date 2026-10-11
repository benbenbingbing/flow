package com.workflow.excel.converter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

/** java.util.Date 转换器，使用 ExcelDateFormat 的显式时区，默认 Asia/Shanghai，不依赖 JVM 默认时区。 */
public final class ExcelDateConverter extends AbstractExcelDateConverter<Date> {
    public ExcelDateConverter() {
        super(Date.class, false);
    }

    /** Date 需要确定的时间点，夏令时造成不存在或重复的本地时间时拒绝猜测偏移。 */
    @Override
    protected Date fromLocalDateTime(LocalDateTime value, ZoneId zone) {
        List<ZoneOffset> offsets = zone.getRules().getValidOffsets(value);
        if (offsets.size() != 1) {
            throw new IllegalArgumentException("日期时间在指定时区不存在或存在歧义：" + zone);
        }
        return Date.from(value.toInstant(offsets.get(0)));
    }

    /** 按配置时区展示时间点，保留 Date 的毫秒精度。 */
    @Override
    protected LocalDateTime toLocalDateTime(Date value, ZoneId zone) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(value.getTime()), zone);
    }
}
