package com.workflow.contracts.entity.code;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 写入前的编码上下文。recordId 只是预分配 ID；data 使用逻辑字段编码，不含关系写入令牌。
 * parentData 是当前事务中的父记录快照，父记录可能未提交，远程系统不能依赖回查。
 * idempotencyKey 仅在上层提供稳定变更键时存在，子记录已追加提交路径以区分同批取号；
 * 调用方换键或变更子行顺序属于新的取号请求，不能用 recordId 推断跨请求幂等性。
 */
public record EntityCodeGenerationContext(
        String entityCode, String recordId, Map<String, Object> data,
        String operatorId, String deptId,
        String parentEntityCode, String parentRecordId, Map<String, Object> parentData,
        LocalDateTime generationTime, String idempotencyKey) {
    public EntityCodeGenerationContext {
        data = EntityCodeSnapshots.copy(data);
        parentData = EntityCodeSnapshots.copy(parentData);
    }
}
