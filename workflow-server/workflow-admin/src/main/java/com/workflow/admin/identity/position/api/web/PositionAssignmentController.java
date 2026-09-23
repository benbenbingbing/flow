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

    /**
     * 分页查询位置分配；查询结果供调用方展示或继续处理。
     *
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @param keyword 关键字，作为 {@code Result.success} 的输入影响后续处理
     * @param positionCode 位置编码，后续用于分页查询位置分配时定位或关联目标
     * @param organizationUnitId 组织单元ID，后续用于分页查询位置分配时定位或关联目标
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param activeOnly 活动仅，作为 {@code Result.success} 的输入影响后续处理
     * @return 符合条件的位置视图结果，供调用方继续处理
     */
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

    /**
     * 处理预检查，并将结果传给后续步骤。
     *
     * @param request 本次请求，后续经校验后用于处理预检查
     * @return 处理后的预检查结果，供调用方继续处理
     */
    @PostMapping("/precheck")
    @RequiresPermission("system:position:assign")
    public Result<PositionViews.PrecheckResult> precheck(
            @RequestBody PositionRequests.AssignmentBatch request) {
        return Result.success(assignmentService.precheck(request));
    }

    /**
     * 处理批次，并将结果传给后续步骤。
     *
     * @param idempotencyKey 幂等键，后续用于授权校验、关联或幂等去重
     * @param request 本次请求，后续经校验后用于处理批次
     * @return 处理后的批次结果，供调用方继续处理
     */
    @PostMapping("/batch")
    @RequiresPermission("system:position:assign")
    public Result<PositionViews.BatchAssignmentResult> batch(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PositionRequests.AssignmentBatch request) {
        return Result.success(assignmentService.batchAssign(
                request, idempotencyKey));
    }

    /**
     * 撤销位置分配；后续读取或执行将使用更新后的状态。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param request 本次请求，后续经校验后用于撤销位置分配
     * @return 撤销后的位置分配结果，供调用方继续处理
     */
    @PostMapping("/{id}/revoke")
    @RequiresPermission("system:position:assign")
    public Result<Void> revoke(
            @PathVariable String id,
            @RequestBody PositionRequests.RevokeAssignment request) {
        assignmentService.revoke(id, request);
        return Result.success();
    }

    /**
     * 更新时段；后续读取或执行将使用更新后的状态。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param request 本次请求，后续经校验后用于更新时段
     * @return 更新后的时段结果，供调用方继续处理
     */
    @PostMapping("/{id}/update-period")
    @RequiresPermission("system:position:assign")
    public Result<Void> updatePeriod(
            @PathVariable String id,
            @RequestBody PositionRequests.UpdateAssignmentPeriod request) {
        assignmentService.updatePeriod(id, request);
        return Result.success();
    }
}
