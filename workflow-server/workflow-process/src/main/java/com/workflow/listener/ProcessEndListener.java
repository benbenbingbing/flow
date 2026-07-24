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

    /** Flowable 运行时服务，已运行时变量被清理后改用历史变量 */
    private final RuntimeService runtimeService;
    /** Flowable 历史服务，查询历史实例与变量 */
    private final HistoryService historyService;
    /** 动态实体数据 Mapper，更新实体结束状态 */
    private final EntityDataDynamicMapper dynamicMapper;
    /** 动态表服务，获取实体对应动态表名 */
    private final DynamicTableService dynamicTableService;
    /** 实体状态 Mapper，查询结束状态码 */
    private final EntityStatusMapper entityStatusMapper;

    /**
     * 流程结束事件处理：根据结束类型（完成/撤回/终止）更新实体结束时间与状态，并清空当前任务。
     *
     * @param event Flowable 引擎事件，仅处理 PROCESS_COMPLETED 与 PROCESS_CANCELLED
     */
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
                boolean isWithdrawn = deleteReason != null && deleteReason.startsWith("发起人撤回");
                boolean isTerminated = deleteReason != null && !deleteReason.isEmpty();

                // 根据结束类型确定状态分类
                String statusCategory = isWithdrawn
                        ? "WITHDRAWN"
                        : (isTerminated ? "TERMINATED" : "COMPLETED");
                String tableName = dynamicTableService.getTableName(entityCode);
                Map<String, Object> entityData = dynamicMapper.selectById(tableName, entityDataId);
                String currentStatus = entityData != null && entityData.get("status") != null
                        ? String.valueOf(entityData.get("status"))
                        : null;
                String statusCode = resolveEndStatusCode(entityCode, statusCategory, currentStatus);

                // 更新实体数据
                Map<String, Object> updateData = new HashMap<>();
                updateData.put("id", entityDataId);
                updateData.put("process_end_time", LocalDateTime.now());
                updateData.put("update_time", LocalDateTime.now());
                // 更新状态
                if (statusCode != null) {
                    updateData.put("status", statusCode);
                }
                dynamicMapper.update(tableName, updateData);
                dynamicMapper.updateCurrentTask(tableName, entityDataId, null, null, null);

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
        if ("WITHDRAWN".equals(category)) {
            return "WITHDRAWN";
        }
        return "TERMINATED".equals(category) ? "TERMINATED" : "APPROVED";
    }

    /**
     * 解析流程结束应写入的状态码。
     * <p>
     * 若实体当前状态与结束分类一致则保留当前状态，否则取该分类下的状态码。
     *
     * @param entityCode    实体编码
     * @param category       结束分类（WITHDRAWN/TERMINATED/COMPLETED）
     * @param currentStatus  实体当前状态码，可为空
     * @return 最终写入的状态码
     */
    private String resolveEndStatusCode(String entityCode, String category, String currentStatus) {
        if (currentStatus != null && !currentStatus.isBlank()) {
            EntityStatus currentStatusDefinition = entityStatusMapper.findByEntityAndCode(entityCode, currentStatus);
            if (shouldPreserveStatus(currentStatusDefinition, category)) {
                return currentStatus;
            }
        }
        return getEntityStatusCode(entityCode, category);
    }

    /**
     * 判断是否保留当前状态：当前状态定义的分类与结束分类一致时保留。
     *
     * @param status       实体状态定义，可为 null
     * @param endCategory  结束分类
     * @return true 表示保留当前状态不变
     */
    static boolean shouldPreserveStatus(EntityStatus status, String endCategory) {
        return status != null && endCategory != null && endCategory.equals(status.getStatusCategory());
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
