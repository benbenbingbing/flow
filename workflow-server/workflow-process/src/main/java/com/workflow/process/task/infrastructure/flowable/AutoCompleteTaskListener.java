package com.workflow.process.task.infrastructure.flowable;

import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;

/**
 * 历史 BPMN 任务监听器的二进制兼容壳。
 *
 * <p>早期已部署模型可能以 class FQCN 引用本类；直接删除会使在途
 * 实例到达节点时因 {@link ClassNotFoundException} 中断。自动跳过现已由
 * Flowable 原生 {@code skipExpression} 在任务创建前处理，因此此兼容类必须
 * 保持纯 no-op，不完成任务，也不写入 {@code approved}、{@code skipReason_*}
 * 或其他流程变量。</p>
 *
 * @deprecated 仅用于读取已部署历史模型；新模型不得再引用。
 */
@Deprecated(forRemoval = false)
public class AutoCompleteTaskListener implements TaskListener {

    /**
     * 保留 Flowable 反射调用入口，不执行任何任务或变量操作。
     *
     * @param delegateTask 历史模型创建的任务；本方法不读取、不修改
     */
    @Override
    public void notify(DelegateTask delegateTask) {
        // 故意留空：class FQCN 仅用于历史部署的加载兼容。
    }
}
