package com.workflow.controller;

import com.workflow.dto.ApiResponse;
import com.workflow.dto.ProcessDefinitionDTO;
import com.workflow.dto.ProcessVersionHistoryDTO;
import com.workflow.service.ProcessDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 流程定义控制器
 * 
 * @description 提供流程定义相关的RESTful API接口
 *              包括流程的增删改查、发布、禁用等操作
 * @author Workflow Team
 * @version 1.0.0
 */
@RestController
@RequestMapping("/api/process")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProcessDefinitionController {

    /**
     * 流程定义服务
     */
    private final ProcessDefinitionService processService;

    /**
     * 获取所有流程定义列表
     * 
     * @return 流程定义列表
     */
    @GetMapping
    public ApiResponse<List<ProcessDefinitionDTO>> list() {
        return ApiResponse.success(processService.findAll());
    }

    /**
     * 获取已发布的流程定义列表
     * 
     * @return 已发布的流程定义列表
     */
    @GetMapping("/published")
    public ApiResponse<List<ProcessDefinitionDTO>> listPublished() {
        return ApiResponse.success(processService.findByStatus(com.workflow.entity.ProcessDefinitionConfig.ProcessStatus.PUBLISHED));
    }

    /**
     * 获取所有未被实体绑定的流程定义列表
     * 用于实体绑定流程时选择，不限于已发布状态
     * 
     * @return 未被绑定的流程定义列表
     */
    @GetMapping("/unbound")
    public ApiResponse<List<ProcessDefinitionDTO>> listUnbound() {
        return ApiResponse.success(processService.findAllUnbound());
    }
    
    /**
     * 获取所有可用于绑定的流程定义列表
     * 包括当前已绑定的流程和未绑定的流程
     * 
     * @param currentProcessId 当前绑定的流程ID（可选）
     * @return 可用于绑定的流程定义列表
     */
    @GetMapping("/bindable")
    public ApiResponse<List<ProcessDefinitionDTO>> listBindable(
            @RequestParam(required = false) String currentProcessId) {
        return ApiResponse.success(processService.findAllBindable(currentProcessId));
    }

    /**
     * 根据ID获取流程定义详情
     * 
     * @param id 流程定义ID
     * @return 流程定义详情
     */
    @GetMapping("/{id}")
    public ApiResponse<ProcessDefinitionDTO> getById(@PathVariable String id) {
        return ApiResponse.success(processService.findById(id));
    }

    /**
     * 根据流程标识获取流程定义详情
     * 
     * @param processKey 流程标识（如：leave_process）
     * @return 流程定义详情
     */
    @GetMapping("/key/{processKey}")
    public ApiResponse<ProcessDefinitionDTO> getByKey(@PathVariable String processKey) {
        return ApiResponse.success(processService.findByProcessKey(processKey));
    }

    /**
     * 创建新流程定义
     * 
     * @param dto 流程定义数据
     * @return 创建后的流程定义
     */
    @PostMapping
    public ApiResponse<ProcessDefinitionDTO> create(@RequestBody ProcessDefinitionDTO dto) {
        return ApiResponse.success(processService.save(dto));
    }

    /**
     * 更新流程定义
     * 
     * @param id 流程定义ID
     * @param dto 更新的流程定义数据
     * @return 更新后的流程定义
     */
    @PutMapping("/{id}")
    public ApiResponse<ProcessDefinitionDTO> update(@PathVariable String id, @RequestBody ProcessDefinitionDTO dto) {
        return ApiResponse.success(processService.update(id, dto));
    }

    /**
     * 删除流程定义
     * 
     * @param id 流程定义ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        processService.delete(id);
        return ApiResponse.success();
    }

    /**
     * 发布流程定义
     * 发布后会部署到流程引擎，可以启动流程实例
     * 
     * @param id 流程定义ID
     * @param request 发布请求，可包含versionDescription版本说明
     * @return 发布后的流程定义
     */
    @PostMapping("/{id}/publish")
    public ApiResponse<ProcessDefinitionDTO> publish(@PathVariable String id, @RequestBody(required = false) Map<String, String> request) {
        String versionDescription = request != null ? request.get("versionDescription") : null;
        return ApiResponse.success(processService.publish(id, versionDescription));
    }

    /**
     * 禁用流程定义
     * 禁用后不能启动新的流程实例
     * 
     * @param id 流程定义ID
     * @return 禁用后的流程定义
     */
    @PostMapping("/{id}/disable")
    public ApiResponse<ProcessDefinitionDTO> disable(@PathVariable String id) {
        return ApiResponse.success(processService.disable(id));
    }
    
    // ==================== 版本管理接口 ====================
    
    /**
     * 获取流程的所有版本历史
     * 
     * @param processId 流程定义ID
     * @return 版本历史列表
     */
    @GetMapping("/{processId}/versions")
    public ApiResponse<List<ProcessVersionHistoryDTO>> getVersions(@PathVariable String processId) {
        return ApiResponse.success(processService.findVersionsByProcessId(processId));
    }
    
    /**
     * 获取指定版本的历史记录详情
     * 
     * @param versionId 版本历史记录ID
     * @return 版本历史详情
     */
    @GetMapping("/versions/{versionId}")
    public ApiResponse<ProcessVersionHistoryDTO> getVersionById(@PathVariable String versionId) {
        return ApiResponse.success(processService.findVersionById(versionId));
    }
    
    /**
     * 回滚到指定版本
     * 将流程恢复到指定版本的状态（创建新版本）
     * 
     * @param processId 流程定义ID
     * @param versionId 目标版本历史记录ID
     * @param request 回滚请求，包含reason回滚原因
     * @return 更新后的流程定义
     */
    @PostMapping("/{processId}/rollback/{versionId}")
    public ApiResponse<ProcessDefinitionDTO> rollbackToVersion(
            @PathVariable String processId,
            @PathVariable String versionId,
            @RequestBody(required = false) Map<String, String> request) {
        String reason = request != null ? request.get("reason") : "手动回滚";
        return ApiResponse.success(processService.rollbackToVersion(processId, versionId, reason));
    }
    
    /**
     * 删除版本
     * 
     * @param versionId 版本ID
     * @return 操作结果
     */
    @DeleteMapping("/versions/{versionId}")
    public ApiResponse<Void> deleteVersion(@PathVariable String versionId) {
        processService.deleteVersion(versionId);
        return ApiResponse.success();
    }
}
