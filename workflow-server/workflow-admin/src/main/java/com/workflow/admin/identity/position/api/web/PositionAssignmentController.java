package com.workflow.admin.identity.position.api.web;

import com.workflow.admin.identity.position.api.request.PositionRequests;
import com.workflow.admin.identity.position.api.response.PositionViews;
import com.workflow.admin.identity.position.application.PositionAssignmentQueryService;
import com.workflow.admin.identity.position.application.PositionAssignmentService;
import com.workflow.core.result.PageResult;
import com.workflow.core.result.Result;
import com.workflow.core.security.RequiresPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任职历史、预检、批量任命、撤销与有效期调整接口。
 */
@RestController
@RequestMapping("/api/system/position/assignments")
@RequiresPermission("system:position:view")
@RequiredArgsConstructor
public class PositionAssignmentController {

    private final PositionAssignmentQueryService queryService;
    private final PositionAssignmentService assignmentService;

    @GetMapping("/page")
    public Result<PageResult<PositionViews.AssignmentView>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String positionCode,
            @RequestParam(required = false) String organizationUnitId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) Boolean activeOnly) {
        return Result.success(queryService.page(
                pageNum, pageSize, keyword, positionCode,
                organizationUnitId, userId, activeOnly));
    }

    @PostMapping("/precheck")
    @RequiresPermission("system:position:assign")
    public Result<PositionViews.PrecheckResult> precheck(
            @RequestBody PositionRequests.AssignmentBatch request) {
        return Result.success(assignmentService.precheck(request));
    }

    @PostMapping("/batch")
    @RequiresPermission("system:position:assign")
    public Result<PositionViews.BatchAssignmentResult> batch(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PositionRequests.AssignmentBatch request) {
        return Result.success(assignmentService.batchAssign(
                request, idempotencyKey));
    }

    @PostMapping("/{id}/revoke")
    @RequiresPermission("system:position:assign")
    public Result<Void> revoke(
            @PathVariable String id,
            @RequestBody PositionRequests.RevokeAssignment request) {
        assignmentService.revoke(id, request);
        return Result.success();
    }

    @PostMapping("/{id}/update-period")
    @RequiresPermission("system:position:assign")
    public Result<Void> updatePeriod(
            @PathVariable String id,
            @RequestBody PositionRequests.UpdateAssignmentPeriod request) {
        assignmentService.updatePeriod(id, request);
        return Result.success();
    }
}
