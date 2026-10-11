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
import java.util.regex.Pattern;

/**
 * BigDecimal 金额转换器，读取数值或十进制文本，默认保留两位小数且禁止隐式舍入。
 * 通过 ExcelAmountFormat 配置千分位、可选货币前缀及导出类型，不承担币种换算。
 */
public final class ExcelAmountConverter implements Converter<BigDecimal> {
    private static final Pattern PLAIN = Pattern.compile("[+-]?[0-9]+(?:\\.[0-9]+)?");
    private static final Pattern GROUPED = Pattern.compile("[+-]?[0-9]{1,3}(?:,[0-9]{3})+(?:\\.[0-9]+)?");

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
        if (cell.getType() == CellDataTypeEnum.EMPTY) {
            return null;
        }
        BigDecimal amount;
        if (cell.getType() == CellDataTypeEnum.NUMBER) {
            amount = cell.getNumberValue();
        } else if (cell.getType() == CellDataTypeEnum.STRING || cell.getType() == CellDataTypeEnum.DIRECT_STRING) {
            String text = cell.getStringValue().strip();
            if (text.isEmpty()) {
                return null;
            }
            String symbol = format == null ? "" : format.currencySymbol();
            if (!symbol.isEmpty() && text.startsWith(symbol)) {
                text = text.substring(symbol.length()).strip();
            }
            boolean grouped = (format == null || format.allowThousands()) && GROUPED.matcher(text).matches();
            if (!PLAIN.matcher(text).matches() && !grouped) {
                throw new IllegalArgumentException("金额必须是十进制数，千分位须按每组三位填写");
            }
            amount = new BigDecimal(text.replace(",", ""));
        } else {
            throw new IllegalArgumentException("金额不支持此单元格类型：" + cell.getType());
        }
        return scale(amount, format);
    }

    /** 默认按文本保留金额精度；显式选择数值导出时拒绝超过 Excel 有效数字限制的金额。 */
    @Override
    public WriteCellData<?> convertToExcelData(BigDecimal value, ExcelContentProperty property, GlobalConfiguration global) {
        if (value == null) {
            return ConverterSupport.textCell(null);
        }
        ExcelAmountFormat format = ConverterSupport.annotation(property, ExcelAmountFormat.class);
        BigDecimal amount = scale(value, format);
        if (format == null || format.writeAsText()) {
            return ConverterSupport.textCell(amount.toPlainString());
        }
        // Excel 数值单元格无法保证超过 15 位有效数字的精度，不能以成功导出掩盖金额变化。
        if (amount.stripTrailingZeros().precision() > 15 || !Double.isFinite(amount.doubleValue())) {
            throw new IllegalArgumentException("金额超过 Excel 数值精度限制，请使用文本导出");
        }
        return new WriteCellData<>(amount);
    }

    private static BigDecimal scale(BigDecimal amount, ExcelAmountFormat format) {
        int scale = format == null ? 2 : format.scale();
        if (scale < 0) {
            throw new IllegalArgumentException("金额的小数位数不能为负数");
        }
        RoundingMode rounding = format == null ? RoundingMode.UNNECESSARY : format.roundingMode();
        return amount.setScale(scale, rounding);
    }
}
