package com.workflow.process.migration.application;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 流程实例迁移变量、表单 release 映射和失败续跑的稳定策略。 */
@Component
public class ProcessMigrationExecutionPolicy {

    public static final String FORM_RELEASE_MAPPING_VARIABLE = "wfMigrationFormReleaseMappings";
    private static final Set<String> RETRYABLE_STATUSES = Set.of("FAILED", "BLOCKED");

    /**
     * 将变量覆盖和表单 release 映射合并为一次 Flowable 原生迁移文档变量。
     * 保留键由平台维护，调用方不能通过变量覆盖伪造映射审计证据。
     */
    public Map<String, Object> migrationVariables(
            Map<String, Object> overrides,
            Map<String, String> formReleaseMappings) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        if (overrides != null) result.putAll(overrides);
        if (result.containsKey(FORM_RELEASE_MAPPING_VARIABLE)) {
            throw new IllegalArgumentException("变量覆盖不能写入平台保留的表单 release 映射键");
        }
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        if (formReleaseMappings != null) {
            formReleaseMappings.forEach((source, target) -> {
                if (!StringUtils.hasText(source) || !StringUtils.hasText(target)) {
                    throw new IllegalArgumentException("表单 release 映射的源和目标不能为空");
                }
                normalized.put(source.trim(), target.trim());
            });
        }
        if (!normalized.isEmpty()) {
            result.put(FORM_RELEASE_MAPPING_VARIABLE, Map.copyOf(normalized));
        }
        return Map.copyOf(result);
    }

    public boolean retryable(String status) {
        return status != null && RETRYABLE_STATUSES.contains(status.toUpperCase());
    }
}
