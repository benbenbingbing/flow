package com.workflow.excel.converter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** LocalDate 转换器，默认导出 yyyy-MM-dd，导入支持横线、斜线及 yyyyMMdd；原生日期取日期部分。 */
public final class ExcelLocalDateConverter extends AbstractExcelDateConverter<LocalDate> {
    public ExcelLocalDateConverter() {
        super(LocalDate.class, true);
    }

    @Override
    protected LocalDate fromLocalDateTime(LocalDateTime value, ZoneId zone) {
        return value.toLocalDate();
    }

    @Override
    protected LocalDateTime toLocalDateTime(LocalDate value, ZoneId zone) {
        return value.atStartOfDay();
    }
}
