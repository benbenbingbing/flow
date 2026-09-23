package com.workflow.admin.identity.position.api.web;

import com.workflow.admin.identity.position.api.response.PositionViews;
import com.workflow.admin.identity.position.application.PositionAssignmentQueryService;
import com.workflow.core.result.Result;
import com.workflow.core.security.RequiresPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 用户视角任职查询接口。 */
@RestController
@RequestMapping("/api/system/user")
@RequiredArgsConstructor
public class UserPositionAssignmentController {

    private final PositionAssignmentQueryService queryService;

    /**
     * 处理分配集合，并将结果传给后续步骤。
     *
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @return 处理后的分配集合结果，供调用方继续处理
     */
    @GetMapping("/{userId}/position-assignments")
    @RequiresPermission("system:position:view")
    public Result<PositionViews.UserAssignments> assignments(
            @PathVariable String userId) {
        return Result.success(queryService.byUser(userId));
    }
}
