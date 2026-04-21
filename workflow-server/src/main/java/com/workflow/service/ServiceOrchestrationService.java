package com.workflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.workflow.entity.ServiceCategory;
import com.workflow.entity.ServiceDefinition;
import com.workflow.entity.ServiceExecutionLog;
import com.workflow.entity.ServiceNode;
import com.workflow.mapper.ServiceCategoryMapper;
import com.workflow.mapper.ServiceDefinitionMapper;
import com.workflow.mapper.ServiceExecutionLogMapper;
import com.workflow.mapper.ServiceNodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 服务编排管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceOrchestrationService extends ServiceImpl<ServiceDefinitionMapper, ServiceDefinition> {
    
    private final ServiceDefinitionMapper serviceDefinitionMapper;
    private final ServiceNodeMapper nodeMapper;
    private final ServiceExecutionLogMapper executionLogMapper;
    private final ServiceCategoryMapper categoryMapper;
    private final ServiceOrchestrationEngine executionEngine;
    
    /**
     * 分页查询服务列表
     */
    public Page<ServiceDefinition> getServiceList(String keyword, String serviceType, 
                                                   String categoryId, int pageNum, int pageSize) {
        Page<ServiceDefinition> page = new Page<>(pageNum, pageSize);
        return serviceDefinitionMapper.selectServiceList(page, keyword, serviceType, categoryId);
    }
    
    /**
     * 根据ID查询服务详情
     */
    public ServiceDefinition getServiceDetail(String id) {
        ServiceDefinition service = serviceDefinitionMapper.selectById(id);
        if (service != null && StringUtils.hasText(service.getCategoryId())) {
            ServiceCategory category = categoryMapper.selectById(service.getCategoryId());
            if (category != null) {
                service.setCategoryName(category.getCategoryName());
            }
        }
        return service;
    }
    
    /**
     * 获取服务完整配置（含节点）
     */
    public ServiceConfigVO getServiceConfig(String id) {
        ServiceDefinition service = getServiceDetail(id);
        if (service == null) {
            return null;
        }
        
        ServiceConfigVO config = new ServiceConfigVO();
        config.setService(service);
        config.setNodes(nodeMapper.findByServiceId(id));
        
        return config;
    }
    
    /**
     * 保存服务（含节点）
     */
    @Transactional(rollbackFor = Exception.class)
    public ServiceDefinition saveService(ServiceDefinition service, List<ServiceNode> nodes) {
        // 生成编码
        if (!StringUtils.hasText(service.getServiceCode())) {
            service.setServiceCode("SVC_" + System.currentTimeMillis());
        }
        
        // 保存服务定义
        if (service.getId() == null) {
            service.setVersion(1);
            service.setStatus("ACTIVE");
            serviceDefinitionMapper.insert(service);
        } else {
            service.setVersion(service.getVersion() + 1);
            serviceDefinitionMapper.updateById(service);
            // 删除旧节点
            nodeMapper.deleteByServiceId(service.getId());
        }
        
        // 保存节点
        if (nodes != null && !nodes.isEmpty()) {
            for (ServiceNode node : nodes) {
                node.setServiceId(service.getId());
                nodeMapper.insert(node);
            }
        }
        
        return service;
    }
    
    /**
     * 删除服务
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteService(String id) {
        nodeMapper.deleteByServiceId(id);
        serviceDefinitionMapper.deleteById(id);
    }
    
    /**
     * 执行服务
     */
    public ServiceOrchestrationEngine.ExecutionResult executeService(String serviceId, 
                                                                      Map<String, Object> inputParams) {
        ServiceDefinition service = serviceDefinitionMapper.selectById(serviceId);
        if (service == null) {
            throw new RuntimeException("服务不存在: " + serviceId);
        }
        
        return executionEngine.execute(service, inputParams);
    }
    
    /**
     * 获取执行日志列表
     */
    public Page<ServiceExecutionLog> getExecutionLogs(String serviceId, String status, 
                                                       int pageNum, int pageSize) {
        Page<ServiceExecutionLog> page = new Page<>(pageNum, pageSize);
        return executionLogMapper.selectExecutionList(page, serviceId, status);
    }
    
    /**
     * 获取所有分类
     */
    public List<ServiceCategory> getCategories() {
        return categoryMapper.findAll();
    }
    
    /**
     * VO类
     */
    @lombok.Data
    public static class ServiceConfigVO {
        private ServiceDefinition service;
        private List<ServiceNode> nodes;
    }
}
