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

/**
 * 抄送事件监听器
 * 监听 Flowable 任务创建/完成与流程启动/完成事件，按节点配置触发自动抄送。
 * <p>
 * 在事务提交后触发，避免流程尚未落库时生成抄送记录。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessCcEventListener implements FlowableEventListener {
    /** 抄送运行时服务，执行抄送触发 */
    private final ProcessCcRuntimeService ccRuntimeService;
    /** 抄送配置服务，查询节点抄送配置 */
    private final ProcessCcConfigService configService;
    /** Flowable 运行时服务，读取流程变量 */
    private final RuntimeService runtimeService;
    /** Flowable 历史服务，读取历史变量与实例 */
    private final HistoryService historyService;
    /** Flowable 仓库服务，查询流程定义 */
    private final RepositoryService repositoryService;

    /**
     * 处理 Flowable 事件，按事件类型与实体类型分发到任务或流程抄送触发。
     *
     * @param event Flowable 事件
     */
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

    /**
     * 触发任务级抄送：查询节点抄送配置，存在则组装上下文调用抄送运行时。
     *
     * @param task    Flowable 任务
     * @param timing  抄送时机（TASK_CREATE/TASK_COMPLETE）
     */
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

    /**
     * 触发流程级抄送：查询流程级抄送配置，存在则组装上下文调用抄送运行时。
     *
     * @param processInstance 流程实例
     * @param timing          抄送时机（PROCESS_START/PROCESS_COMPLETE）
     */
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

    /**
     * 获取流程变量集合，运行时实例不可用时回退到历史变量查询。
     *
     * @param processInstanceId 流程实例ID
     * @return 流程变量映射
     */
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

    /**
     * 查询流程实例与流程定义信息，组装为 {@link ProcessInfo}。
     *
     * @param processInstanceId    流程实例ID
     * @param processDefinitionId  Flowable 流程定义ID
     * @return 流程信息记录（含Key、名称、业务Key、发起人）
     */
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

    /** 流程关键信息快照，用于抄送上下文组装 */
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
