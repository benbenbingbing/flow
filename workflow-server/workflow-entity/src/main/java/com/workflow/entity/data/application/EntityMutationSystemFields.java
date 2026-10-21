package com.workflow.entity.data.application;

import com.workflow.contracts.entity.mutation.model.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.model.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.model.EntityMutationSourceType;
import java.util.Set;

/**
 * 实体运行态写入使用的内部标识。
 *
 * <p>这些字段只协调实体聚合写入与其适配器，不能作为跨模块 mutation 协议的一部分暴露。</p>
 */
public final class EntityMutationSystemFields {

    public static final String MODE_KEY = "_entityMutationMode";
    public static final String PROCESS_END = "PROCESS_END";
    public static final String CURRENT_TASK = "CURRENT_TASK";
    /** 只由实体端口创建，关联引擎已经建立的子流程，不再次启动流程引擎。 */
    public static final String CALL_PROCESS_START = "CALL_PROCESS_START";

    /**
     * 仅受信流程运行态的任务摘要命令可走局部同步；禁止夹带业务字段绕过聚合校验。
     * 其他实体写入（包括流程结束和业务状态变更）继续走完整变更管道。
     *
     * @throws IllegalArgumentException 来源、操作类型或载荷不符合任务摘要协议
     */
    public static boolean isCurrentTaskSync(EntityMutationCommand command) {
        if (!CURRENT_TASK.equals(command.payload().get(MODE_KEY))) return false;
        Set<String> fields = Set.of(MODE_KEY, "currentTaskId", "currentTaskName", "currentTaskAssignee");
        if (command.context().sourceType() != EntityMutationSourceType.PROCESS_RUNTIME
                || command.operationType() != EntityMutationOperationType.STATUS_CHANGE
                || !command.payload().keySet().equals(fields)) {
            throw new IllegalArgumentException("当前任务同步只允许流程引擎更新任务摘要字段");
        }
        return true;
    }

    /**
     * 初始化实体变更系统字段，保存构造参数供后续方法使用。
     */
    private EntityMutationSystemFields() {
    }
}
