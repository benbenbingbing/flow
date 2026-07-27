package com.workflow.contracts.identity.resolver;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 人员解析器目录描述符。
 *
 * @param code                 稳定解析器编码
 * @param displayName          中文名称
 * @param description          用途说明
 * @param implementationVersion 实现版本
 * @param contractVersion      平台契约版本
 * @param supportedUsages      支持的人员解析场景
 * @param extraParamSchema     extraParams 配置 Schema
 * @param dynamicExtraParams   是否允许 Schema 外的动态参数
 */
public record PersonResolverDescriptor(
        String code,
        String displayName,
        String description,
        int implementationVersion,
        int contractVersion,
        Set<PersonResolveUsage> supportedUsages,
        Map<String, Object> extraParamSchema,
        boolean dynamicExtraParams) {

    public PersonResolverDescriptor {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("人员解析器编码不能为空");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("人员解析器名称不能为空");
        }
        if (implementationVersion < 1 || contractVersion < 1) {
            throw new IllegalArgumentException("人员解析器版本必须大于 0");
        }
        code = code.trim();
        displayName = displayName.trim();
        description = description == null ? "" : description.trim();
        supportedUsages = supportedUsages == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(supportedUsages));
        extraParamSchema = extraParamSchema == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(extraParamSchema));
        Objects.requireNonNull(supportedUsages, "supportedUsages");
        Objects.requireNonNull(extraParamSchema, "extraParamSchema");
    }
}
