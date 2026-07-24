package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.dto.TaskAddSignRequest;
import com.workflow.dto.TaskCcRequest;
import com.workflow.service.ProcessCcRuntimeService;
import com.workflow.service.TaskAddSignService;
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
@RestController
@RequiredArgsConstructor
public class TaskOperationController {
    /** 任务加签服务 */
    private final TaskAddSignService taskAddSignService;
    /** 知会运行时服务 */
    private final ProcessCcRuntimeService ccRuntimeService;

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
