package com.workflow.excel.converter;

import com.alibaba.excel.converters.Converter;
import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.metadata.GlobalConfiguration;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.metadata.property.ExcelContentProperty;
import com.workflow.excel.annotation.converter.ExcelStringFormat;
import com.workflow.excel.dynamic.ExcelValueCodec;
import com.workflow.excel.dynamic.ExcelRawCell;

/**
 * 文本清理转换器，通过 ExcelProperty.converter 绑定到 String 字段。
 * 数值单元格遵循原单元格显示格式，导出始终为文本，避免编码前导零或大整数文本被 Excel 改写。
 * 默认 strip 首尾空白并将空文本转为 null，可用 ExcelStringFormat 配置。
 */
public final class ExcelStringConverter implements Converter<String> {
    @Override
    public Class<?> supportJavaTypeKey() {
        return String.class;
    }

    @Override
    public CellDataTypeEnum supportExcelTypeKey() {
        return CellDataTypeEnum.STRING;
    }

    /** 将文本、数值或布尔单元格转换成清理后的文本，不能恢复源文件已经丢失的数值精度。 */
    @Override
    public String convertToJavaData(ReadCellData<?> cell, ExcelContentProperty property, GlobalConfiguration global) {
        ExcelValueCodec.requireLiteral(ExcelRawCell.from(cell, 0, false));
        return normalize(ConverterSupport.text(cell, property, global), property);
    }

    /** 按字段清理配置导出文本，null 或被转为 null 的空文本输出为空单元格。 */
    @Override
    public WriteCellData<?> convertToExcelData(String value, ExcelContentProperty property, GlobalConfiguration global) {
        return ConverterSupport.textCell(normalize(value, property));
    }

    private static String normalize(String value, ExcelContentProperty property) {
        if (value == null) {
            return null;
        }
        ExcelStringFormat format = ConverterSupport.annotation(property, ExcelStringFormat.class);
        return ExcelValueCodec.writeString(value, format == null || format.trim(), format == null || format.emptyToNull());
    }
}
