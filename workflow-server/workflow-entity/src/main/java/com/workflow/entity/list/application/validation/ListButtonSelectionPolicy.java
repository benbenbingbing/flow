package com.workflow.entity.list.application.validation;

import com.workflow.core.error.BusinessForbiddenException;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 工具栏执行前的选择契约；目标列表的 selectionMode 仍独立控制选择器。 */
public final class ListButtonSelectionPolicy {
    public static final String NONE = "NONE";
    public static final String SINGLE = "SINGLE";
    public static final String AT_LEAST_ONE = "AT_LEAST_ONE";
    private static final Set<String> REQUIREMENTS = Set.of(NONE, SINGLE, AT_LEAST_ONE);

    /**
     * 初始化列表按钮选择策略，保存构造参数供后续方法使用。
     */
    private ListButtonSelectionPolicy() {}

    /**
     * 解析发布按钮的有效要求，缺省兼容旧配置，内置动作及唯一来源参数不能被放宽。
     *
     * @param button 按钮，作为 {@code fixedRequirement} 的输入影响后续处理
     * @return 处理后的{@code requirement}文本，供调用方比较或展示
     */
    public static String requirement(Map<String, Object> button) {
        String fixed = fixedRequirement(text(button, "type"), text(button, "key"),
                text(button, "customMode"), button);
        if (fixed != null) return fixed;
        return "custom".equals(button.get("type")) && button.get("selectionRequirement") != null
                ? text(button, "selectionRequirement") : NONE;
    }

    /**
     * 判断需要选择条件是否成立，供调用方选择后续分支。
     *
     * @param button 按钮，作为 {@code NONE.equals} 的输入影响后续处理
     * @return 需要选择条件成立时为 true，否则为 false
     */
    public static boolean requiresSelection(Map<String, Object> button) {
        return !NONE.equals(requirement(button));
    }

    /**
     * 在读取记录和执行事件链之前检查数量。count 必须来自已清理、去重的记录 ID 集合，
     * 不接收客户端自报数量；失败时抛出业务禁止异常。NONE 允许已勾选的数据继续传入。
     *
     * @param button 按钮，作为 {@code requirement} 的输入影响后续处理
     * @param count 数量，供本方法校验并获取数量时使用
     */
    public static void requireCount(Map<String, Object> button, int count) {
        String requirement = requirement(button);
        if (SINGLE.equals(requirement) && count != 1) {
            throw new BusinessForbiddenException("UI_EVENT_LIST_SINGLE_SELECTION_REQUIRED", "请选择恰好一条数据");
        }
        if (AT_LEAST_ONE.equals(requirement) && count < 1) {
            throw new BusinessForbiddenException("UI_EVENT_LIST_SELECTION_REQUIRED", "请先选择数据");
        }
        if (!REQUIREMENTS.contains(requirement)) {
            throw new BusinessForbiddenException("UI_EVENT_LIST_SELECTION_INVALID", "按钮选择要求配置无效");
        }
    }

    /**
     * 保存/发布时只接受自定义工具栏按钮的三种要求，拒绝无效值或与打开动作冲突的设置。
     *
     * @param position 位置，供本方法校验列表按钮选择策略时使用
     * @param type 类型标识，决定后续列表按钮选择策略采用的处理分支
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param mode 模式标识，决定后续列表按钮选择策略采用的处理分支
     * @param params 参数，作为 {@code fixedRequirement} 的输入影响后续处理
     */
    public static void validate(String position, String type, String key, String mode, Map<String, Object> params) {
        Object value = params.get("selectionRequirement");
        if (value == null) return;
        if (!(value instanceof String requirement) || !REQUIREMENTS.contains(requirement)) {
            throw new IllegalArgumentException("按钮选择要求只能是 NONE、SINGLE 或 AT_LEAST_ONE");
        }
        if (!"TOOLBAR".equals(position) || !"custom".equals(type)) {
            throw new IllegalArgumentException("只有自定义工具栏按钮可以配置选择要求");
        }
        String fixed = fixedRequirement(type, key, mode, params);
        if (fixed != null && !fixed.equals(value)) {
            throw new IllegalArgumentException("按钮选择要求与动作所需记录数量不一致");
        }
    }

    /**
     * 整包配置和发布检查使用同一规则，避免绕过单按钮保存接口。
     *
     * @param position 位置，供本方法校验按钮集合时使用
     * @param buttons 按钮集合，供本方法校验按钮集合时使用
     */
    public static void validateButtons(String position, List<Map<String, Object>> buttons) {
        if (buttons == null) return;
        for (Map<String, Object> button : buttons) {
            if (button != null) validate(position, text(button, "type"), text(button, "key"), text(button, "customMode"), button);
        }
    }

    /**
     * 生成固定{@code requirement}文本，供后续匹配或展示。
     *
     * @param type 类型标识，决定后续固定{@code requirement}采用的处理分支
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param mode 模式标识，决定后续固定{@code requirement}采用的处理分支
     * @param params 参数，供本方法处理固定{@code requirement}时使用
     * @return 处理后的固定{@code requirement}文本，供调用方比较或展示
     */
    private static String fixedRequirement(String type, String key, String mode, Map<String, Object> params) {
        key = key == null ? "" : key;
        mode = mode == null ? "" : mode;
        if (Set.of("batchDelete", "exportSelected").contains(key)) return AT_LEAST_ONE;
        if ("custom".equals(type) && "open-related-content".equals(mode)) return SINGLE;
        boolean opensPage = ("custom".equals(type) && Set.of("open-list", "open-form").contains(mode))
                || ("built-in".equals(type) && "create".equals(key));
        if (opensPage && params.get("parameterMappings") instanceof List<?> mappings
                && mappings.stream().anyMatch(item -> item instanceof Map<?, ?> mapping
                && ("FIELD".equals(mapping.get("sourceType")) || "RECORD_ID".equals(mapping.get("sourceType"))))) {
            return SINGLE;
        }
        return null;
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private static String text(Map<String, Object> value, String key) {
        return value.get(key) instanceof String text ? text : "";
    }
}
