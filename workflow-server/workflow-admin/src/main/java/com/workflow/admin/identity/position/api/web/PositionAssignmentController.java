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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前职务人员查询、集合保存及移除接口。
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
     * @return 符合条件的位置视图结果，供调用方继续处理
     */
    @GetMapping("/page")
    public Result<PageResult<PositionViews.AssignmentView>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String positionCode,
            @RequestParam(required = false) String organizationUnitId,
            @RequestParam(required = false) String userId) {
        return Result.success(queryService.page(
                pageNum, pageSize, keyword, positionCode,
                organizationUnitId, userId));
    }

    /** 保存完整当前人员集合，后台统一校验并立即生效。 */
    @PutMapping
    @RequiresPermission("system:position:assign")
    public Result<Void> setMembers(@RequestBody PositionRequests.SetMembers request) {
        assignmentService.setMembers(request);
        return Result.success();
    }

    /** 删除当前关系；不保存撤销原因或历史。 */
    @DeleteMapping("/{id}")
    @RequiresPermission("system:position:assign")
    public Result<Void> remove(@PathVariable String id,
                              @RequestBody PositionRequests.RemoveAssignment request) {
        assignmentService.remove(id, request);
        return Result.success();
    }
}
