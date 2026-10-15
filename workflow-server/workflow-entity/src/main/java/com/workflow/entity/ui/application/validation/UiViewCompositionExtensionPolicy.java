package com.workflow.entity.ui.application.validation;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 普通关系子页面沿用平台业务操作；接口扩展只属于独立扩展页面。 */
public final class UiViewCompositionExtensionPolicy {

    private static final Set<String> EXTENSION_RELATIONS = Set.of("SAME_RECORD", "INTERFACE_SERVICE");

    private UiViewCompositionExtensionPolicy() {
    }

    /**
     * 根据服务端已保存的关系类型确定接口能力边界，缺失或未知类型不得获得扩展动作权限。
     * 保存校验、能力查询和通用接口绑定匹配共用此规则，旧发布也不能绕过普通关系限制。
     */
    public static boolean allowsInterfaceExtensions(Object relation) {
        return relation instanceof Map<?, ?> map
                && EXTENSION_RELATIONS.contains(normalize(map.get("type")));
    }

    /**
     * 在特殊处理归一化之前拒绝普通关系中的接口配置，防止非接口模式静默丢弃动作绑定。
     * 空白的旧编辑器占位对象仍可读取；自定义组件只负责展示，可继续单独配置。
     *
     * @param relation 已校验的关系对象
     * @param special 原始特殊处理配置，不应在调用前剥离未启用字段
     * @throws IllegalArgumentException 普通关系携带接口模式、动作绑定或数据接口
     */
    public static void validateSpecialHandling(Object relation, Map<String, Object> special) {
        if (allowsInterfaceExtensions(relation)) return;
        boolean interfaceMode = Set.of("INTERFACE_SERVICE", "BOTH").contains(normalize(special.get("mode")));
        boolean actionBindings = special.get("actionServices") instanceof Collection<?> bindings && !bindings.isEmpty();
        boolean dataInterface = special.get("interfaceService") instanceof Map<?, ?> service
                && Set.of("extensionId", "serviceId", "operationCode").stream()
                .anyMatch(key -> !normalize(service.get(key)).isEmpty());
        if (interfaceMode || actionBindings || dataInterface) {
            throw new IllegalArgumentException("普通关系子页面不支持接口扩展；如需数据或动作接口，请使用独立扩展页面");
        }
    }

    private static String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim().toUpperCase(Locale.ROOT);
    }
}
