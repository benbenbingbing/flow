package com.workflow.contracts.process.action.port;

import java.util.Map;

/**
 * 流程动作执行期间可用的运行时数据访问能力。
 *
 * <p>引擎对象以 {@link Object} 暴露，避免 contracts 模块依赖 Flowable 或实体实现类。</p>
 */
public interface FlowActionRuntimeAccess {

    Map<String, Object> getVariables(String processInstanceId);

    Object getVariable(String processInstanceId, String name);

    void setVariable(
            String processInstanceId,
            String name,
            Object value);

    void setVariables(
            String processInstanceId,
            Map<String, Object> variables);

    Object getProcessInstance(String processInstanceId);

    Object getHistoricProcessInstance(String processInstanceId);

    Object getCurrentTask(String processInstanceId);

    Object getTask(String taskId);

    Object getEntityData(String entityCode, String entityDataId);

    <T> T convertParams(Map<String, Object> params, Class<T> targetType);
}
