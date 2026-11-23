package com.workflow.port.entity.mutation.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 一次已发布实体提交；表单坐标用于定位规则，操作者由运行时绑定。
 * @param expectedRevision 已有记录详情返回的修订标记，在取得写锁后校验，避免覆盖并发更新
 */
public record EntitySubmissionCommand(String requestId, Intent intent, String entityCode, String recordId,
        String previousProcessInstanceId, String listKey, String formId, String formConfigHash,
        Map<String, Object> values, String expectedRevision) {

    /** 使用当前记录状态提交，适用于无需比较已读修订标记的调用。 */
    public EntitySubmissionCommand(String requestId, Intent intent, String entityCode, String recordId,
            String previousProcessInstanceId, String listKey, String formId, String formConfigHash,
            Map<String, Object> values) {
        this(requestId, intent, entityCode, recordId, previousProcessInstanceId, listKey, formId,
                formConfigHash, values, null);
    }

    public EntitySubmissionCommand {
        requestId = required(requestId, "requestId");
        if (requestId.length() > 160) throw new IllegalArgumentException("requestId 最长 160 字符");
        Objects.requireNonNull(intent, "intent");
        entityCode = required(entityCode, "entityCode");
        if (intent == Intent.CREATE_DRAFT || intent == Intent.CREATE_AND_START) {
            if (recordId != null) throw new IllegalArgumentException("新增提交不接受已有记录 ID");
        } else {
            recordId = required(recordId, "recordId");
        }
        if (intent == Intent.RESTART) {
            previousProcessInstanceId = required(previousProcessInstanceId, "previousProcessInstanceId");
        } else if (previousProcessInstanceId != null) {
            throw new IllegalArgumentException("只有重新发起才接受上一轮实例 ID");
        }
        if (expectedRevision != null && (recordId == null || !expectedRevision.matches("[0-9a-f]{64}"))) {
            throw new IllegalArgumentException("实体记录修订标记无效");
        }
        values = values == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /** START_EXISTING 提交既有草稿；RESTART 在上一轮撤回后创建新流程实例。 */
    public enum Intent { CREATE_DRAFT, UPDATE, CREATE_AND_START, START_EXISTING, RESTART }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " 不能为空");
        return value.strip();
    }
}
