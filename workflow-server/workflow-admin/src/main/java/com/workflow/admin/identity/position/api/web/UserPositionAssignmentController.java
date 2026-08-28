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

    @GetMapping("/{userId}/position-assignments")
    @RequiresPermission("system:position:view")
    public Result<PositionViews.UserAssignments> assignments(
            @PathVariable String userId) {
        return Result.success(queryService.byUser(userId));
    }
}
