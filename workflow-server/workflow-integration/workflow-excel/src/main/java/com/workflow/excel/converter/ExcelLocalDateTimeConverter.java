package com.workflow.excel.converter;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** LocalDateTime 转换器，默认导出 yyyy-MM-dd HH:mm:ss 并保留非零小数秒；纯日期导入为当天零点。 */
public final class ExcelLocalDateTimeConverter extends AbstractExcelDateConverter<LocalDateTime> {
    public ExcelLocalDateTimeConverter() {
        super(LocalDateTime.class, false);
    }

    @Override
    protected LocalDateTime fromLocalDateTime(LocalDateTime value, ZoneId zone) {
        return value;
    }

    @Override
    protected LocalDateTime toLocalDateTime(LocalDateTime value, ZoneId zone) {
        return value;
    }
}
