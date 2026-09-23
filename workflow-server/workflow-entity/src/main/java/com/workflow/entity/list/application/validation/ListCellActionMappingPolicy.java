package com.workflow.entity.list.application.validation;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 行按钮功能映射契约：配置归按钮所有，不以字段当前是否展示作为保存条件。 */
public final class ListCellActionMappingPolicy {
    /**
     * 初始化列表{@code cell}动作映射策略，保存构造参数供后续方法使用。
     */
    private ListCellActionMappingPolicy() {}

    /**
     * 校验单个按钮映射的类型与执行能力。字段可以暂不展示，运行时保留原按钮入口。
     * 不支持的按钮位置、执行方式或参数类型会抛出 IllegalArgumentException。
     *
     * @param position 位置，供本方法校验列表{@code cell}动作映射策略时使用
     * @param type 类型标识，决定后续列表{@code cell}动作映射策略采用的处理分支
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param mode 模式标识，决定后续列表{@code cell}动作映射策略采用的处理分支
     * @param params 参数，供本方法校验列表{@code cell}动作映射策略时使用
     */
    public static void validate(String position, String type, String key, String mode, Map<String, Object> params) {
        Object hidden = params.get("hideWhenMapped");
        if (hidden != null && !(hidden instanceof Boolean)) {
            throw new IllegalArgumentException("映射后隐藏按钮必须是布尔值");
        }
        Object field = params.get("mappedFieldCode");
        if (field == null || "".equals(field)) return;
        if (!(field instanceof String code) || !code.matches("[A-Za-z][A-Za-z0-9_]{0,99}")) {
            throw new IllegalArgumentException("功能映射字段编码不合法");
        }
        boolean supported = "built-in".equals(type)
                ? Set.of("view", "edit", "approve", "delete").contains(key == null ? "" : key)
                : "custom".equals(type) && Set.of("", "handler", "event", "open-form", "open-list", "open-related-content")
                        .contains(mode == null ? "" : mode);
        if (!"ROW".equals(position) || !supported) {
            throw new IllegalArgumentException("只有支持统一执行的操作列按钮可以配置功能映射，组件按钮暂不支持");
        }
    }

    /**
     * 发布或整包保存前检查所有按钮，包含停用按钮，防止重新启用后同一字段对应多个动作。
     *
     * @param position 位置，作为 {@code validate} 的输入影响后续处理
     * @param buttons 按钮集合，供本方法校验按钮集合时使用
     */
    public static void validateButtons(String position, List<Map<String, Object>> buttons) {
        if (buttons == null) return;
        Set<String> fields = new HashSet<>();
        for (Map<String, Object> button : buttons) {
            if (button == null) continue;
            validate(position, text(button, "type"), text(button, "key"), text(button, "customMode"), button);
            String field = text(button, "mappedFieldCode");
            if (!field.isEmpty() && !fields.add(field)) {
                throw new IllegalArgumentException("字段已映射到多个操作列按钮，请保留一个映射: " + field);
            }
        }
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
