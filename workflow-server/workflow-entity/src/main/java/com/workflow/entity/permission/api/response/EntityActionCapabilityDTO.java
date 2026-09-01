package com.workflow.entity.permission.api.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个按钮针对当前数据的运行时能力。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EntityActionCapabilityDTO {
    private boolean visible;
    private boolean enabled;
    private String reason;
    /**
     * 当前认证用户执行该按钮时应使用的任务 ID，仅审批等任务动作返回。
     * 该值来自未完成待办查询，不等同于实体行上可能指向兄弟任务的 currentTaskId。
     */
    private String actionableTaskId;

    /** 保留原有三参数构造契约；非任务按钮没有可办理任务 ID。 */
    public EntityActionCapabilityDTO(boolean visible, boolean enabled, String reason) {
        this(visible, enabled, reason, null);
    }

    /**
     * 构造"允许"能力：可见且可用。
     *
     * @return 可见且可用的能力对象
     */
    public static EntityActionCapabilityDTO allowed() {
        return new EntityActionCapabilityDTO(true, true, "", null);
    }

    /**
     * 构造绑定当前用户可办理任务的允许能力。
     *
     * @param actionableTaskId 当前用户自己的未完成 Flowable taskId
     * @return 可见、可用且携带受信任务目标的能力对象
     */
    public static EntityActionCapabilityDTO allowedForTask(String actionableTaskId) {
        return new EntityActionCapabilityDTO(true, true, "", actionableTaskId);
    }

    /**
     * 构造"隐藏"能力：不可见且不可用。
     *
     * @param reason 隐藏原因（用于调试/提示）
     * @return 不可见且不可用的能力对象
     */
    public static EntityActionCapabilityDTO hidden(String reason) {
        return new EntityActionCapabilityDTO(false, false, reason, null);
    }

    /**
     * 构造"禁用"能力：可见但不可用。
     *
     * @param reason 禁用原因（用于调试/提示）
     * @return 可见但不可用的能力对象
     */
    public static EntityActionCapabilityDTO disabled(String reason) {
        return new EntityActionCapabilityDTO(true, false, reason, null);
    }
}
