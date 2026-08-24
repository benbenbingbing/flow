package com.workflow.entity.list.experience.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 字段索引向导 API 模型。 */
public final class EntityListExperienceModels {

    private EntityListExperienceModels() {
    }

    public record IndexAnalyzeRequest(
            List<String> filterFields,
            List<String> sortFields) {
    }

    public record IndexCandidate(
            String id,
            String entityCode,
            String listKey,
            String indexName,
            List<String> columns,
            Map<String, Object> evidence,
            String status,
            int revision,
            double selectivityEstimate,
            long estimatedRows,
            String writeCostLevel,
            String recommendation,
            String schemaOperationId,
            String errorMessage,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record IndexApplyRequest(Integer expectedRevision, Boolean confirmed) {
    }

    public record IndexRejectRequest(Integer expectedRevision, String reason) {
    }
}
