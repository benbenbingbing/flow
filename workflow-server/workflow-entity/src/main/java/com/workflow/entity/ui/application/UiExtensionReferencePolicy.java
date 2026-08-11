package com.workflow.entity.ui.application;

import java.util.Locale;
import java.util.Map;

/**
 * UI 扩展引用类型判定策略。
 *
 * <p>表单节点的 {@code componentName} 同时承载 NODE 扩展和 FIELD 字段组件扩展。
 * FIELD 节点通过 props 中显式的 {@code componentExtensionType=FIELD} 区分字段组件；
 * 未标记及其他节点继续按 NODE 扩展处理，避免依赖扩展名称或注册顺序猜测语义。</p>
 */
public final class UiExtensionReferencePolicy {

    /** 字段组件扩展类型。 */
    public static final String FIELD = "FIELD";
    /** 普通节点扩展类型。 */
    public static final String NODE = "NODE";
    /** FIELD 节点属性中用于声明 componentName 角色的键。 */
    public static final String COMPONENT_EXTENSION_TYPE =
            "componentExtensionType";

    private UiExtensionReferencePolicy() {
    }

    /**
     * 解析节点 componentName 对应的扩展类型。
     *
     * @param nodeType 节点类型
     * @param props    节点属性
     * @return FIELD 或 NODE
     */
    public static String resolveNodeExtensionType(
            String nodeType,
            Map<String, Object> props) {
        if (FIELD.equals(normalize(nodeType))
                && FIELD.equals(normalize(
                        props == null
                                ? null
                                : props.get(COMPONENT_EXTENSION_TYPE)))) {
            return FIELD;
        }
        return NODE;
    }

    private static String normalize(Object value) {
        return value == null
                ? ""
                : String.valueOf(value)
                        .trim()
                        .toUpperCase(Locale.ROOT);
    }
}
