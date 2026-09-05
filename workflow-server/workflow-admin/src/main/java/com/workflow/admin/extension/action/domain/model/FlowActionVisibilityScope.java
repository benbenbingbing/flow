package com.workflow.admin.extension.action.domain.model;

/**
 * 流程动作处理器在管理目录中的可见范围。
 *
 * <p>该枚举只参与管理端目录校验，不属于跨模块执行契约。</p>
 */
public enum FlowActionVisibilityScope {
    /** 全局可见：任意实体的流程均可选用该处理器。 */
    GLOBAL,
    /** 实体可见：仅指定实体集合的流程可选用该处理器。 */
    ENTITY
}
