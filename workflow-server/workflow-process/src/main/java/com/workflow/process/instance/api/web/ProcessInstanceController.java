package com.workflow.process.instance.api.web;

import com.workflow.core.security.AuthenticatedApi;

import com.workflow.core.result.PageResult;
import com.workflow.core.result.Result;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.ForbiddenException;
import com.workflow.core.result.ApiResponse;
import com.workflow.process.instance.api.response.ProcessProgressDTO;
import com.workflow.process.task.api.request.ReceiveTaskTriggerRequest;
import com.workflow.process.instance.application.ProcessInstanceService;
import com.workflow.process.workbench.api.response.MyStartedProcessVO;
import com.workflow.process.instance.api.response.ProcessDetailVO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 流程实例控制器
 * 提供流程实例查询、进度追踪等接口
 */
@AuthenticatedApi(objectAuthorization = true)
@RestController
@RequestMapping("/api/process-instance")
@RequiredArgsConstructor
public class ProcessInstanceController {
    
    private final ProcessInstanceService processInstanceService;
    
    /**
     * 获取流程实例的执行进度
     * 
     * @param processInstanceId 流程实例ID
     * @return 流程进度信息，包含已完成节点、当前活动节点、BPMN XML等
     */
    @GetMapping("/{processInstanceId}/progress")
    public ApiResponse<ProcessProgressDTO> getProcessProgress(@PathVariable String processInstanceId) {
        return ApiResponse.success(processInstanceService.getProcessProgress(processInstanceId));
    }
    
    /**
     * 获取流程实例详情
     * 
     * @param instanceId 流程实例ID
     * @return 流程详情信息
     */
    @GetMapping("/{instanceId}/detail")
    public Result<ProcessDetailVO> getProcessDetail(@PathVariable String instanceId) {
        return Result.success(processInstanceService.getProcessDetail(instanceId));
    }
    
    /**
     * 获取我发起的流程列表
     * 
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param processName 流程名称（可选）
     * @param startDate 发起日期下限（可选）
     * @param endDate 发起日期上限（可选，包含当天）
     * @return 我发起的流程列表
     */
    @GetMapping("/my-started")
    public Result<PageResult<MyStartedProcessVO>> getMyStartedList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String processName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        String userId = UserContext.getUserId();
        if (userId == null || userId.isEmpty()) {
            userId = UserContext.getUsername();
        }
        if (userId == null || userId.isEmpty()) {
            throw new ForbiddenException("用户未登录");
        }
        return Result.success(processInstanceService.getMyStartedList(
                userId, pageNum, pageSize, processName, startDate, endDate));
    }
    
    /**
     * 终止流程实例
     * 
     * @param processInstanceId 流程实例ID
     * @param requestBody 请求体，包含reason（终止原因，可选）
     * @return 操作结果
     */
    @PostMapping("/{processInstanceId}/terminate")
    public Result<Void> terminateProcess(
            @PathVariable String processInstanceId,
            @RequestBody(required = false) Map<String, String> requestBody) {
        String userId = UserContext.getUserId();
        if (userId == null || userId.isEmpty()) {
            userId = UserContext.getUsername();
        }
        if (userId == null || userId.isEmpty()) {
            throw new ForbiddenException("用户未登录");
        }
        
        String reason = requestBody != null ? requestBody.get("reason") : null;
        return processInstanceService.terminateProcess(processInstanceId, userId, reason);
    }
    
    /**
     * 获取流程实例的BPMN XML
     * 
     * @param processInstanceId 流程实例ID
     * @return BPMN XML字符串
     */
    @GetMapping("/{processInstanceId}/xml")
    public Result<String> getProcessXml(@PathVariable String processInstanceId) {
        String xml = processInstanceService.getBpmnXmlByProcessInstanceId(processInstanceId);
        return Result.success(xml);
    }

    /**
     * 触发流程实例中处于等待状态的接收任务（ReceiveTask）继续流转。
     *
     * @param processInstanceId 流程实例ID
     * @param request          触发请求（executionId/activityId 定位，可校验消息标识）
     * @return 包含流程实例ID与被执行实例ID的响应
     */
    @PostMapping("/{processInstanceId}/receive")
    public ApiResponse<Map<String, String>> triggerReceiveTask(
            @PathVariable String processInstanceId,
            @RequestBody ReceiveTaskTriggerRequest request) {
        String executionId = processInstanceService.triggerReceiveTask(processInstanceId, request);
        return ApiResponse.success(Map.of(
                "processInstanceId", processInstanceId,
                "executionId", executionId));
    }
}
