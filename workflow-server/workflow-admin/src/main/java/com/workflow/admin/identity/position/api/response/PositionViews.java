package com.workflow.admin.identity.position.api.response;

import java.time.Instant;
import java.util.List;

/**
 * 职务定义、组织任职及批量预检的只读响应契约。
 */
public final class PositionViews {

    private PositionViews() {
    }

    public record PositionView(
            String id,
            String positionCode,
            String positionName,
            String applicableUnitType,
            String holderMode,
            boolean builtIn,
            String status,
            int sortOrder,
            String description,
            int revision,
            long currentAssignmentCount,
            long processReferenceCount,
            String createdBy,
            String updatedBy,
            Instant createTime,
            Instant updateTime) {
    }

    public record AssignmentView(
            String id,
            String positionId,
            String positionCode,
            String positionName,
            String organizationUnitId,
            String organizationUnitName,
            String organizationUnitType,
            String businessLevelCode,
            String userId,
            String username,
            String displayName,
            boolean isPrimary,
            int sortOrder,
            Instant effectiveFrom,
            Instant effectiveTo,
            Instant revokedAt,
            String revokedBy,
            String revokeReason,
            int revision,
            boolean active,
            Instant createTime,
            Instant updateTime) {
    }

    public record OrganizationAssignmentMatrix(
            String organizationUnitId,
            String organizationUnitName,
            String organizationUnitType,
            String businessLevelCode,
            List<AssignmentView> assignments) {
    }

    public record UserAssignments(
            String userId,
            String username,
            String displayName,
            List<AssignmentView> assignments) {
    }

    public record PrecheckResult(
            boolean valid,
            List<PrecheckItem> items) {
    }

    public record PrecheckItem(
            int index,
            boolean valid,
            String errorCode,
            String message) {
    }

    public record BatchAssignmentResult(
            String idempotencyKey,
            boolean replayed,
            List<String> assignmentIds) {
    }
}
