package com.workflow.listener;

import com.workflow.entity.EntityFlowStatusMapping;
import com.workflow.mapper.EntityDataDynamicMapper;
import com.workflow.mapper.EntityFlowStatusMappingMapper;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.service.DynamicTableService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.event.impl.FlowableEntityEventImpl;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * 流程任务完成监听器
 * 在任务完成时自动更新实体数据状态
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EntityStatusUpdateListener implements FlowableEventListener {
    
    /** Flowable 运行时服务，查询流程实例与变量 */
    private final RuntimeService runtimeService;
    /** 动态实体数据 Mapper，读取/更新实体状态 */
    private final EntityDataDynamicMapper entityDataDynamicMapper;
    /** 动态表服务，获取实体对应动态表名 */
    private final DynamicTableService dynamicTableService;
    /** 流程状态映射 Mapper，查询节点到实体状态的映射 */
    private final EntityFlowStatusMappingMapper statusMappingMapper;
    /** 流程定义配置 Mapper，查询流程配置 */
    private final ProcessDefinitionConfigMapper processConfigMapper;

    /**
     * 任务完成事件处理：根据节点状态映射更新关联实体数据的状态。
     * <p>
     * 仅处理 TASK_COMPLETED 事件；流程未关联实体或无状态映射时跳过。
     *
     * @param event Flowable 引擎事件
     */
    @Override
    public void onEvent(FlowableEvent event) {
        if (event == null
                || event.getType() != FlowableEngineEventType.TASK_COMPLETED
                || !(event instanceof FlowableEntityEventImpl)) {
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
                String tableName = dynamicTableService.getTableName(entityCode);
                Map<String, Object> entityData = entityDataDynamicMapper.selectById(tableName, entityDataId);
                if (entityData != null) {
                    String oldStatus = entityData.get("status") != null
                            ? String.valueOf(entityData.get("status"))
                            : null;
                    
                    // 获取新状态编码
                    String newStatusCode = mapping.getEntityStatusCode();
                    String newStatus = parseStatus(newStatusCode);
                    if (newStatus != null && !newStatus.equals(oldStatus)) {
                        Map<String, Object> updateData = new HashMap<>();
                        updateData.put("id", entityDataId);
                        updateData.put("status", newStatus);
                        entityDataDynamicMapper.update(tableName, updateData);
                        
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

    /**
     * 是否在事件处理抛出异常时失败回滚流程。
     *
     * @return 固定 false，状态更新失败不应影响流程执行
     */
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
