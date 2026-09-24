package com.workflow.process.task.infrastructure.flowable;

import com.workflow.process.task.application.TaskInboxProjectionService;
import lombok.RequiredArgsConstructor;
import org.flowable.common.engine.api.delegate.event.FlowableEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.LinkedHashSet;
import java.util.Set;

/** 捕获任务及候选身份增删，合并到同一事务提交前，避开创建监听器早于最终分配的问题。 */
@Component
@RequiredArgsConstructor
public class TaskInboxProjectionListener implements FlowableEventListener {
    private final ObjectProvider<TaskInboxProjectionService> projection;

    @Override
    public void onEvent(FlowableEvent event) {
        if (!(event instanceof FlowableEntityEvent entityEvent) || event.getType() == null) return;
        String type = event.getType().name();
        if (!Set.of("TASK_CREATED", "TASK_ASSIGNED", "TASK_COMPLETED", "ENTITY_CREATED", "ENTITY_UPDATED", "ENTITY_DELETED").contains(type)) return;
        Object entity = entityEvent.getEntity();
        String taskId = entity instanceof Task task ? task.getId()
                : entity instanceof IdentityLink link ? link.getTaskId() : null;
        if (taskId == null || taskId.isBlank()) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("任务身份投影必须与引擎共用 Spring 事务");
        }
        Pending pending = TransactionSynchronizationManager.getSynchronizations().stream()
                .filter(Pending.class::isInstance).map(Pending.class::cast)
                .filter(value -> value.owner == this).findFirst().orElse(null);
        if (pending == null) {
            pending = new Pending(this);
            TransactionSynchronizationManager.registerSynchronization(pending);
        }
        pending.ids.add(taskId);
    }

    private final class Pending implements TransactionSynchronization {
        private final TaskInboxProjectionListener owner;
        private final Set<String> ids = new LinkedHashSet<>();
        private Pending(TaskInboxProjectionListener owner) { this.owner = owner; }

        /** 这里仍处于原事务；同步失败向外抛出，禁止在 afterCommit 中才更新权限投影。 */
        @Override public void beforeCommit(boolean readOnly) {
            // 修复缺失镜像时 SLA 初始化可能再次产生引擎事件，先取出批次再处理，避免修改遍历中的集合。
            while (!ids.isEmpty()) {
                var batch = ids.stream().sorted().toList();
                ids.clear();
                batch.forEach(id -> projection.getObject().synchronizeTask(id));
            }
        }
    }

    @Override public boolean isFailOnException() { return true; }
    @Override public boolean isFireOnTransactionLifecycleEvent() { return false; }
    @Override public String getOnTransaction() { return null; }
}
