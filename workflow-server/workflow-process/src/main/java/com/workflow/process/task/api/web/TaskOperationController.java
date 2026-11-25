package com.workflow.process.task.api.web;

import com.workflow.core.security.AuthenticatedApi;

import com.workflow.core.result.Result;
import com.workflow.process.task.api.request.TaskAddSignRequest;
import com.workflow.process.cc.api.request.TaskCcRequest;
import com.workflow.process.task.application.TaskAddSignService;
import com.workflow.process.task.application.TaskHistoryReturnService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 任务操作控制器。
 *
 * <p>提供任务加签（预览、新增、撤销）与人工知会等扩展操作的 RESTful 接口。</p>
 */
@AuthenticatedApi(objectAuthorization = true)
@RestController
@RequiredArgsConstructor
public class TaskOperationController {
    @org.springframework.beans.factory.annotation.Autowired
    private com.workflow.process.task.application.ProcessTaskCommandAdapter taskCommands;

    private final TaskHistoryReturnService historyReturns;

    /** 任务加签服务 */
    private final TaskAddSignService taskAddSignService;
    private final com.workflow.process.task.application.TaskOperationApplicationService operationService;

    /**
     * 查询任务可执行的操作集合，含历史回退、发起人重提、转办、知会及加签类型。
     *
     * @param taskId 任务ID
     * @return 操作集合
     */
    @GetMapping("/api/tasks/{taskId}/operations")
    public Result<Map<String, Object>> operations(@PathVariable String taskId) {
        var capabilities = new java.util.LinkedHashMap<>(operationService.operations(taskId));
        capabilities.put("return", !historyReturns.targets(taskId).isEmpty());
        capabilities.put("historyResubmit", historyReturns.canResubmit(taskId));
        return Result.success(capabilities);
    }

    /** 查询在当前实例中经实际办理路径验证的目标。 */
    @GetMapping("/api/tasks/{taskId}/return-targets")
    public Result<List<TaskHistoryReturnService.Target>> returnTargets(@PathVariable String taskId) {
        return Result.success(historyReturns.targets(taskId));
    }

    /** 校验回退请求后，在共用幂等事务内将当前待办回退到所选的历史活动。 */
    @PostMapping("/api/tasks/{taskId}/return")
    public Result<TaskHistoryReturnService.Outcome> returnTask(
            @PathVariable String taskId, @Valid @RequestBody TaskHistoryReturnService.Request request,
            BindingResult validation) {
        // 直接调用接口时也应获得可修正的参数提示，不应由通用异常处理返回系统繁忙。
        if (validation.hasErrors()) {
            throw new IllegalArgumentException(validation.getAllErrors().get(0).getDefaultMessage());
        }
        return Result.success(historyReturns.execute(taskId, request));
    }

    /**
     * 加签预览：解析加签人员并返回去重、禁用、无效等校验结果。
     *
     * @param taskId  任务ID
     * @param userIds 加签人员标识列表
     * @param type    加签类型，默认 PARALLEL
     * @return 预览结果
     */
    @GetMapping("/api/tasks/{taskId}/add-sign-preview")
    public Result<Map<String, Object>> preview(
            @PathVariable String taskId,
            @RequestParam List<String> userIds,
            @RequestParam(defaultValue = "PARALLEL") String type) {
        return Result.success(taskAddSignService.preview(taskId, userIds, type));
    }

    /**
     * 新增加签。
     *
     * @param taskId  任务ID
     * @param request 加签请求
     * @return 加签结果（含加签ID、生成的任务ID等）
     */
    @PostMapping("/api/tasks/{taskId}/add-sign")
    public Result<Map<String, Object>> addSign(
            @PathVariable String taskId,
            @Valid @RequestBody TaskAddSignRequest request) {
        return Result.success(taskCommands.addSignHttp(taskId, request));
    }

    /**
     * 人工知会：由办理人手动添加知会人员。
     *
     * @param taskId  任务ID
     * @param request 知会请求
     * @return 知会结果（含创建的知会记录数）
     */
    @PostMapping("/api/tasks/{taskId}/cc")
    public Result<Map<String, Object>> manualCc(
            @PathVariable String taskId,
            @Valid @RequestBody TaskCcRequest request) {
        return Result.success(Map.of("created", taskCommands.addCcForUser(new com.workflow.port.process.model.task.ManualCcCommand(
                com.workflow.process.command.application.ProcessHttpCommands.meta(request.getRequestId()), taskId,
                request.getUserIds(), request.getComment())).created()));
    }

    /**
     * 撤销加签。
     *
     * @param addSignId 加签记录ID
     * @return 操作结果
     */
    @PostMapping("/api/add-sign/{addSignId}/cancel")
    public Result<Void> cancel(@PathVariable String addSignId,
            @RequestBody(required = false) Map<String, String> request) {
        Map<String, String> values = request == null ? Map.of() : request;
        taskCommands.cancelAddSignForUser(new com.workflow.port.process.model.task.CancelAddSignCommand(
                com.workflow.process.command.application.ProcessHttpCommands.meta(values.get("requestId")),
                addSignId, values.get("reason")));
        return Result.success();
    }

}
