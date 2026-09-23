package com.workflow.entity.form.application;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 前后端共享比较契约：有效本地日期/秒级日期时间、精确十进制，禁止隐式时区转换。 */
public final class CrossFieldValueComparator {
    private static final Pattern DECIMAL = Pattern.compile("([+-]?)(\\d+)(?:\\.(\\d+))?(?:[eE]([+-]?\\d{1,5}))?");
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern DATETIME = Pattern.compile("\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}(?:\\.0{1,9})?");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);

    /**
     * 初始化跨字段值{@code comparator}，保存构造参数供后续方法使用。
     */
    private CrossFieldValueComparator() {}

    /**
     * 返回 -1、0、1；非空非法值抛出异常，由表单终检转为所属字段错误。
     *
     * @param left 左侧，作为 {@code date} 的输入影响后续处理
     * @param right 右侧，供本方法比较跨字段值{@code comparator}时使用
     * @param leftType 左侧类型标识，决定后续跨字段值{@code comparator}采用的处理分支
     * @param rightType 右侧类型标识，决定后续跨字段值{@code comparator}采用的处理分支
     * @return 比较后的跨字段值{@code comparator}结果，供调用方继续处理
     */
    public static int compare(Object left, Object right, String leftType, String rightType) {
        if (!FormCrossFieldRulePolicy.compatible(leftType, rightType)) throw new IllegalArgumentException("比较字段类型不兼容");
        int result = switch (leftType) {
            case "DATE" -> date(left).compareTo(date(right));
            case "DATETIME" -> dateTime(left).compareTo(dateTime(right));
            default -> decimal(left, leftType).compareTo(decimal(right, rightType));
        };
        return Integer.signum(result);
    }

    /**
     * 处理日期，并将结果传给后续步骤。
     *
     * @param value 待处理日期的原始输入，结果供调用方继续使用
     * @return 处理后的日期结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private static LocalDate date(Object value) {
        try {
            LocalDate result;
            if (value instanceof LocalDate localDate) result = localDate;
            else if (value instanceof java.sql.Date sqlDate) result = sqlDate.toLocalDate();
            else if (value instanceof String text && DATE.matcher(text).matches()) result = LocalDate.parse(text);
            else throw new IllegalArgumentException();
            if (result.getYear() < 1 || result.getYear() > 9999) throw new IllegalArgumentException();
            return result;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("不是有效的日期", exception);
        }
    }

    /**
     * 处理日期时间，并将结果传给后续步骤。
     *
     * @param value 待处理日期时间的原始输入，结果供调用方继续使用
     * @return 处理后的日期时间结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private static LocalDateTime dateTime(Object value) {
        try {
            LocalDateTime result;
            if (value instanceof LocalDateTime dateTime) result = dateTime;
            else if (value instanceof Timestamp timestamp) result = timestamp.toLocalDateTime();
            else if (value instanceof String text && DATETIME.matcher(text).matches()) {
                result = LocalDateTime.parse(text.substring(0, 19).replace('T', ' '), DATETIME_FORMAT);
            } else throw new IllegalArgumentException();
            if (result.getYear() < 1 || result.getYear() > 9999 || result.getNano() != 0) throw new IllegalArgumentException();
            return result;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("不是有效的日期时间（精确到秒且不含时区偏移）", exception);
        }
    }

    /**
     * 字符串保持精度，数据库整数/BigDecimal 原值无需经过浮点转换。
     *
     * @param value 待处理{@code decimal}的原始输入，结果供调用方继续使用
     * @param type 类型标识，决定后续{@code decimal}采用的处理分支
     * @return 处理后的{@code decimal}结果，供调用方继续处理
     */
    private static BigDecimal decimal(Object value, String type) {
        if (!(value instanceof String) && !(value instanceof Number)) throw new IllegalArgumentException("不是有效的数字");
        String text = value.toString();
        Matcher matcher = DECIMAL.matcher(text);
        if (text.length() > 500 || !matcher.matches()) throw new IllegalArgumentException("不是有效的数字");
        if (matcher.group(4) != null && Math.abs(Integer.parseInt(matcher.group(4))) > 10000) throw new IllegalArgumentException("数值指数超出支持范围");
        BigDecimal decimal = new BigDecimal(text);
        if (("INTEGER".equals(type) || "LONG".equals(type)) && decimal.stripTrailingZeros().scale() > 0) throw new IllegalArgumentException("必须为整数");
        return decimal;
    }
}
