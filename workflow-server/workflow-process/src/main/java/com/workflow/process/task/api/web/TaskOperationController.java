package com.workflow.process.task.api.web;

import com.workflow.core.security.AuthenticatedApi;

import com.workflow.core.result.Result;
import com.workflow.process.task.api.request.TaskAddSignRequest;
import com.workflow.process.cc.api.request.TaskCcRequest;
import com.workflow.process.cc.application.ProcessCcRuntimeService;
import com.workflow.process.task.application.TaskAddSignService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.LinkedHashMap;
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
    /** 任务加签服务 */
    private final TaskAddSignService taskAddSignService;
    /** 知会运行时服务 */
    private final ProcessCcRuntimeService ccRuntimeService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.workflow.process.task.application.operation.NodeOperationDecisionService
            nodeOperationDecisionService;

    /**
     * 查询任务可执行的操作集合（含加签、转办、知会是否可用及加签类型）。
     *
     * @param taskId 任务ID
     * @return 操作集合
     */
    @GetMapping("/api/tasks/{taskId}/operations")
    public Result<Map<String, Object>> operations(@PathVariable String taskId) {
        Map<String, Object> operations = new LinkedHashMap<>(taskAddSignService.operations(taskId));
        operations.put("manualCc", ccRuntimeService.isManualCcAllowed(taskId));
        if (nodeOperationDecisionService != null) {
            Map<String, com.workflow.process.task.application.operation.NodeOperationDecisionService.ActionDecision>
                    decisions = nodeOperationDecisionService.availableActions(taskId);
            operations.put("availableActions", decisions);
            operations.put("approve", decisions.get("approve").allowed());
            operations.put("reject", decisions.get("reject").allowed());
            operations.put("transfer", decisions.get("transfer").allowed());
            operations.put("manualCc", decisions.get("manualCc").allowed());
            operations.put("withdraw", decisions.get("withdraw").allowed());
            operations.put("terminate", decisions.get("terminate").allowed());
            operations.put("addSign",
                    decisions.get("addSignBefore").allowed()
                            || decisions.get("addSignAfter").allowed()
                            || decisions.get("addSignParallel").allowed());
        }
        return Result.success(operations);
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
        requireAddSignAllowed(taskId, request);
        return Result.success(taskAddSignService.addSign(taskId, request));
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
        if (nodeOperationDecisionService != null) {
            nodeOperationDecisionService.requireAllowed(
                    taskId,
                    com.workflow.process.task.application.operation.NodeOperationPolicy.Operation.MANUAL_CC,
                    com.workflow.process.task.application.operation.NodeOperationDecisionService.CheckContext
                            .ofTarget(
                                    request.getComment(),
                                    request.getUserIds() == null
                                            ? java.util.Set.of()
                                            : new java.util.LinkedHashSet<>(request.getUserIds()),
                                    null,
                                    null,
                                    Map.of()));
        }
        return Result.success(Map.of("created", ccRuntimeService.manualCc(taskId, request)));
    }

    /**
     * 撤销加签。
     *
     * @param addSignId 加签记录ID
     * @return 操作结果
     */
    @PostMapping("/api/add-sign/{addSignId}/cancel")
    public Result<Void> cancel(@PathVariable String addSignId) {
        taskAddSignService.cancel(addSignId);
        return Result.success();
    }

    /** 在创建加签任务前统一校验类型、权限、条件、理由和目标范围。 */
    private void requireAddSignAllowed(String taskId, TaskAddSignRequest request) {
        if (nodeOperationDecisionService == null) {
            return;
        }
        String type = request.getType() == null
                ? "PARALLEL"
                : request.getType().trim().toUpperCase(java.util.Locale.ROOT);
        com.workflow.process.task.application.operation.NodeOperationPolicy.Operation operation =
                switch (type) {
                    case "BEFORE" -> com.workflow.process.task.application.operation.NodeOperationPolicy.Operation
                            .ADD_SIGN_BEFORE;
                    case "AFTER" -> com.workflow.process.task.application.operation.NodeOperationPolicy.Operation
                            .ADD_SIGN_AFTER;
                    default -> com.workflow.process.task.application.operation.NodeOperationPolicy.Operation
                            .ADD_SIGN_PARALLEL;
                };
        nodeOperationDecisionService.requireAllowed(
                taskId,
                operation,
                com.workflow.process.task.application.operation.NodeOperationDecisionService.CheckContext
                        .ofTarget(
                                request.getComment(),
                                request.getUserIds() == null
                                        ? java.util.Set.of()
                                        : new java.util.LinkedHashSet<>(request.getUserIds()),
                                null,
                                type,
                                Map.of("completionPolicy",
                                        request.getCompletionPolicy() == null
                                                ? "" : request.getCompletionPolicy())));
    }
}
