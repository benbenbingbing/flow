package com.workflow.excel.converter;

import com.alibaba.excel.converters.Converter;
import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.metadata.GlobalConfiguration;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.metadata.property.ExcelContentProperty;
import com.workflow.excel.annotation.converter.ExcelAmountFormat;
import java.math.BigDecimal;
import java.math.RoundingMode;
import com.workflow.excel.dynamic.ExcelRawCell;
import com.workflow.excel.dynamic.ExcelValueCodec;

/**
 * BigDecimal 金额转换器，读取数值或十进制文本，默认保留两位小数且禁止隐式舍入。
 * 通过 ExcelAmountFormat 配置千分位、可选货币前缀及导出类型，不承担币种换算。
 */
public final class ExcelAmountConverter implements Converter<BigDecimal> {
    @Override
    public Class<?> supportJavaTypeKey() {
        return BigDecimal.class;
    }

    @Override
    public CellDataTypeEnum supportExcelTypeKey() {
        return CellDataTypeEnum.STRING;
    }

    /** 先校验完整金额文本及千分位分组，再解析 BigDecimal，防止错误逗号被删除后形成另一笔金额。 */
    @Override
    public BigDecimal convertToJavaData(ReadCellData<?> cell, ExcelContentProperty property, GlobalConfiguration global) {
        ExcelAmountFormat format = ConverterSupport.annotation(property, ExcelAmountFormat.class);
        return ExcelValueCodec.readAmount(ExcelRawCell.from(cell, 0, false), format == null ? 2 : format.scale(),
                format == null ? RoundingMode.UNNECESSARY : format.roundingMode(),
                format == null || format.allowThousands(), format == null ? "" : format.currencySymbol());
    }

    /** 默认按文本保留金额精度；显式选择数值导出时拒绝超过 Excel 有效数字限制的金额。 */
    @Override
    public WriteCellData<?> convertToExcelData(BigDecimal value, ExcelContentProperty property, GlobalConfiguration global) {
        ExcelAmountFormat format = ConverterSupport.annotation(property, ExcelAmountFormat.class);
        Object amount = ExcelValueCodec.writeAmount(value, format == null ? 2 : format.scale(),
                format == null ? RoundingMode.UNNECESSARY : format.roundingMode(), format == null || format.writeAsText());
        return amount instanceof BigDecimal number ? new WriteCellData<>(number)
                : ConverterSupport.textCell((String) amount);
    }
}
