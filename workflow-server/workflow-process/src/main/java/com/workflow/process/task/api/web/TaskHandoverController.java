package com.workflow.process.task.api.web;

import com.workflow.contracts.identity.model.IdentityHandoverUser;
import com.workflow.contracts.identity.port.IdentityDirectoryPort;
import com.workflow.core.result.ApiResponse;
import com.workflow.core.result.PageResult;
import com.workflow.core.security.RequiresPermission;
import com.workflow.process.task.api.request.TaskHandoverRequest;
import com.workflow.process.task.api.response.TaskHandoverTask;
import com.workflow.process.task.application.TaskHandoverService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 系统管理专用人员交接边界，管理权限不依赖来源人员是否还能登录。 */
@RestController
@RequestMapping("/api/task-handover")
@RequiredArgsConstructor
public class TaskHandoverController {
    private final IdentityDirectoryPort directory;
    private final TaskHandoverService service;

    /** 来源允许所有状态；targetOnly 只筛选可接收任务的正常人员，不返回账号敏感字段。 */
    @GetMapping("/users")
    @RequiresPermission("system:task-handover:view")
    public ApiResponse<List<IdentityHandoverUser>> users(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean targetOnly) {
        return ApiResponse.success(directory.searchHandoverUsers(keyword, targetOnly));
    }

    /** 按来源人员分页读取当前待办，不使用登录人的个人待办范围。 */
    @GetMapping("/tasks")
    @RequiresPermission("system:task-handover:view")
    public ApiResponse<PageResult<TaskHandoverTask>> tasks(
            @RequestParam String sourceUserId,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(service.findPage(sourceUserId, pageNum, pageSize));
    }

    /** 交接整批任务；归属变更、目标停用或写入失败时整批回滚，调用方应刷新后重试。 */
    @PostMapping("/transfer")
    @RequiresPermission("system:task-handover:transfer")
    public ApiResponse<Map<String, Integer>> transfer(@Valid @RequestBody TaskHandoverRequest request) {
        return ApiResponse.success(Map.of("transferredCount", service.transfer(request)));
    }
}
