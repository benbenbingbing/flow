package com.workflow.contracts.process.open.port;

import com.workflow.contracts.process.open.OpenApplicationActor;
import com.workflow.contracts.process.open.OpenMessageCorrelationCommand;
import com.workflow.contracts.process.open.OpenMessageCorrelationResult;
import com.workflow.contracts.process.open.OpenProcessCancelCommand;
import com.workflow.contracts.process.open.OpenProcessStartCommand;
import com.workflow.contracts.process.open.OpenProcessView;
import com.workflow.contracts.process.open.OpenTaskView;
import java.util.List;

/**
 * 面向外部应用的开放流程运行时端口。
 */
public interface OpenProcessRuntimePort {

    /** 启动外部绑定的流程实例。 */
    OpenProcessView start(OpenProcessStartCommand command);

    /**
     * 外部绑定持久化后释放被缓冲的流程生命周期事件。
     *
     * @param processInstanceId 流程实例 ID
     * @param actor 外部应用身份
     */
    void releaseIntegrationEvents(
            String processInstanceId,
            OpenApplicationActor actor);

    /** 按外部应用身份读取流程实例。 */
    OpenProcessView get(
            String processInstanceId,
            OpenApplicationActor actor);

    /** 取消外部绑定的流程实例。 */
    OpenProcessView cancel(OpenProcessCancelCommand command);

    /** 查询外部应用可见的活跃任务。 */
    List<OpenTaskView> listActiveTasks(
            String processInstanceId,
            int offset,
            int limit,
            OpenApplicationActor actor);

    /** 按消息关联命令推进流程实例。 */
    OpenMessageCorrelationResult correlate(
            OpenMessageCorrelationCommand command);
}
