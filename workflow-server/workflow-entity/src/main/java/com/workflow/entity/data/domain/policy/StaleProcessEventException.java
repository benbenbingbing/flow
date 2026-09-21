package com.workflow.entity.data.domain.policy;

/** 旧实例事件不可作用于实体当前的新实例；消费方可安全忽略该事件。 */
public class StaleProcessEventException extends RuntimeException {
    public StaleProcessEventException(String instanceId) {
        super("实体当前关联流程已改变，跳过旧实例事件: " + instanceId);
    }
}
