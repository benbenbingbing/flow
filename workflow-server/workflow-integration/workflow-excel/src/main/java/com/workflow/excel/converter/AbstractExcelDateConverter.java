package com.workflow.excel.converter;

import com.alibaba.excel.converters.Converter;
import com.alibaba.excel.converters.ReadConverterContext;
import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.metadata.GlobalConfiguration;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.metadata.property.ExcelContentProperty;
import com.workflow.excel.annotation.converter.ExcelDateFormat;
import com.workflow.excel.dynamic.ExcelRawCell;
import com.workflow.excel.dynamic.ExcelValueCodec;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
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
        DateSettings config = settings(property);
        LocalDateTime value = config.format().read(ExcelRawCell.from(cell, 0, use1904));
        return value == null ? null : fromLocalDateTime(value, config.zone());
    }

    /** 按字段格式输出日期文本，默认日期时间格式保留非零小数秒；不依赖 Excel 或机器的日期显示设置。 */
    @Override
    public final WriteCellData<?> convertToExcelData(T value, ExcelContentProperty property, GlobalConfiguration global) {
        if (value == null) {
            return ConverterSupport.textCell(null);
        }
        DateSettings config = settings(property);
        LocalDateTime dateTime = toLocalDateTime(value, config.zone());
        return ConverterSupport.textCell(config.format().write(dateTime));
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

    private record DateSettings(ExcelValueCodec.DateFormat format, ZoneId zone) {
        private static DateSettings create(ExcelDateFormat format, boolean dateOnly) {
            return new DateSettings(ExcelValueCodec.dateFormat(format == null ? "" : format.value(),
                    format == null ? List.of() : List.of(format.readPatterns()), dateOnly),
                    ZoneId.of(format == null ? "Asia/Shanghai" : format.zoneId()));
        }
    }
}
