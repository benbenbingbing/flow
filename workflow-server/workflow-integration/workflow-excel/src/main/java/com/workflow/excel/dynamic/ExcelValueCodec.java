package com.workflow.excel.dynamic;

import com.alibaba.excel.util.DateUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.chrono.IsoEra;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.DataFormatter;

/**
 * 可由普通参数驱动的纯值转换，不访问实体、数据库或 Spring。
 * 所有读取方法拒绝公式和错误单元格；缺失单元格按空值处理，是否必填由业务校验决定。
 * 导出返回 String/BigDecimal/null，直接用于 ExcelUtils.writeRowsBatches，文本不会被当作公式执行。
 */
public final class ExcelValueCodec {
    private static final Pattern PLAIN = Pattern.compile("[+-]?[0-9]+(?:\\.[0-9]+)?");
    private static final Pattern GROUPED = Pattern.compile("[+-]?[0-9]{1,3}(?:,[0-9]{3})+(?:\\.[0-9]+)?");

    private ExcelValueCodec() { }

    /** 按原单元格数字显示格式读取编码（例如 123/00000 得到 00123），再执行文本清理。 */
    public static String readString(ExcelRawCell cell, boolean trim, boolean emptyToNull) {
        requireLiteral(cell);
        if (cell == null || cell.type() == ExcelCellType.EMPTY) return null;
        String text;
        if (cell.type() == ExcelCellType.NUMBER) {
            BigDecimal number = (BigDecimal) cell.value();
            text = cell.format() == null || cell.format().isBlank() || "General".equalsIgnoreCase(cell.format())
                    ? number.stripTrailingZeros().toPlainString()
                    : new DataFormatter(Locale.ROOT).formatRawCellContents(number.doubleValue(),
                            cell.formatIndex() == null ? 0 : cell.formatIndex(), cell.format(), cell.use1904Windowing());
        } else {
            text = cell.value() == null ? null : cell.value().toString();
        }
        return writeString(text, trim, emptyToNull);
    }

    /** 不将以 =、+、-、@ 开头的文本变成公式；具体写入使用字符串单元格。 */
    public static String writeString(String value, boolean trim, boolean emptyToNull) {
        if (value == null) return null;
        String result = trim ? value.strip() : value;
        return result.isEmpty() && emptyToNull ? null : result;
    }

    /** 完整校验金额语法后解析，禁止把错误千分位或未知币种前缀静默删除。 */
    public static BigDecimal readAmount(ExcelRawCell cell, int scale, RoundingMode rounding,
            boolean allowThousands, String currencySymbol) {
        requireLiteral(cell);
        requireScale(scale, rounding);
        if (cell == null || cell.type() == ExcelCellType.EMPTY) return null;
        BigDecimal amount;
        if (cell.type() == ExcelCellType.NUMBER) {
            amount = (BigDecimal) cell.value();
        } else if (cell.type() == ExcelCellType.STRING) {
            String text = cell.value() == null ? "" : cell.value().toString().strip();
            if (text.isEmpty()) return null;
            String symbol = currencySymbol == null ? "" : currencySymbol;
            if (!symbol.isEmpty() && text.startsWith(symbol)) text = text.substring(symbol.length()).strip();
            if (!PLAIN.matcher(text).matches() && !(allowThousands && GROUPED.matcher(text).matches())) {
                throw new IllegalArgumentException("金额必须是十进制数，千分位须按每组三位填写");
            }
            amount = new BigDecimal(text.replace(",", ""));
        } else {
            throw new IllegalArgumentException("金额不支持此单元格类型：" + cell.type());
        }
        return amount.setScale(scale, rounding);
    }

    /** 默认建议文本保真；数值输出超过 Excel 的 15 位有效数字能力时拒绝导出。 */
    public static Object writeAmount(BigDecimal value, int scale, RoundingMode rounding, boolean writeAsText) {
        requireScale(scale, rounding);
        if (value == null) return null;
        BigDecimal amount = value.setScale(scale, rounding);
        if (writeAsText) return amount.toPlainString();
        if (amount.stripTrailingZeros().precision() > 15 || !Double.isFinite(amount.doubleValue())) {
            throw new IllegalArgumentException("金额超过 Excel 数值精度限制，请使用文本导出");
        }
        return amount;
    }

