package com.workflow.contracts.entity.mutation;

/**
 * 平台内部实体运行态写入标识。
 */
public final class EntityMutationSystemFields {

    public static final String MODE_KEY =
            "_entityMutationMode";
    public static final String PROCESS_END =
            "PROCESS_END";
    public static final String CURRENT_TASK =
            "CURRENT_TASK";

    private EntityMutationSystemFields() {
    }
}
