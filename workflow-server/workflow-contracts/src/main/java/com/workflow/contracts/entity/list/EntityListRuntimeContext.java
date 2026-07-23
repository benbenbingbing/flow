package com.workflow.contracts.entity.list;

import java.util.Map;

/**
 * 列表页面、弹窗、抽屉和表单选择器共享的运行时上下文。
 */
public record EntityListRuntimeContext(
        /** 实体编码 */
        String entityCode,
        /** 列表Key */
        String listKey,
        /** 使用场景标识 */
        String scene,
        /** 来源实体编码 */
        String sourceEntityCode,
        /** 来源记录ID */
        String sourceRecordId,
        /** 关联关系Key */
        String relationKey,
        /** 附加参数 */
        Map<String, Object> parameters) {
}
