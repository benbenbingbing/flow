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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    /**
     * 处理分配集合，并将结果传给后续步骤。
     *
     * @param unitId 单元ID，后续用于处理分配集合时定位或关联目标
     * @return 处理后的分配集合结果，供调用方继续处理
     */
    @GetMapping("/{unitId}/position-assignments")
    @RequiresPermission("system:position:view")
    public Result<PositionViews.OrganizationAssignmentMatrix> assignments(
            @PathVariable String unitId) {
        return Result.success(queryService.byOrganization(unitId));
    }

    /** 负责人快捷编辑复用当前关系服务，与其他职务使用相同并发检查。 */
    @PostMapping("/{unitId}/leader")
    @RequiresPermission("system:position:assign")
    public Result<Void> changeLeader(@PathVariable String unitId,
            @RequestBody PositionRequests.ChangeOrganizationLeader request) {
        assignmentService.setMembers(new PositionRequests.SetMembers(
                PositionAssignmentService.UNIT_LEADER, unitId,
                request.userId() == null || request.userId().isBlank()
                        ? List.of() : List.of(request.userId()), request.expectedUserIds()));
        return Result.success();
    }
}
