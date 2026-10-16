package com.workflow.entity.ui.application.validation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 普通关系子页面沿用目标页面的展示和平台业务操作；局部展示、接口扩展只属于独立扩展页面。 */
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
     * 在配置副本中移除普通关系已退役的局部展示覆盖，目标页面自身的 PAGE 组件不受影响。
     * 保存、历史投影和迁移转换共用此规则，不修改传入的历史文档或嵌套组件定义。
     *
     * @param relation 关联类型；仅 SAME_RECORD、INTERFACE_SERVICE 保留局部展示
     * @param special 原始特殊处理对象，接口绑定必须保留供后续策略拒绝非法普通关系配置
     * @return 可修改的新特殊处理对象；BOTH 只移除展示部分，不默许或吞掉接口配置
     */
    public static Map<String, Object> normalizePresentationOverride(
            Object relation, Map<String, Object> special) {
        Map<String, Object> normalized = new LinkedHashMap<>(special);
        if (allowsInterfaceExtensions(relation)) return normalized;
        normalized.remove("customComponent");
        String mode = normalize(normalized.get("mode"));
        if ("CUSTOM_COMPONENT".equals(mode)) normalized.put("mode", "NONE");
        else if ("BOTH".equals(mode)) normalized.put("mode", "INTERFACE_SERVICE");
        // 普通关系不再提供展示降级设置；旧 HIDE/PLACEHOLDER 不能成为无法编辑的隐藏行为。
        if (normalized.containsKey("failurePolicy")) normalized.put("failurePolicy", "ERROR");
        return normalized;
    }

    /**
     * 在特殊处理归一化之前拒绝普通关系中的接口配置，防止非接口模式静默丢弃动作绑定。
     * 空白的旧编辑器占位对象仍可读取；退役展示覆盖由 normalizePresentationOverride 单独清理。
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
