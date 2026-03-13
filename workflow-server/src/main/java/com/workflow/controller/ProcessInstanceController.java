package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.dto.ApiResponse;
import com.workflow.dto.ProcessProgressDTO;
import com.workflow.service.ProcessInstanceService;
import com.workflow.vo.ProcessDetailVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 流程实例控制器
 * 提供流程实例查询、进度追踪等接口
 */
@RestController
@RequestMapping("/api/process-instance")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
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
}
