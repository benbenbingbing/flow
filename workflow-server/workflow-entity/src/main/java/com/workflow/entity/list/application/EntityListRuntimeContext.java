package com.workflow.entity.list.application;

import java.util.Map;

/** 页面配置摘要与业务来源；摘要不授予权限，来源必须由后端重新验证。 */
public record EntityListRuntimeContext(String configHash, Map<String, Object> runtimeContext) {
    public EntityListRuntimeContext { runtimeContext = runtimeContext == null ? Map.of() : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(runtimeContext)); }
    public static EntityListRuntimeContext current() { return new EntityListRuntimeContext(null, Map.of()); }
}
