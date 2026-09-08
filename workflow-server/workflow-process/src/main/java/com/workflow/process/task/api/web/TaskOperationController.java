package com.workflow.process.task.api.web;

import com.workflow.core.security.AuthenticatedApi;

import com.workflow.core.result.Result;
import com.workflow.process.task.api.request.TaskAddSignRequest;
import com.workflow.process.cc.api.request.TaskCcRequest;
import com.workflow.process.cc.application.ProcessCcRuntimeService;
import com.workflow.process.task.application.TaskAddSignService;
import com.workflow.process.task.application.operation.NodeOperationCapabilityService;
import com.workflow.process.task.application.operation.NodeOperationDecisionService;
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
    /** 三个节点操作开关的服务端权威判定。 */
    private final NodeOperationCapabilityService nodeOperationCapabilityService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private NodeOperationDecisionService nodeOperationDecisionService;

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
        NodeOperationCapabilityService.OperationCapabilities capabilities =
                nodeOperationCapabilityService.availableCapabilities(taskId);

        // 固有运行状态与节点配置采用 AND，配置不能重新开放正在加签等场景下已关闭的动作。
        boolean intrinsicTransfer = Boolean.TRUE.equals(operations.get("transfer"));
        boolean intrinsicAddSign = Boolean.TRUE.equals(operations.get("addSign"));
        List<String> allowedTypes = ((List<?>) operations.getOrDefault("addSignTypes", List.of()))
                .stream()
                .map(String::valueOf)
                .filter(capabilities.allowedAddSignTypes()::contains)
                .toList();
        operations.put("transfer", intrinsicTransfer && capabilities.transfer());
        operations.put("addSignTypes", allowedTypes);
        operations.put("addSign", intrinsicAddSign
                && capabilities.addSign()
                && !allowedTypes.isEmpty());
        operations.put("terminate", capabilities.terminate());

        // 存量流程仍保留矩阵对其它旧动作的准确约束；新三开关会令旧矩阵自动失效。
        if (nodeOperationDecisionService != null) {
            Map<String, NodeOperationDecisionService.ActionDecision>
                    decisions = nodeOperationDecisionService.availableActions(taskId);
            operations.put("approve", Boolean.TRUE.equals(operations.get("approve"))
                    && decisions.get("approve").allowed());
            operations.put("reject", Boolean.TRUE.equals(operations.get("reject"))
                    && decisions.get("reject").allowed());
            operations.put("manualCc", Boolean.TRUE.equals(operations.get("manualCc"))
                    && decisions.get("manualCc").allowed());
            operations.put("withdraw", decisions.get("withdraw").allowed());
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

}
