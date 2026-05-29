package com.workflow.listener;

import com.workflow.entity.EntityStatus;
import com.workflow.mapper.EntityDataDynamicMapper;
import com.workflow.mapper.EntityStatusMapper;
import com.workflow.service.DynamicTableService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.event.impl.FlowableEntityEventImpl;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程结束监听器
 * 在流程完成或终止时更新实体数据的流程结束时间和状态
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessEndListener implements FlowableEventListener {

    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final EntityDataDynamicMapper dynamicMapper;
    private final DynamicTableService dynamicTableService;
    private final EntityStatusMapper entityStatusMapper;

    @Override
    public void onEvent(FlowableEvent event) {
        if (!(event instanceof FlowableEntityEventImpl)) {
            return;
        }

        FlowableEntityEventImpl entityEvent = (FlowableEntityEventImpl) event;
        String eventType = entityEvent.getType() != null ? entityEvent.getType().name() : "";

        // 只处理流程完成和流程取消/终止事件
        if (!"PROCESS_COMPLETED".equals(eventType) && !"PROCESS_CANCELLED".equals(eventType)) {
            return;
        }

        Object entity = entityEvent.getEntity();

        // 监听流程实例结束事件
        if (entity instanceof ProcessInstance) {
            ProcessInstance processInstance = (ProcessInstance) entity;
            String processInstanceId = processInstance.getId();

            try {
                // 获取流程变量（运行时可能已清理，改查历史变量）
                String entityCode = getHistoricVariable(processInstanceId, "entityCode");
                String entityDataId = getHistoricVariable(processInstanceId, "entityDataId");

                if (entityCode == null || entityDataId == null) {
                    log.debug("流程未关联实体数据: processInstanceId={}", processInstanceId);
                    return;
                }

                // 获取历史流程实例，判断是正常完成还是终止
                HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
                        .processInstanceId(processInstanceId)
                        .singleResult();
                String deleteReason = historicInstance != null ? historicInstance.getDeleteReason() : null;
                boolean isTerminated = deleteReason != null && !deleteReason.isEmpty();

                // 根据结束类型确定状态分类
                String statusCategory = isTerminated ? "TERMINATED" : "COMPLETED";
                String statusCode = getEntityStatusCode(entityCode, statusCategory);

                // 更新实体数据
                String tableName = dynamicTableService.getTableName(entityCode);
                Map<String, Object> updateData = new HashMap<>();
                updateData.put("id", entityDataId);
                updateData.put("process_end_time", LocalDateTime.now());
                updateData.put("updated_at", LocalDateTime.now());
                // 更新状态
                if (statusCode != null) {
                    updateData.put("status", statusCode);
                }
                // 清空当前任务信息
                updateData.put("current_task_id", null);
                updateData.put("current_task_name", null);
                updateData.put("current_task_assignee", null);

                dynamicMapper.update(tableName, updateData);

                log.info("流程结束，已更新实体数据: entityCode={}, entityDataId={}, processInstanceId={}, statusCategory={}, statusCode={}",
                        entityCode, entityDataId, processInstanceId, statusCategory, statusCode);

            } catch (Exception e) {
                log.error("更新流程结束状态失败: processInstanceId={}", processInstanceId, e);
            }
        }
    }

    /**
     * 获取实体指定分类的状态码
     */
    private String getEntityStatusCode(String entityCode, String category) {
        try {
            List<EntityStatus> statuses = entityStatusMapper.findByCategory(entityCode, category);
            if (statuses != null && !statuses.isEmpty()) {
                return statuses.get(0).getStatusCode();
            }
        } catch (Exception e) {
            log.warn("获取实体[{}]状态失败: {}", entityCode, e.getMessage());
        }
        // 兜底默认值
        return "TERMINATED".equals(category) ? "REJECTED" : "APPROVED";
    }

    /**
     * 从历史变量查询指定变量值
     */
    private String getHistoricVariable(String processInstanceId, String variableName) {
        try {
            var varInstance = historyService.createHistoricVariableInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .variableName(variableName)
                    .singleResult();
            return varInstance != null ? (String) varInstance.getValue() : null;
        } catch (Exception e) {
            log.warn("查询历史变量失败: processInstanceId={}, variableName={}", processInstanceId, variableName, e);
            return null;
        }
    }

    @Override
    public boolean isFailOnException() {
        return false; // 更新失败不应影响流程执行
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
