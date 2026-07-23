package com.workflow.process.action;

/**
 * 流程动作处理器可见范围。
 *
 * <p>用于控制动作处理器在动作目录中可见的目标范围，决定是否可以在某个实体的流程中选用该处理器。</p>
 */
public enum FlowActionVisibilityScope {
    /** 全局可见：任意实体的流程均可选用该处理器 */
    GLOBAL,
    /** 实体可见：仅指定实体集合的流程可选用该处理器 */
    ENTITY
}
