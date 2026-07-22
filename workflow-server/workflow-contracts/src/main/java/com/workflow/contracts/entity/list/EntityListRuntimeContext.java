package com.workflow.contracts.entity.list;

import java.util.Map;

/**
 * 列表页面、弹窗、抽屉和表单选择器共享的运行时上下文。
 */
public record EntityListRuntimeContext(
        String entityCode,
        String listKey,
        String scene,
        String sourceEntityCode,
        String sourceRecordId,
        String relationKey,
        Map<String, Object> parameters) {
}
