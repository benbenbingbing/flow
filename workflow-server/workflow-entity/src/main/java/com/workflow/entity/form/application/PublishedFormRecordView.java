package com.workflow.entity.form.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 表单终检与实体持久化一致的字段级补丁视图：缺省保留，显式 null 清空。 */
public final class PublishedFormRecordView {
    private static final Set<String> SYSTEM_MANAGED_FIELDS = Set.of(
            "id", "status", "processInstanceId", "processStartTime", "processEndTime",
            "currentTaskId", "currentTaskName", "currentTaskAssignee", "submitterId", "submitterName",
            "submitTime", "create_time", "update_time", "create_by", "update_by", "deleted");

    private PublishedFormRecordView() {}

    /** 把 DTO 形态的 data 与系统字段展平；系统字段以顶层可信记录为准。 */
    public static Map<String, Object> flatten(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (source == null) return result;
        if (source.get("data") instanceof Map<?, ?> data) data.forEach((key, value) -> result.put(String.valueOf(key), value));
        source.forEach((key, value) -> { if (!"data".equals(key)) result.put(key, value); });
        return result;
    }

    /** 合并用户字段补丁时保留系统维护字段，客户端不能伪造流程时间或状态来通过比较。 */
    public static Map<String, Object> merge(Map<String, Object> existing, Map<String, Object> patch) {
        Map<String, Object> result = flatten(existing);
        flatten(patch).forEach((key, value) -> {
            if (!SYSTEM_MANAGED_FIELDS.contains(key)) result.put(key, value);
        });
        return result;
    }
}
