package com.workflow.admin.identity.position.api.request;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 职务定义与任职写接口的请求契约。
 */
public final class PositionRequests {

    private PositionRequests() {
    }

    public record CreatePosition(
            String positionCode,
            String positionName,
            String applicableUnitType,
            String holderMode,
            Integer sortOrder,
            String description) {
    }

    public record UpdatePosition(
            String positionName,
            String applicableUnitType,
            String holderMode,
            Integer sortOrder,
            String description,
            Integer revision) {
    }

    public record ChangePositionStatus(
            String status,
            Integer revision) {
    }

    public record DeletePosition(Integer revision) {
    }

    public record AssignmentBatch(
            Boolean atomic,
            String reason,
            List<AssignmentItem> items) {
    }

    public record AssignmentItem(
            String positionCode,
            String organizationUnitId,
            String userId,
            OffsetDateTime effectiveFrom,
            OffsetDateTime effectiveTo,
            Boolean isPrimary,
            Integer sortOrder,
            Boolean replaceExisting) {
    }

    public record RevokeAssignment(
            String reason,
            Integer revision) {
    }

    public record UpdateAssignmentPeriod(
            OffsetDateTime effectiveFrom,
            OffsetDateTime effectiveTo,
            String reason,
            Integer revision) {
    }

    public record ChangeOrganizationLeader(
            String userId,
            OffsetDateTime effectiveFrom,
            String reason) {
    }
}
