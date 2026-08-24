package com.workflow.migration.collaboration.application;

import java.time.LocalDateTime;
import java.util.Map;

/** 配置分支、评论、评审和定时发布请求模型。 */
public final class ConfigurationCollaborationModels {

    private ConfigurationCollaborationModels() {
    }

    public record WorkspaceSaveRequest(
            String id,
            String assetType,
            String assetId,
            String assetName,
            Map<String, Object> content,
            Integer expectedRevision) {
    }

    public record BranchCreateRequest(String branchKey, String branchName) {
    }

    public record BranchSaveRequest(Map<String, Object> content, Integer expectedRevision) {
    }

    public record MergeRequest(Integer expectedWorkspaceRevision, Map<String, Object> resolvedContent) {
    }

    public record CommentRequest(String branchId, String targetKey, String content) {
    }

    public record ReviewRequest(String branchId) {
    }

    public record ReviewDecisionRequest(boolean approved, String note) {
    }

    public record ScheduleRequest(
            String reviewId,
            String releaseCandidateId,
            LocalDateTime scheduledAt,
            String idempotencyKey) {
    }
}
