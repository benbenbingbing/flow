package com.workflow.process.task.application;

import com.workflow.contracts.process.error.ProcessOperationException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/** 引擎同步回调中的同任务防重入；不同请求键也不能递归办理正在推进的任务。 */
final class TaskCommandScope {
    private static final ThreadLocal<Set<String>> ACTIVE = new ThreadLocal<>();
    private TaskCommandScope() { }

    /** 退出即清理线程状态；并发线程仍由数据库事务及 Flowable 乐观锁协调。 */
    static <T> T run(String taskId, Supplier<T> work) {
        Set<String> tasks = ACTIVE.get();
        if (tasks == null) { tasks = new HashSet<>(); ACTIVE.set(tasks); }
        if (!tasks.add(taskId)) throw new ProcessOperationException(
                "COMMAND_REENTRANT", "同一任务不能在流程回调中递归办理", taskId);
        try { return work.get(); }
        finally { tasks.remove(taskId); if (tasks.isEmpty()) ACTIVE.remove(); }
    }
}
