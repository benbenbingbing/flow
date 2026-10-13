package com.workflow.entity.form.application.context;

import java.util.Map;

/** 页面配置摘要与业务来源；摘要不授予权限，来源必须由后端重新验证。 */
public record EntityFormRuntimeContext(String configHash, Map<String, Object> runtimeContext) {
    public EntityFormRuntimeContext { runtimeContext = runtimeContext == null ? Map.of() : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(runtimeContext)); }
    public static EntityFormRuntimeContext current() { return new EntityFormRuntimeContext(null, Map.of()); }
}
