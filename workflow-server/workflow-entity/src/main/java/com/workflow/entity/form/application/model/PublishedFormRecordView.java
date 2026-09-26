package com.workflow.entity.form.application.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 表单终检与实体持久化一致的字段级补丁视图：缺省保留，显式 null 清空。 */
public final class PublishedFormRecordView {
    private static final Set<String> SYSTEM_MANAGED_FIELDS = Set.of(
            "id", "status", "processStatus", "processInstanceId", "processStartTime", "processEndTime",
            "currentTaskId", "currentTaskName", "currentTaskAssignee", "submitterId", "submitterName",
            "submitTime", "create_time", "update_time", "create_by", "update_by", "deleted");

    /**
     * 初始化已发布表单记录视图，保存构造参数供后续方法使用。
     */
    private PublishedFormRecordView() {}

    /**
     * 把 DTO 形态的 data 与系统字段展平；系统字段以顶层可信记录为准。
     *
     * @param source 待处理{@code flatten}的原始输入，结果供调用方继续使用
     * @return {@code flatten}键值结果，供调用方继续处理
     */
    public static Map<String, Object> flatten(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (source == null) return result;
        if (source.get("data") instanceof Map<?, ?> data) data.forEach((key, value) -> result.put(String.valueOf(key), value));
        source.forEach((key, value) -> { if (!"data".equals(key)) result.put(key, value); });
        return result;
    }

    /**
     * 合并用户字段补丁时保留系统维护字段，客户端不能伪造流程时间或状态来通过比较。
     *
     * @param existing 已有，作为 {@code flatten} 的输入影响后续处理
     * @param patch 补丁，作为 {@code flatten} 的输入影响后续处理
     * @return 已发布表单记录视图键值结果，供调用方继续处理
     */
    public static Map<String, Object> merge(Map<String, Object> existing, Map<String, Object> patch) {
        Map<String, Object> result = flatten(existing);
        flatten(patch).forEach((key, value) -> {
            if (!SYSTEM_MANAGED_FIELDS.contains(key)) result.put(key, value);
        });
        return result;
    }
}
