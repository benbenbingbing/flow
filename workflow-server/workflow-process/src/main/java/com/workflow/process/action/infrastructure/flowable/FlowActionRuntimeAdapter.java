package com.workflow.process.action.infrastructure.flowable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.process.action.port.FlowActionRuntimePort;
import com.workflow.contracts.entity.model.EntityRecordData;
import com.workflow.contracts.entity.port.EntityRecordQueryPort;
import com.workflow.contracts.process.model.ProcessLifecycle;
import com.workflow.contracts.process.query.model.ProcessInstanceView;
import com.workflow.contracts.process.query.model.ProcessTaskView;
import com.workflow.contracts.process.query.port.ProcessQueryPort;
import com.workflow.contracts.process.variable.model.ProcessVariablePatch;
import com.workflow.contracts.process.variable.model.VariableReadMode;
import com.workflow.contracts.process.variable.port.ProcessVariablePort;
import com.workflow.process.command.application.ProcessInvocationScope;
import com.workflow.process.variable.application.ProcessVariableReader;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 动作上下文的便利方法委托到公共流程端口。签发的动作范围由执行器绑定，
 * 不因持有此 Bean 就自动获得跨流程权限，也不泄露可直接修改的 Flowable 对象。
 */
@Component
public class FlowActionRuntimeAdapter implements FlowActionRuntimePort {
    private final ProcessQueryPort queries;
    private final ProcessVariablePort variables;
    private final ProcessVariableReader reader;
    private final EntityRecordQueryPort entityDataDynamicService;
    private final ObjectMapper objectMapper;

    /** 引擎监听器在引擎装配期间依赖此适配器，延迟取得端口以避免反向初始化流程引擎。 */
    public FlowActionRuntimeAdapter(@Lazy ProcessQueryPort queries, @Lazy ProcessVariablePort variables,
                                   @Lazy ProcessVariableReader reader, EntityRecordQueryPort entityDataDynamicService,
                                   ObjectMapper objectMapper) {
        this.queries = queries; this.variables = variables; this.reader = reader;
        this.entityDataDynamicService = entityDataDynamicService; this.objectMapper = objectMapper;
    }

    /** 空运行态仍是有效结果；仅实例结束时才允许公共变量读取回退历史。 */
    @Override
    public Map<String, Object> getVariables(String processInstanceId) {
        return variables.getProcessVariables(processInstanceId, VariableReadMode.RUNTIME_OR_HISTORY);
    }

    @Override
    public Map<String, Object> getLiveVariables(String processInstanceId) {
        return variables.getProcessVariables(processInstanceId, VariableReadMode.RUNTIME_ONLY);
    }

    @Override
    public Object getVariable(String processInstanceId, String name) {
        return variables.getProcessVariable(processInstanceId, name, VariableReadMode.RUNTIME_OR_HISTORY).value();
    }

    /** 快捷写入按本次动作内调用顺序获得稳定键，重试不能生成随机 requestId。 */
    @Override
    public void setVariable(String processInstanceId, String name, Object value) {
        setVariables(processInstanceId, Collections.singletonMap(name, value));
    }

    /** 变量边界、原子写入和幂等回执与 biz 的公共调用完全相同。 */
    @Override
    public void setVariables(String processInstanceId, Map<String, Object> values) {
        if (values == null || values.isEmpty()) return;
        variables.patchProcessVariables(new ProcessVariablePatch(ProcessInvocationScope.nextVariableCommand(processInstanceId),
                processInstanceId, values, Set.of()));
    }

    /** 保留原快捷方法只返回运行实例的语义；需要已结束实例时使用历史方法。 */
    @Override
    public ProcessInstanceView getProcessInstance(String processInstanceId) {
        return queries.findInstance(processInstanceId).filter(view -> view.lifecycle() != ProcessLifecycle.ENDED).orElse(null);
    }

    @Override
    public ProcessInstanceView getHistoricProcessInstance(String processInstanceId) {
        return queries.findInstance(processInstanceId).orElse(null);
    }

    @Override
    public List<ProcessTaskView> getActiveTasks(String processInstanceId) {
        return queries.listActiveTasks(processInstanceId);
    }

    /** 已经完成的触发任务返回 null，避免把后继任务误认为触发任务。 */
    @Override
    public ProcessTaskView getTask(String taskId) {
        return taskId == null || taskId.isBlank() ? null : queries.findActiveTask(taskId).orElse(null);
    }

    /**
     * 仅供引擎监听器构造触发快照，保留发起人、实体绑定和审批编排字段。
     * 不属于公共 Port，不能复用公共业务变量过滤后丢失事件必须的身份数据。
     */
    Map<String, Object> getTriggerVariables(String instanceId) {
        return reader.process(instanceId, VariableReadMode.RUNTIME_OR_HISTORY).values();
    }

    /**
     * 按实体编码与数据 ID 查询实体数据。
     *
     * @param entityCode   实体编码
     * @param entityDataId 实体数据 ID
     * @return 经宿主读取策略校验后的业务记录投影，不含 HTTP 表单令牌和按钮能力
     */
    @Override
    public EntityRecordData getEntityData(String entityCode, String entityDataId) {
        return entityDataDynamicService.findById(entityCode, entityDataId);
    }

    /**
     * 将业务参数 map 转换为目标类型实例。
     *
     * @param <T>         目标类型
     * @param extraParams 业务参数 map；为 null 时使用空 map
     * @param targetType  目标类型 Class
     * @return 转换后的参数实例
     */
    @Override
    public <T> T convertParams(Map<String, Object> extraParams, Class<T> targetType) {
        if (extraParams == null) {
            return objectMapper.convertValue(new java.util.HashMap<>(), targetType);
        }
        return objectMapper.convertValue(extraParams, targetType);
    }
}
