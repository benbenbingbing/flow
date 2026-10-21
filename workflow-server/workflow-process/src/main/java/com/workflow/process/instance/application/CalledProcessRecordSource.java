package com.workflow.process.instance.application;

import java.util.Map;
import java.util.function.Function;

/** 子流程记录来源仅允许变量/Map 路径，不执行任意 UEL 方法或脚本。 */
public final class CalledProcessRecordSource {
    private CalledProcessRecordSource() { }

    /** 校验并返回字段路径；支持 relatedId、${relatedId} 和 ${relation.id}。 */
    public static String path(String source) {
        String path = source == null ? "" : source.trim();
        if (path.startsWith("${") && path.endsWith("}")) {
            path = path.substring(2, path.length() - 1).trim();
        }
        if (!path.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")) {
            throw new IllegalArgumentException("子流程记录 ID 必须引用父流程字段或参数，如 ${relatedRecordId}");
        }
        return path;
    }

    /** 从父流程作用域读取单条记录 ID；空值、集合和对象均拒绝，避免误绑定业务记录。 */
    public static String resolve(String source, Function<String, Object> variables) {
        String[] parts = path(source).split("\\.");
        Object value = variables.apply(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            value = value instanceof Map<?, ?> map ? map.get(parts[i]) : null;
        }
        if (!(value instanceof String || value instanceof Number) || value.toString().isBlank()) {
            throw new IllegalArgumentException("子流程记录 ID 为空或不是单条记录: " + source);
        }
        return value.toString().trim();
    }
}
