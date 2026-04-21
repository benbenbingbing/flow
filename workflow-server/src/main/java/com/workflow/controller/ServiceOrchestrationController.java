package com.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.dto.ApiResponse;
import com.workflow.entity.ServiceCategory;
import com.workflow.entity.ServiceDefinition;
import com.workflow.entity.ServiceExecutionLog;
import com.workflow.entity.ServiceNode;
import com.workflow.service.ServiceOrchestrationEngine;
import com.workflow.service.ServiceOrchestrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 服务编排控制器
 */
@RestController
@RequestMapping("/api/service-orchestration")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ServiceOrchestrationController {
    
    private final ServiceOrchestrationService orchestrationService;
    
    /**
     * 分页查询服务列表
     */
    @GetMapping("/list")
    public ApiResponse<Page<ServiceDefinition>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String serviceType,
            @RequestParam(required = false) String categoryId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(orchestrationService.getServiceList(keyword, serviceType, categoryId, pageNum, pageSize));
    }
    
    /**
     * 根据ID查询服务详情
     */
    @GetMapping("/{id}")
    public ApiResponse<ServiceDefinition> getById(@PathVariable String id) {
        ServiceDefinition service = orchestrationService.getServiceDetail(id);
        if (service == null) {
            return ApiResponse.error(404, "服务不存在");
        }
        return ApiResponse.success(service);
    }
    
    /**
     * 查询服务完整配置（含节点）
     */
    @GetMapping("/{id}/config")
    public ApiResponse<ServiceOrchestrationService.ServiceConfigVO> getServiceConfig(@PathVariable String id) {
        ServiceOrchestrationService.ServiceConfigVO config = orchestrationService.getServiceConfig(id);
        if (config == null) {
            return ApiResponse.error(404, "服务不存在");
        }
        return ApiResponse.success(config);
    }
    
    /**
     * 保存服务
     */
    @PostMapping("/save")
    public ApiResponse<ServiceDefinition> save(@RequestBody ServiceConfigDTO dto) {
        ServiceDefinition service = orchestrationService.saveService(dto.getService(), dto.getNodes());
        return ApiResponse.success(service);
    }
    
    /**
     * 删除服务
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        orchestrationService.deleteService(id);
        return ApiResponse.success();
    }
    
    /**
     * 执行服务
     */
    @PostMapping("/{id}/execute")
    public ApiResponse<ServiceOrchestrationEngine.ExecutionResult> execute(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> inputParams) {
        return ApiResponse.success(orchestrationService.executeService(id, inputParams != null ? inputParams : new java.util.HashMap<>()));
    }
    
    /**
     * 获取执行日志
     */
    @GetMapping("/{id}/logs")
    public ApiResponse<Page<ServiceExecutionLog>> getExecutionLogs(
            @PathVariable String id,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(orchestrationService.getExecutionLogs(id, status, pageNum, pageSize));
    }
    
    /**
     * 获取服务分类
     */
    @GetMapping("/categories")
    public ApiResponse<List<ServiceCategory>> getCategories() {
        return ApiResponse.success(orchestrationService.getCategories());
    }
    
    /**
     * DTO
     */
    @lombok.Data
    public static class ServiceConfigDTO {
        private ServiceDefinition service;
        private List<ServiceNode> nodes;
    }
}
