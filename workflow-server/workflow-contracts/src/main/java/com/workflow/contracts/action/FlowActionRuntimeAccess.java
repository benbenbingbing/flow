package com.workflow.contracts.action;

import java.util.Map;

/**
 * Runtime data access available to custom flow actions.
 *
 * <p>The contract intentionally exposes engine-owned objects as {@link Object}. Extension
 * implementations can inspect them when needed without making the contracts module depend on
 * Flowable or entity implementation classes.</p>
 */
public interface FlowActionRuntimeAccess {

    Map<String, Object> getVariables(String processInstanceId);

    Object getVariable(String processInstanceId, String name);

    Object getProcessInstance(String processInstanceId);

    Object getHistoricProcessInstance(String processInstanceId);

    Object getCurrentTask(String processInstanceId);

    Object getTask(String taskId);

    Object getEntityData(String entityCode, String entityDataId);

    <T> T convertParams(Map<String, Object> params, Class<T> targetType);
}
