package com.workflow.excel.converter;

import com.alibaba.excel.converters.string.StringNumberConverter;
import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.metadata.GlobalConfiguration;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.metadata.property.ExcelContentProperty;
import java.lang.annotation.Annotation;

/** 转换器共用的字段配置及单元格类型处理，不把异常或未知单元格悄悄转换为 null。 */
final class ConverterSupport {
    private static final StringNumberConverter NUMBER_TO_STRING = new StringNumberConverter();

    private ConverterSupport() {
    }

    static <A extends Annotation> A annotation(ExcelContentProperty property, Class<A> type) {
        return property == null || property.getField() == null ? null : property.getField().getAnnotation(type);
    }

    static String text(ReadCellData<?> cell, ExcelContentProperty property, GlobalConfiguration global) {
        return switch (cell.getType()) {
            case EMPTY -> null;
            case STRING, DIRECT_STRING -> cell.getStringValue();
            // 沿用 Excel 数值显示格式，例如格式为 00000 的数字应读为 00123。
            case NUMBER -> NUMBER_TO_STRING.convertToJavaData(cell, property, global);
            case BOOLEAN -> String.valueOf(cell.getBooleanValue());
            default -> throw new IllegalArgumentException("不支持转换为文本的单元格类型：" + cell.getType());
        };
    }

    static WriteCellData<?> textCell(String value) {
        return value == null ? new WriteCellData<>(CellDataTypeEnum.EMPTY) : new WriteCellData<>(value);
    }
}
