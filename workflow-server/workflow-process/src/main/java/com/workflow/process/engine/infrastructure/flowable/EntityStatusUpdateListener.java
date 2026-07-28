package com.workflow.process.engine.infrastructure.flowable;

import com.workflow.entity.data.infrastructure.persistence.record.EntityFlowStatusMapping;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityFlowStatusMappingMapper;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import com.workflow.process.status.application.ProcessStatusSyncPublisher;
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

/**
 * 流程任务完成监听器。
 * 在任务事务内写入持久化状态同步事件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EntityStatusUpdateListener implements FlowableEventListener {
    
    /** Flowable 运行时服务，查询流程实例与变量 */
    private final RuntimeService runtimeService;
    /** 流程状态映射 Mapper，查询节点到实体状态的映射 */
    private final EntityFlowStatusMappingMapper statusMappingMapper;
    /** 流程定义配置 Mapper，查询流程配置 */
    private final ProcessDefinitionConfigMapper processConfigMapper;
    /** 事务内发布持久化状态同步事件 */
    private final ProcessStatusSyncPublisher statusSyncPublisher;

    /**
     * 任务完成事件处理：根据节点状态映射写入关联实体状态同步事件。
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
                
                String newStatus = parseStatus(
                        mapping.getEntityStatusCode());
                if (newStatus != null) {
                    statusSyncPublisher.publishTaskStatus(
                            processInstanceId,
                            task.getId(),
                            entityCode,
                            entityDataId,
                            newStatus);
                    log.info(
                            "实体状态同步事件已入队: entityDataId={}, "
                                    + "toStatus={}, processNode={}",
                            entityDataId,
                            newStatus,
                            taskDefinitionKey);
                }
                
            } catch (Exception e) {
                throw new IllegalStateException(
                        "实体状态同步事件入队失败: processInstanceId="
                                + processInstanceId
                                + ", taskId=" + task.getId(),
                        e);
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
     * @return 固定 true，事件无法持久化时必须回滚流程事务
     */
    @Override
    public boolean isFailOnException() {
        return true;
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
