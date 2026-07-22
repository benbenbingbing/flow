package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.entity.EntityFlowStatusMapping;
import com.workflow.service.EntityFlowStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 实体流程状态映射控制器
 */
@RestController
@RequestMapping("/api/process-entity-status-mappings")
@RequiredArgsConstructor
public class EntityFlowStatusController {
    
    private final EntityFlowStatusService entityFlowStatusService;
    
    /**
     * 保存流程状态映射配置
     */
    @PostMapping("/process/{processConfigId}/update")
    public Result<Void> saveStatusMappings(
            @PathVariable String processConfigId,
            @RequestBody SaveStatusMappingRequest request) {
        entityFlowStatusService.saveStatusMappings(
                processConfigId, 
                request.getProcessKey(), 
                request.getEntityCode(),
                request.getMappings()
        );
        return Result.success();
    }
    
    /**
     * 查询流程的状态映射配置
     */
    @GetMapping("/process/{processConfigId}")
    public Result<List<EntityFlowStatusMapping>> getStatusMappings(@PathVariable String processConfigId) {
        List<EntityFlowStatusMapping> mappings = entityFlowStatusService.getStatusMappings(processConfigId);
        return Result.success(mappings);
    }
    
    /**
     * 根据流程标识查询
     */
    @GetMapping("/process-key/{processKey}")
    public Result<List<EntityFlowStatusMapping>> getStatusMappingsByProcessKey(@PathVariable String processKey) {
        List<EntityFlowStatusMapping> mappings = entityFlowStatusService.getStatusMappingsByProcessKey(processKey);
        return Result.success(mappings);
    }
    
    /**
     * 删除流程的状态映射配置
     */
    @PostMapping("/process/{processConfigId}/delete")
    public Result<Void> deleteByProcessConfigId(@PathVariable String processConfigId) {
        entityFlowStatusService.deleteByProcessConfigId(processConfigId);
        return Result.success();
    }
    
    /**
     * 保存请求体
     */
    public static class SaveStatusMappingRequest {
        private String processKey;
        private String entityCode;
        private List<EntityFlowStatusMapping> mappings;
        
        public String getProcessKey() { return processKey; }
        public void setProcessKey(String processKey) { this.processKey = processKey; }
        public String getEntityCode() { return entityCode; }
        public void setEntityCode(String entityCode) { this.entityCode = entityCode; }
        public List<EntityFlowStatusMapping> getMappings() { return mappings; }
        public void setMappings(List<EntityFlowStatusMapping> mappings) { this.mappings = mappings; }
    }
}
