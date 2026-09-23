package com.workflow.contracts.process.assignment.model;

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

    /**
     * 初始化人员解析器描述，保存构造参数供后续方法使用。
     *
     * @param code 业务编码，供后续匹配和引用
     * @param displayName 用户可见名称，供界面和日志展示
     * @param description 描述，保存在对象中供后续校验、查询或展示
     * @param implementationVersion 实现版本，保存在对象中供后续校验、查询或展示
     * @param contractVersion 契约版本，保存在对象中供后续校验、查询或展示
     * @param supportedUsages {@code supported}{@code usages}，保存在对象中供后续校验、查询或展示
     * @param extraParamSchema 附加参数结构，保存在对象中供后续校验、查询或展示
     * @param dynamicExtraParams 动态附加参数，保存在对象中供后续校验、查询或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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
