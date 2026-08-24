package com.workflow.entity.permission.api.dto;

/** 批量确认结果；重复提交相同内容会计入 skipped，避免重复发布。 */
public record EntityListScopeInventoryBatchResult(int requested, int processed, int skipped) {
}
