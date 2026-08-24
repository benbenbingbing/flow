package com.workflow.entity.permission.api.dto;

import java.time.LocalDateTime;

/** 存量列表数据范围盘点记录。 */
public record EntityListScopeInventoryDTO(
        String id,
        String listId,
        String entityId,
        String entityCode,
        String listKey,
        String listName,
        String detectedPolicy,
        String detectedEnforcement,
        String ownerId,
        String ownerName,
        String processingStatus,
        String selectedPolicy,
        String confirmationReason,
        String confirmedBy,
        LocalDateTime confirmedAt,
        String exceptionNote,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
