package com.workflow.entity.data.domain.policy;

/** 旧实例事件不可作用于实体当前的新实例；消费方可安全忽略该事件。 */
public class StaleProcessEventException extends RuntimeException {
    /**
     * 初始化{@code stale}流程事件异常，保存构造参数供后续方法使用。
     *
     * @param instanceId 实例ID，后续用于初始化{@code stale}流程事件异常时定位或关联目标
     */
    public StaleProcessEventException(String instanceId) {
        super("实体当前关联流程已改变，跳过旧实例事件: " + instanceId);
    }
}
