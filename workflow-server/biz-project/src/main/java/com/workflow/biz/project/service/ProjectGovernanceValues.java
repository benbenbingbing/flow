package com.workflow.biz.project.service;

import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.api.response.EntityDataDTO;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 项目治理规则使用的数据读取和规范化工具。
 */
final class ProjectGovernanceValues {

    /**
     * 初始化项目治理值集合，保存构造参数供后续方法使用。
     */
    private ProjectGovernanceValues() {
    }

    /**
     * 更新项目治理值集合；后续读取或执行将使用更新后的状态。
     *
     * @param status 目标状态，写入记录后供流程分支或列表查询使用
     * @param customData 自定义数据，作为 {@code update.put} 的输入影响后续处理
     * @return 项目治理值集合键值结果，供调用方继续处理
     */
    static Map<String, Object> update(
            String status,
            Map<String, Object> customData) {
        Map<String, Object> update = new LinkedHashMap<>();
        if (StringUtils.hasText(status)) {
            update.put("status", status);
        }
        update.put("data", new LinkedHashMap<>(customData));
        return update;
    }

    /**
     * 整理数据数据，供调用方遍历或继续处理。
     *
     * @param dto DTO，供本方法处理数据时使用
     * @return 数据键值结果，供调用方继续处理
     */
    static Map<String, Object> data(EntityDataDTO dto) {
        return dto.getData() == null
                ? Map.of() : dto.getData();
    }

    /**
     * 校验并获取实体；不满足约束时阻止后续处理。
     *
     * @param dto DTO，供本方法校验并获取实体时使用
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @throws BusinessConflictException 目标状态已被其他操作改变时抛出
     */
    static void requireEntity(
            EntityDataDTO dto,
            String entityCode) {
        if (dto == null
                || !entityCode.equals(dto.getEntityCode())) {
            throw new BusinessConflictException(
                    "PROJECT_ENTITY_CONTEXT_INVALID",
                    "流程动作未获得正确的业务实体上下文");
        }
    }

    /**
     * 校验并获取文本；不满足约束时阻止后续处理。
     *
     * @param source 待校验并获取文本的原始输入，结果供调用方继续使用
     * @param fieldCode 字段编码，后续用于校验并获取文本时定位或关联目标
     * @param message 消息，作为 {@code conflict} 的输入影响后续处理
     * @return 校验并获取后的文本文本，供调用方比较或展示
     */
    static String requireText(
            Map<String, Object> source,
            String fieldCode,
            String message) {
        String value = text(read(source, fieldCode));
        if (!StringUtils.hasText(value)) {
            conflict("PROJECT_REQUIRED_FIELD_MISSING", message);
        }
        return value;
    }

    /**
     * 校验日期范围；不满足约束时阻止后续处理。
     *
     * @param startValue 启动值，作为 {@code date} 的输入影响后续处理
     * @param endValue 结束值，作为 {@code date} 的输入影响后续处理
     * @param label 标签，后续用于校验日期范围时匹配或展示
     */
    static void validateDateRange(
            Object startValue,
            Object endValue,
            String label) {
        LocalDate start = date(startValue);
        LocalDate end = date(endValue);
        if (start == null
                || end == null
                || end.isBefore(start)) {
            conflict(
                    "PROJECT_DATE_RANGE_INVALID",
                    label + "结束日期不得早于开始日期");
        }
    }

    /**
     * 复制项目治理值集合；结果供后续流程传递或持久化。
     *
     * @param source 待复制项目治理值集合的原始输入，结果供调用方继续使用
     * @param target 目标，供本方法复制项目治理值集合时使用
     * @param sourceKey 来源键，后续用于授权校验、关联或幂等去重
     * @param targetKey 目标键，后续用于授权校验、关联或幂等去重
     */
    static void copy(
            Map<String, Object> source,
            Map<String, Object> target,
            String sourceKey,
            String targetKey) {
        Object value = read(source, sourceKey);
        if (value != null
                && (!(value instanceof String text)
                || !text.isBlank())) {
            target.put(targetKey, value);
        }
    }

