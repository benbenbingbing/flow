package com.workflow.process.cc.infrastructure.flowable;

import com.workflow.process.cc.application.ProcessCcRuntimeService;
import com.workflow.process.cc.application.CcRuntimeContext;
import com.workflow.process.cc.application.ProcessCcConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
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
        // TASK_CREATED 来自 task-service，流程/完成事件来自 BPMN 引擎；两者的
        // 实现类不同，必须按公共事件接口识别，否则创建时的知会会被静默跳过。
        if (!(event instanceof FlowableEntityEvent entityEvent)) {
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
        // PROCESS_STARTED 的实体可能是启动节点的子执行；其 id 是执行 ID，
        // 只有 processInstanceId 才能关联首页流程详情、业务编码和历史变量。
        String processInstanceId = processInstance.getProcessInstanceId();
        ProcessInfo process = processInfo(processInstanceId, processInstance.getProcessDefinitionId());
        ccRuntimeService.trigger(new CcRuntimeContext(
                processInstanceId,
                processInstance.getProcessDefinitionId(),
                process.key(),
                process.name(),
                process.businessKey(),
                null,
                process.name(),
                timing,
                process.startUserId(),
                variables(processInstanceId)), config);
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

    /**
     * 流程关键信息快照，用于抄送上下文组装
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param name 展示名称，供界面或日志识别
     * @param businessKey 业务键，后续用于授权校验、关联或幂等去重
     * @param startUserId 启动用户ID，后续用于处理流程{@code info}时定位或关联目标
     */
    private record ProcessInfo(String key, String name, String businessKey, String startUserId) {
    }

    /**
     * 判断是否失败异常；判断结果决定调用方的后续分支。
     *
     * @return 失败异常条件成立时为 true，否则为 false
     */
    @Override
    public boolean isFailOnException() {
        return false;
    }

    /**
     * 判断是否{@code fire}事务生命周期事件；判断结果决定调用方的后续分支。
     *
     * @return {@code fire}事务生命周期事件条件成立时为 true，否则为 false
     */
    @Override
    public boolean isFireOnTransactionLifecycleEvent() {
        return true;
    }

    /**
     * 读取事务；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的事务文本，供调用方比较或展示
     */
    @Override
    public String getOnTransaction() {
        return "COMMITTED";
    }
}
