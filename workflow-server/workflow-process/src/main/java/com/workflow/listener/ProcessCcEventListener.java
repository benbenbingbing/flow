package com.workflow.listener;

import com.workflow.service.ProcessCcRuntimeService;
import com.workflow.service.cc.CcRuntimeContext;
import com.workflow.service.cc.ProcessCcConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.event.impl.FlowableEntityEventImpl;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessCcEventListener implements FlowableEventListener {
    private final ProcessCcRuntimeService ccRuntimeService;
    private final ProcessCcConfigService configService;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final RepositoryService repositoryService;

    @Override
    public void onEvent(FlowableEvent event) {
        if (!(event instanceof FlowableEntityEventImpl entityEvent)) {
            return;
        }
        String eventType = event.getType() == null ? "" : event.getType().name();
        try {
            if (entityEvent.getEntity() instanceof Task task) {
                String timing = switch (eventType) {
                    case "TASK_CREATED" -> "TASK_CREATE";
                    case "TASK_COMPLETED" -> "TASK_COMPLETE";
                    default -> null;
                };
                if (timing != null) {
                    triggerTask(task, timing);
                }
            } else if (entityEvent.getEntity() instanceof ProcessInstance processInstance) {
                String timing = switch (eventType) {
                    case "PROCESS_STARTED" -> "PROCESS_START";
                    case "PROCESS_COMPLETED" -> "PROCESS_COMPLETE";
                    default -> null;
                };
                if (timing != null) {
                    triggerProcess(processInstance, timing);
                }
            }
        } catch (Exception exception) {
            log.error("自动知会生成失败: eventType={}, message={}", eventType, exception.getMessage(), exception);
        }
    }

    private void triggerTask(Task task, String timing) {
        String config = configService.findConfig(task.getProcessDefinitionId(), task.getTaskDefinitionKey());
        if (config == null) {
            return;
        }
        ProcessInfo process = processInfo(task.getProcessInstanceId(), task.getProcessDefinitionId());
        ccRuntimeService.trigger(new CcRuntimeContext(
                task.getProcessInstanceId(),
                task.getProcessDefinitionId(),
                process.key(),
                process.name(),
                process.businessKey(),
                task.getTaskDefinitionKey(),
                task.getName(),
                timing,
                task.getAssignee(),
                variables(task.getProcessInstanceId())), config);
    }

    private void triggerProcess(ProcessInstance processInstance, String timing) {
        String config = configService.findConfig(processInstance.getProcessDefinitionId(), null);
        if (config == null) {
            return;
        }
        ProcessInfo process = processInfo(processInstance.getId(), processInstance.getProcessDefinitionId());
        ccRuntimeService.trigger(new CcRuntimeContext(
                processInstance.getId(),
                processInstance.getProcessDefinitionId(),
                process.key(),
                process.name(),
                process.businessKey(),
                null,
                process.name(),
                timing,
                process.startUserId(),
                variables(processInstance.getId())), config);
    }

    private Map<String, Object> variables(String processInstanceId) {
        try {
            return runtimeService.getVariables(processInstanceId);
        } catch (Exception ignored) {
            Map<String, Object> values = new HashMap<>();
            historyService.createHistoricVariableInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .list()
                    .forEach(variable -> values.put(variable.getVariableName(), variable.getValue()));
            return values;
        }
    }

    private ProcessInfo processInfo(String processInstanceId, String processDefinitionId) {
        HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId)
                .singleResult();
        String key = definition == null ? null : definition.getKey();
        String name = definition == null ? null : definition.getName();
        String businessKey = historic == null ? null : historic.getBusinessKey();
        String startUserId = historic == null ? null : historic.getStartUserId();
        return new ProcessInfo(key, name, businessKey, startUserId);
    }

    private record ProcessInfo(String key, String name, String businessKey, String startUserId) {
    }

    @Override
    public boolean isFailOnException() {
        return false;
    }

    @Override
    public boolean isFireOnTransactionLifecycleEvent() {
        return true;
    }

    @Override
    public String getOnTransaction() {
        return "COMMITTED";
    }
}
