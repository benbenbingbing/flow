package com.workflow.admin.identity.position.api.web;

import com.workflow.admin.identity.position.api.request.PositionRequests;
import com.workflow.admin.identity.position.api.response.PositionViews;
import com.workflow.admin.identity.position.application.PositionAssignmentQueryService;
import com.workflow.admin.identity.position.application.PositionAssignmentService;
import com.workflow.core.result.Result;
import com.workflow.core.security.RequiresPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 组织视角任职矩阵与 UNIT_LEADER 快捷维护接口。
 */
@RestController
@RequestMapping("/api/system/org")
@RequiredArgsConstructor
public class OrganizationPositionAssignmentController {

    private final PositionAssignmentQueryService queryService;
    private final PositionAssignmentService assignmentService;

    @GetMapping("/{unitId}/position-assignments")
    @RequiresPermission("system:position:view")
    public Result<PositionViews.OrganizationAssignmentMatrix> assignments(
            @PathVariable String unitId) {
        return Result.success(queryService.byOrganization(unitId));
    }

    /**
     * 负责人快捷编辑仍写 UNIT_LEADER 任职；旧 leader_id 不接受独立权威写入。
     */
    @PostMapping("/{unitId}/leader")
    @RequiresPermission("system:position:assign")
    public Result<PositionViews.BatchAssignmentResult> changeLeader(
            @PathVariable String unitId,
            @RequestHeader(value = "Idempotency-Key", required = false)
                    String idempotencyKey,
            @RequestBody PositionRequests.ChangeOrganizationLeader request) {
        if (request == null || request.userId() == null
                || request.userId().isBlank()) {
            assignmentService.clearCurrentLeader(
                    unitId, request == null ? null : request.reason());
            return Result.success(null);
        }
        OffsetDateTime effectiveFrom = request.effectiveFrom() == null
                ? OffsetDateTime.now(ZoneOffset.UTC)
                : request.effectiveFrom();
        PositionRequests.AssignmentBatch batch =
                new PositionRequests.AssignmentBatch(
                        true,
                        request.reason(),
                        List.of(new PositionRequests.AssignmentItem(
                                PositionAssignmentService.UNIT_LEADER,
                                unitId,
                                request.userId(),
                                effectiveFrom,
                                null,
                                true,
                                0,
                                true)));
        return Result.success(assignmentService.batchAssign(
                batch, idempotencyKey));
    }
}
