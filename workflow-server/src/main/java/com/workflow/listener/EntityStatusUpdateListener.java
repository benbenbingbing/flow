package com.workflow.listener;

import com.workflow.entity.EntityData;
import com.workflow.entity.EntityFlowStatusMapping;
import com.workflow.mapper.EntityDataMapper;
import com.workflow.mapper.EntityFlowStatusMappingMapper;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import com.workflow.entity.ProcessDefinitionConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.event.impl.FlowableEntityEventImpl;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 流程任务完成监听器
 * 在任务完成时自动更新实体数据状态
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EntityStatusUpdateListener implements FlowableEventListener {
    
    private final RuntimeService runtimeService;
    private final EntityDataMapper entityDataMapper;
    private final EntityFlowStatusMappingMapper statusMappingMapper;
    private final ProcessDefinitionConfigMapper processConfigMapper;
    
    @Override
    public void onEvent(FlowableEvent event) {
        if (!(event instanceof FlowableEntityEventImpl)) {
            return;
        }
        
        FlowableEntityEventImpl entityEvent = (FlowableEntityEventImpl) event;
        Object entity = entityEvent.getEntity();
        
        // 监听任务完成事件
        if (entity instanceof TaskEntity) {
            TaskEntity task = (TaskEntity) entity;
            String processInstanceId = task.getProcessInstanceId();
            String taskDefinitionKey = task.getTaskDefinitionKey();
            
            try {
                // 获取流程实例
                ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                        .processInstanceId(processInstanceId)
                        .singleResult();
                
                if (processInstance == null) {
                    return;
                }
                
                // 获取流程变量
                String entityCode = (String) runtimeService.getVariable(processInstanceId, "entityCode");
                String entityDataId = (String) runtimeService.getVariable(processInstanceId, "entityDataId");
                
                if (entityCode == null || entityDataId == null) {
                    log.debug("流程未关联实体数据: processInstanceId={}", processInstanceId);
                    return;
                }
                
                // 获取流程配置
                String processDefinitionKey = processInstance.getProcessDefinitionKey();
                ProcessDefinitionConfig processConfig = processConfigMapper.findByProcessKey(processDefinitionKey)
                        .orElse(null);
                
                if (processConfig == null) {
                    log.warn("未找到流程配置: processKey={}", processDefinitionKey);
                    return;
                }
                
                // 获取目标节点（下一步要流转到的节点）
                // 注意：任务完成时，当前任务节点是源节点，下一个节点是目标节点
                // 由于任务已完成，我们需要根据流程定义和条件来判断下一个节点
                // 这里简化处理：查询从当前节点出发的所有状态映射
                
                // 查找状态映射配置（根据源节点）
                List<EntityFlowStatusMapping> mappings = statusMappingMapper.findByProcessAndSourceNode(
                        processConfig.getId(), taskDefinitionKey);
                
                if (mappings == null || mappings.isEmpty()) {
                    log.debug("未配置状态映射，不更新实体状态: processConfigId={}, sourceNodeId={}", 
                            processConfig.getId(), taskDefinitionKey);
                    return; // 没有配置状态映射，保持原状态不变
                }
                
                // 如果配置了多个映射（如条件分支），根据条件选择
                // 这里简化处理：取第一个有状态配置的映射
                EntityFlowStatusMapping mapping = mappings.stream()
                        .filter(m -> m.getEntityStatusCode() != null && !m.getEntityStatusCode().isEmpty())
                        .findFirst()
                        .orElse(null);
                
                if (mapping == null) {
                    log.debug("状态映射未配置实体状态，不更新: processConfigId={}, sourceNodeId={}",
                            processConfig.getId(), taskDefinitionKey);
                    return; // 映射存在但没有配置具体状态，保持原状态不变
                }
                
                // 更新实体数据状态
                EntityData entityData = entityDataMapper.selectById(entityDataId);
                if (entityData != null) {
                    String oldStatus = entityData.getStatus();
                    
                    // 获取新状态编码
                    String newStatusCode = mapping.getEntityStatusCode();
                    String newStatus = parseStatus(newStatusCode);
                    if (newStatus != null && !newStatus.equals(oldStatus)) {
                        entityData.setStatus(newStatus);
                        entityDataMapper.updateById(entityData);
                        
                        log.info("实体数据状态已更新: entityDataId={}, fromStatus={}, toStatus={}, processNode={}, sourceNode={}, targetNode={}",
                                entityDataId, oldStatus, newStatus, taskDefinitionKey, 
                                mapping.getSourceNodeName(), mapping.getTargetNodeName());
                    } else if (newStatus == null) {
                        log.debug("状态值为空，不更新: processConfigId={}, sourceNodeId={}",
                                processConfig.getId(), taskDefinitionKey);
                    } else {
                        log.debug("状态未变化，不更新: entityDataId={}, status={}", entityDataId, newStatus);
                    }
                }
                
            } catch (Exception e) {
                log.error("更新实体状态失败: processInstanceId={}, taskId={}", processInstanceId, task.getId(), e);
            }
        }
    }
    
    /**
     * 解析状态编码（支持自定义状态）
     * @param statusCode 状态编码或状态名称
     * @return 有效的状态值，如果为空则返回null
     */
    private String parseStatus(String statusCode) {
        if (statusCode == null || statusCode.isEmpty()) {
            return null;
        }
        // 直接返回配置的状态值，支持自定义
        return statusCode.trim();
    }
    
    @Override
    public boolean isFailOnException() {
        return false; // 状态更新失败不应影响流程执行
    }
    
    @Override
    public boolean isFireOnTransactionLifecycleEvent() {
        return false;
    }
    
    @Override
    public String getOnTransaction() {
        return null;
    }
}
