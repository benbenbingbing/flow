package com.workflow.listener;

import com.workflow.mapper.EntityDataDynamicMapper;
import com.workflow.service.DynamicTableService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.event.impl.FlowableEntityEventImpl;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 流程结束监听器
 * 在流程完成或终止时更新实体数据的流程结束时间
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessEndListener implements FlowableEventListener {

    private final RuntimeService runtimeService;
    private final EntityDataDynamicMapper dynamicMapper;
    private final DynamicTableService dynamicTableService;

    @Override
    public void onEvent(FlowableEvent event) {
        if (!(event instanceof FlowableEntityEventImpl)) {
            return;
        }

        FlowableEntityEventImpl entityEvent = (FlowableEntityEventImpl) event;
        Object entity = entityEvent.getEntity();

        // 监听流程实例结束事件
        if (entity instanceof ProcessInstance) {
            ProcessInstance processInstance = (ProcessInstance) entity;
            String processInstanceId = processInstance.getId();

            try {
                // 获取流程变量
                String entityCode = (String) runtimeService.getVariable(processInstanceId, "entityCode");
                String entityDataId = (String) runtimeService.getVariable(processInstanceId, "entityDataId");

                if (entityCode == null || entityDataId == null) {
                    log.debug("流程未关联实体数据: processInstanceId={}", processInstanceId);
                    return;
                }

                // 更新实体数据的流程结束时间
                String tableName = dynamicTableService.getTableName(entityCode);
                Map<String, Object> updateData = new HashMap<>();
                updateData.put("id", entityDataId);
                updateData.put("process_end_time", LocalDateTime.now());
                updateData.put("updated_at", LocalDateTime.now());

                dynamicMapper.update(tableName, updateData);

                log.info("流程结束，已更新实体数据流程结束时间: entityCode={}, entityDataId={}, processInstanceId={}",
                        entityCode, entityDataId, processInstanceId);

            } catch (Exception e) {
                log.error("更新流程结束时间失败: processInstanceId={}", processInstanceId, e);
            }
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
