package com.workflow.entity.data.application;

/**
 * 实体运行态写入使用的内部标识。
 *
 * <p>这些字段只协调实体聚合写入与其适配器，不能作为跨模块 mutation 协议的一部分暴露。</p>
 */
public final class EntityMutationSystemFields {

    public static final String MODE_KEY = "_entityMutationMode";
    public static final String PROCESS_END = "PROCESS_END";
    public static final String CURRENT_TASK = "CURRENT_TASK";

    private EntityMutationSystemFields() {
    }
}
