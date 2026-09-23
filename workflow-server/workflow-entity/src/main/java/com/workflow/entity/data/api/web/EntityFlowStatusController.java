package com.workflow.entity.data.api.web;

import com.workflow.core.security.RequiresPermission;

import com.workflow.core.result.Result;
import com.workflow.entity.data.infrastructure.persistence.record.EntityFlowStatusMapping;
import com.workflow.entity.data.application.EntityFlowStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 实体流程状态映射控制器
 */
@RequiresPermission("entity:definition:view")
@RestController
@RequestMapping("/api/process-entity-status-mappings")
@RequiredArgsConstructor
public class EntityFlowStatusController {
    
    private final EntityFlowStatusService entityFlowStatusService;
    
    /**
     * 保存流程状态映射配置
     *
     * @param processConfigId 流程配置ID，后续用于保存状态映射集合时定位或关联目标
     * @param request 本次请求，后续经校验后用于保存状态映射集合
     * @return 保存后的状态映射集合结果，供调用方继续处理
     */
    @PostMapping("/process/{processConfigId}/update")
    @RequiresPermission("entity:definition:manage")
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
     *
     * @param processConfigId 流程配置ID，后续用于读取状态映射集合时定位或关联目标
     * @return 符合条件的实体流程状态映射结果，供调用方继续处理
     */
    @GetMapping("/process/{processConfigId}")
    public Result<List<EntityFlowStatusMapping>> getStatusMappings(@PathVariable String processConfigId) {
        List<EntityFlowStatusMapping> mappings = entityFlowStatusService.getStatusMappings(processConfigId);
        return Result.success(mappings);
    }
    
    /**
     * 根据流程标识查询
     *
     * @param processKey 流程键，后续用于授权校验、关联或幂等去重
     * @return 符合条件的实体流程状态映射结果，供调用方继续处理
     */
    @GetMapping("/process-key/{processKey}")
    public Result<List<EntityFlowStatusMapping>> getStatusMappingsByProcessKey(@PathVariable String processKey) {
        List<EntityFlowStatusMapping> mappings = entityFlowStatusService.getStatusMappingsByProcessKey(processKey);
        return Result.success(mappings);
    }
    
    /**
     * 删除流程的状态映射配置
     *
     * @param processConfigId 流程配置ID，后续用于删除流程配置ID时定位或关联目标
     * @return 删除后的流程配置ID结果，供调用方继续处理
     */
    @PostMapping("/process/{processConfigId}/delete")
    @RequiresPermission("entity:definition:manage")
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
        
        /**
         * 读取流程键；查询结果供调用方展示或继续处理。
         *
         * @return 读取后的流程键文本，供调用方比较或展示
         */
        public String getProcessKey() { return processKey; }
        /**
         * 设置流程键；后续读取或执行将使用更新后的状态。
         *
         * @param processKey 流程键，后续用于授权校验、关联或幂等去重
         */
        public void setProcessKey(String processKey) { this.processKey = processKey; }
        /**
         * 读取实体编码；查询结果供调用方展示或继续处理。
         *
         * @return 读取后的实体编码文本，供调用方比较或展示
         */
        public String getEntityCode() { return entityCode; }
        /**
         * 设置实体编码；后续读取或执行将使用更新后的状态。
         *
         * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
         */
        public void setEntityCode(String entityCode) { this.entityCode = entityCode; }
        /**
         * 读取映射集合；查询结果供调用方展示或继续处理。
         *
         * @return 实体流程状态映射集合，供调用方遍历或展示
         */
        public List<EntityFlowStatusMapping> getMappings() { return mappings; }
        /**
         * 设置映射集合；后续读取或执行将使用更新后的状态。
         *
         * @param mappings 映射集合，供本方法设置映射集合时使用
         */
        public void setMappings(List<EntityFlowStatusMapping> mappings) { this.mappings = mappings; }
    }
}