    /**
     * 读取项目治理值集合；查询结果供调用方展示或继续处理。
     *
     * @param source 待读取项目治理值集合的原始输入，结果供调用方继续使用
     * @param snakeCaseKey {@code snake}分支键，后续用于授权校验、关联或幂等去重
     * @return 读取后的项目治理值集合结果，供调用方继续处理
     */
    static Object read(
            Map<String, Object> source,
            String snakeCaseKey) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        if (source.containsKey(snakeCaseKey)) {
            return source.get(snakeCaseKey);
        }
        StringBuilder camelCaseKey = new StringBuilder();
        boolean capitalizeNext = false;
        for (char character : snakeCaseKey.toCharArray()) {
            if (character == '_') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                camelCaseKey.append(
                        Character.toUpperCase(character));
                capitalizeNext = false;
            } else {
                camelCaseKey.append(character);
            }
        }
        return source.get(camelCaseKey.toString());
    }

    /**
     * 整理行数据，供调用方遍历或继续处理。
     *
     * @param value 待处理行的原始输入，结果供调用方继续使用
     * @return 项目治理值集合，供调用方遍历或展示
     */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> rows(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item ->
                        (Map<String, Object>) item)
                .toList();
    }

    /**
     * 处理{@code decimal}，并将结果传给后续步骤。
     *
     * @param value 待处理{@code decimal}的原始输入，结果供调用方继续使用
     * @return 处理后的{@code decimal}结果，供调用方继续处理
     */
    static BigDecimal decimal(Object value) {
        if (value == null
                || String.valueOf(value).isBlank()) {
            return BigDecimal.ZERO;
        }
        return value instanceof BigDecimal decimal
                ? decimal
                : new BigDecimal(String.valueOf(value));
    }

    /**
     * 处理日期，并将结果传给后续步骤。
     *
     * @param value 待处理日期的原始输入，结果供调用方继续使用
     * @return 处理后的日期结果，供调用方继续处理
     */
    static LocalDate date(Object value) {
        if (value == null
                || String.valueOf(value).isBlank()) {
            return null;
        }
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toLocalDate();
        }
        String text = String.valueOf(value);
        return LocalDate.parse(text.length() >= 10
                ? text.substring(0, 10) : text);
    }

    /**
     * 判断{@code bool}条件是否成立，供调用方选择后续分支。
     *
     * @param value 待处理{@code bool}的原始输入，结果供调用方继续使用
     * @return {@code bool}条件成立时为 true，否则为 false
     */
    static boolean bool(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return "true".equalsIgnoreCase(
                String.valueOf(value))
                || "1".equals(String.valueOf(value));
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    static String text(Object value) {
        return value == null
                ? null : String.valueOf(value);
    }

    /**
     * 生成{@code upper}文本，供后续匹配或展示。
     *
     * @param value 待处理{@code upper}的原始输入，结果供调用方继续使用
     * @return 处理后的{@code upper}文本，供调用方比较或展示
     */
    static String upper(Object value) {
        String text = text(value);
        return text == null
                ? null : text.toUpperCase(Locale.ROOT);
    }

    /**
     * 按候选顺序取首个非空白值，供后续处理使用。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的首个非空白文本，供调用方比较或展示
     */
    static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 处理首个非空值，并将结果传给后续步骤。
     *
     * @param first 首个，供本方法处理首个非空值时使用
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 处理后的首个非空值结果，供调用方继续处理
     */
    static <T> T firstNonNull(
            T first,
            T fallback) {
        return first != null ? first : fallback;
    }

    /**
     * 构造业务冲突异常，供调用方刷新或重试。
     *
     * @param errorCode 错误编码，后续用于处理冲突时定位或关联目标
     * @param message 消息，作为 {@code BusinessConflictException} 的输入影响后续处理
     * @throws BusinessConflictException 目标状态已被其他操作改变时抛出
     */
    static void conflict(
            String errorCode,
            String message) {
        throw new BusinessConflictException(
                errorCode,
                message);
    }
}