    private static void requireScale(int scale, RoundingMode rounding) {
        if (scale < 0) throw new IllegalArgumentException("金额的小数位数不能为负数");
        Objects.requireNonNull(rounding, "金额舍入规则不能为空");
    }

    public static LocalDate readDate(ExcelRawCell cell, String pattern, List<String> readPatterns) {
        LocalDateTime value = dateFormat(pattern, readPatterns, true).read(cell);
        return value == null ? null : value.toLocalDate();
    }

    public static LocalDateTime readDateTime(ExcelRawCell cell, String pattern, List<String> readPatterns) {
        return dateFormat(pattern, readPatterns, false).read(cell);
    }

    public static String writeDate(LocalDate value, String pattern) {
        return value == null ? null : dateFormat(pattern, List.of(), true).write(value.atStartOfDay());
    }

    public static String writeDateTime(LocalDateTime value, String pattern) {
        return value == null ? null : dateFormat(pattern, List.of(), false).write(value);
    }

    /** 编译不可变日期格式供同一列的所有行复用，避免逐单元格重建格式器。 */
    public static DateFormat dateFormat(String pattern, List<String> readPatterns, boolean dateOnly) {
        DateTimeFormatter writer;
        if (pattern != null && !pattern.isEmpty()) writer = formatter(pattern);
        else if (dateOnly) writer = formatter("yyyy-MM-dd");
        else writer = fractional("yyyy-MM-dd HH:mm:ss");
        List<DateTimeFormatter> readers = new ArrayList<>();
        readers.add(writer);
        if (readPatterns != null && !readPatterns.isEmpty()) {
            readPatterns.forEach(value -> readers.add(formatter(value)));
        } else {
            if (!dateOnly) {
                readers.add(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                readers.add(fractional("yyyy/MM/dd HH:mm:ss"));
                readers.add(formatter("yyyy-MM-dd HH:mm"));
                readers.add(formatter("yyyy/MM/dd HH:mm"));
            }
            readers.add(formatter("yyyy-MM-dd"));
            readers.add(formatter("yyyy/MM/dd"));
            readers.add(formatter("yyyyMMdd"));
        }
        return new DateFormat(writer, List.copyOf(readers), dateOnly);
    }

    /** 日期序号及本地日期文本的共用解析规则；不猜测、丢弃或转换文本中携带的时区。 */
    public record DateFormat(DateTimeFormatter writer, List<DateTimeFormatter> readers, boolean dateOnly) {
        public DateFormat {
            Objects.requireNonNull(writer, "导出日期格式不能为空");
            readers = List.copyOf(readers);
        }

        public LocalDateTime read(ExcelRawCell cell) {
            requireLiteral(cell);
            if (cell == null || cell.type() == ExcelCellType.EMPTY) return null;
            if (cell.type() == ExcelCellType.NUMBER) {
                double serial = ((BigDecimal) cell.value()).doubleValue();
                if (!Double.isFinite(serial) || !DateUtils.isValidExcelDate(serial)) {
                    throw new IllegalArgumentException("Excel 日期序号不合法");
                }
                LocalDateTime value = DateUtils.getLocalDateTime(serial, cell.use1904Windowing());
                if (value == null || value.getYear() > 9999) throw new IllegalArgumentException("Excel 日期超出支持范围");
                return value;
            }
            if (cell.type() != ExcelCellType.STRING) throw new IllegalArgumentException("日期不支持此单元格类型：" + cell.type());
            String text = cell.value() == null ? "" : cell.value().toString().strip();
            if (text.isEmpty()) return null;
            for (DateTimeFormatter reader : readers) {
                try {
                    TemporalAccessor value = reader.parse(text);
                    if (value.isSupported(ChronoField.OFFSET_SECONDS) || value.query(TemporalQueries.zone()) != null) {
                        throw new IllegalArgumentException("日期文本不能包含时区或 UTC 偏移，请通过 zoneId 配置 Date 的时区");
                    }
                    return value.isSupported(ChronoField.HOUR_OF_DAY)
                            ? LocalDateTime.from(value) : LocalDate.from(value).atStartOfDay();
                } catch (DateTimeException ignored) {
                    // 仅格式不匹配时继续；STRICT 禁止把 2 月 30 日自动修正为另一日期。
                }
            }
            throw new IllegalArgumentException("日期格式不正确或日期不存在");
        }

        public String write(LocalDateTime value) {
            return value == null ? null : writer.format(dateOnly ? value.toLocalDate() : value);
        }
    }

    private static DateTimeFormatter fractional(String pattern) {
        return builder(pattern).optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd()
                .toFormatter(Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
    }

    private static DateTimeFormatter formatter(String pattern) {
        if (pattern == null || pattern.isBlank()) throw new IllegalArgumentException("日期格式不能为空白");
        return builder(pattern).toFormatter(Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
    }

    private static DateTimeFormatterBuilder builder(String pattern) {
        // yyyy 是 year-of-era；STRICT 解析显式提供公元纪元，保证合法 yyyy-MM-dd 可还原。
        return new DateTimeFormatterBuilder().appendPattern(pattern).parseDefaulting(ChronoField.ERA, IsoEra.CE.getValue());
    }

    public static String readCode(ExcelRawCell cell, Map<String, String> codeToLabel, boolean allowCode, boolean strict) {
        return new CodeMapping(codeToLabel, allowCode, strict).read(cell);
    }

    public static String writeCode(String value, Map<String, String> codeToLabel, boolean strict) {
        return new CodeMapping(codeToLabel, true, strict).write(value);
    }

    /** 由业务层批量取得字典后构造一次；同列全部行共享这个不可变映射。 */
    public static final class CodeMapping {
        private final Map<String, String> codes;
        private final Map<String, String> labels;
        private final boolean allowCode;
        private final boolean strict;

        public CodeMapping(Map<String, String> codeToLabel, boolean allowCode, boolean strict) {
            Objects.requireNonNull(codeToLabel, "代码映射不能为空");
            if (codeToLabel.isEmpty()) throw new IllegalArgumentException("代码映射不能为空");
            Map<String, String> reverse = new LinkedHashMap<>();
            codeToLabel.forEach((code, label) -> {
                if (code == null || label == null || code.isBlank() || label.isBlank()
                        || !code.equals(code.strip()) || !label.equals(label.strip())) {
                    throw new IllegalArgumentException("映射的代码和名称不能空白或带有首尾空白");
                }
                if (reverse.putIfAbsent(label, code) != null) throw new IllegalArgumentException("映射的名称不能重复");
            });
            if (allowCode) reverse.forEach((label, code) -> {
                if (codeToLabel.containsKey(label) && !label.equals(code)) {
                    throw new IllegalArgumentException("显示名称与另一项代码冲突，无法确定导入含义");
                }
            });
            this.codes = Map.copyOf(codeToLabel);
            this.labels = Map.copyOf(reverse);
            this.allowCode = allowCode;
            this.strict = strict;
        }

        public String read(ExcelRawCell cell) {
            return readText(readString(cell, true, true));
        }

        /** 供强类型 DTO 的整数代码适配器复用；由其负责将原始数值还原成代码文本。 */
        public String readText(String text) {
            if (text == null || text.isBlank()) return null;
            text = text.strip();
            String code = labels.get(text);
            if (code != null) return code;
            if (allowCode && codes.containsKey(text)) return text;
            if (strict) throw new IllegalArgumentException("代码或显示名称不在配置的映射中");
            return text;
        }

        public String write(String code) {
            if (code == null) return null;
            String label = codes.get(code);
            if (label != null) return label;
            if (strict) throw new IllegalArgumentException("导出的代码不在配置的映射中");
            return code;
        }
    }

    /** 原始读取不立即中断文件，业务可逐格调用并收集这些类型错误。 */
    public static void requireLiteral(ExcelRawCell cell) {
        if (cell != null && cell.type() == ExcelCellType.FORMULA) throw new IllegalArgumentException("导入不支持公式单元格");
        if (cell != null && cell.type() == ExcelCellType.ERROR) throw new IllegalArgumentException("导入不支持错误单元格");
    }
}
