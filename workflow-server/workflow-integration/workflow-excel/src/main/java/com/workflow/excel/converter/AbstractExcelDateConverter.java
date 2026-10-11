package com.workflow.excel.converter;

import com.alibaba.excel.converters.Converter;
import com.alibaba.excel.converters.ReadConverterContext;
import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.metadata.GlobalConfiguration;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.metadata.property.ExcelContentProperty;
import com.alibaba.excel.util.DateUtils;
import com.workflow.excel.annotation.converter.ExcelDateFormat;
import java.lang.reflect.Field;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.chrono.IsoEra;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 日期转换共用严格解析、原生日期序号及字段格式缓存；不使用有共享可变状态的 SimpleDateFormat。 */
abstract class AbstractExcelDateConverter<T> implements Converter<T> {
    private final Class<T> javaType;
    private final boolean dateOnly;
    private final DateSettings defaults;
    // ClassValue 允许 DTO 类卸载；按字段缓存不可变 formatter，避免每行重复构建格式解析器。
    private final ClassValue<ConcurrentMap<String, DateSettings>> settings = new ClassValue<>() {
        @Override
        protected ConcurrentMap<String, DateSettings> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    protected AbstractExcelDateConverter(Class<T> javaType, boolean dateOnly) {
        this.javaType = javaType;
        this.dateOnly = dateOnly;
        this.defaults = DateSettings.create(null, dateOnly);
    }

    @Override
    public final Class<?> supportJavaTypeKey() {
        return javaType;
    }

    @Override
    public final CellDataTypeEnum supportExcelTypeKey() {
        return CellDataTypeEnum.STRING;
    }

    /** 读取严格日期文本或 Excel 日期序号；无效日期报错，空文本返回 null。 */
    @Override
    public final T convertToJavaData(ReadCellData<?> cell, ExcelContentProperty property, GlobalConfiguration global) {
        return convert(cell, property, Boolean.TRUE.equals(global.getUse1904windowing()));
    }

    /** 从实际工作簿识别日期基准，弥补 EasyExcel 默认只读取手动配置的 use1904windowing 的行为。 */
    @Override
    public final T convertToJavaData(ReadConverterContext<?> context) {
        GlobalConfiguration global = context.getAnalysisContext().currentReadHolder().globalConfiguration();
        boolean use1904 = context.getReadCellData().getType() == CellDataTypeEnum.NUMBER
                ? DateWorkbookSupport.use1904(context.getAnalysisContext().readWorkbookHolder())
                : Boolean.TRUE.equals(global.getUse1904windowing());
        return convert(context.getReadCellData(), context.getContentProperty(), use1904);
    }

    private T convert(ReadCellData<?> cell, ExcelContentProperty property, boolean use1904) {
        if (cell.getType() == CellDataTypeEnum.EMPTY) {
            return null;
        }
        DateSettings config = settings(property);
        LocalDateTime dateTime;
        if (cell.getType() == CellDataTypeEnum.NUMBER) {
            double serial = cell.getNumberValue().doubleValue();
            if (!Double.isFinite(serial) || !DateUtils.isValidExcelDate(serial)) {
                throw new IllegalArgumentException("Excel 日期序号不合法");
            }
            // 必须使用工作簿的日期基准，不能把 1904 日期系统的序号按 1900 系统解析。
            dateTime = DateUtils.getLocalDateTime(serial, use1904);
            if (dateTime == null || dateTime.getYear() > 9999) {
                throw new IllegalArgumentException("Excel 日期超出支持范围");
            }
        } else if (cell.getType() == CellDataTypeEnum.STRING || cell.getType() == CellDataTypeEnum.DIRECT_STRING) {
            String text = cell.getStringValue().strip();
            if (text.isEmpty()) {
                return null;
            }
            dateTime = config.parse(text);
        } else {
            throw new IllegalArgumentException("日期不支持此单元格类型：" + cell.getType());
        }
        return fromLocalDateTime(dateTime, config.zone());
    }

    /** 按字段格式输出日期文本，默认日期时间格式保留非零小数秒；不依赖 Excel 或机器的日期显示设置。 */
    @Override
    public final WriteCellData<?> convertToExcelData(T value, ExcelContentProperty property, GlobalConfiguration global) {
        if (value == null) {
            return ConverterSupport.textCell(null);
        }
        DateSettings config = settings(property);
        LocalDateTime dateTime = toLocalDateTime(value, config.zone());
        return ConverterSupport.textCell(config.writer().format(dateOnly ? dateTime.toLocalDate() : dateTime));
    }

    protected abstract T fromLocalDateTime(LocalDateTime value, ZoneId zone);

    protected abstract LocalDateTime toLocalDateTime(T value, ZoneId zone);

    private DateSettings settings(ExcelContentProperty property) {
        Field field = property == null ? null : property.getField();
        if (field == null) {
            return defaults;
        }
        return settings.get(field.getDeclaringClass()).computeIfAbsent(field.getName(),
                name -> DateSettings.create(field.getAnnotation(ExcelDateFormat.class), dateOnly));
    }

    private record DateSettings(DateTimeFormatter writer, List<DateTimeFormatter> readers, ZoneId zone) {
        private static DateSettings create(ExcelDateFormat format, boolean dateOnly) {
            String pattern = format == null ? "" : format.value();
            DateTimeFormatter writer;
            if (!pattern.isEmpty()) {
                writer = formatter(pattern);
            } else if (dateOnly) {
                writer = formatter("yyyy-MM-dd");
            } else {
                writer = builder("yyyy-MM-dd HH:mm:ss").optionalStart()
                        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd()
                        .toFormatter(Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
            }
            List<DateTimeFormatter> readers = new ArrayList<>();
            readers.add(writer);
            if (format != null && format.readPatterns().length > 0) {
                for (String readPattern : format.readPatterns()) {
                    readers.add(formatter(readPattern));
                }
            } else {
                if (!dateOnly) {
                    readers.add(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    readers.add(builder("yyyy/MM/dd HH:mm:ss").optionalStart()
                            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd()
                            .toFormatter(Locale.ROOT).withResolverStyle(ResolverStyle.STRICT));
                    readers.add(formatter("yyyy-MM-dd HH:mm"));
                    readers.add(formatter("yyyy/MM/dd HH:mm"));
                }
                readers.add(formatter("yyyy-MM-dd"));
                readers.add(formatter("yyyy/MM/dd"));
                readers.add(formatter("yyyyMMdd"));
            }
            ZoneId zone = ZoneId.of(format == null ? "Asia/Shanghai" : format.zoneId());
            return new DateSettings(writer, List.copyOf(readers), zone);
        }

        private LocalDateTime parse(String text) {
            for (DateTimeFormatter reader : readers) {
                try {
                    TemporalAccessor value = reader.parse(text);
                    // 字段表达本地时间，不能静默丢弃输入携带的 UTC 偏移或时区后再按另一个时区解释。
                    if (value.isSupported(ChronoField.OFFSET_SECONDS) || value.query(TemporalQueries.zone()) != null) {
                        throw new IllegalArgumentException("日期文本不能包含时区或 UTC 偏移，请通过 zoneId 配置 Date 的时区");
                    }
                    return value.isSupported(ChronoField.HOUR_OF_DAY)
                            ? LocalDateTime.from(value) : LocalDate.from(value).atStartOfDay();
                } catch (DateTimeException ignored) {
                    // 仅当前格式不匹配时尝试下一格式；非法日期不会通过 lenient 解析被调整成另一日期。
                }
            }
            throw new IllegalArgumentException("日期格式不正确或日期不存在");
        }

        private static DateTimeFormatter formatter(String pattern) {
            if (pattern.isBlank()) {
                throw new IllegalArgumentException("日期格式不能为空白");
            }
            return builder(pattern).toFormatter(Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
        }

        private static DateTimeFormatterBuilder builder(String pattern) {
            // yyyy 表示 year-of-era，严格解析时显式提供公元纪元，避免合法的 yyyy-MM-dd 无法还原日期。
            return new DateTimeFormatterBuilder().appendPattern(pattern)
                    .parseDefaulting(ChronoField.ERA, IsoEra.CE.getValue());
        }
    }
}
