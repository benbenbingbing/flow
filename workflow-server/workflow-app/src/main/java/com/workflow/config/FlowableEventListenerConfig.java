package com.workflow.config;

import com.workflow.listener.EntityStatusUpdateListener;
import com.workflow.listener.MultiInstanceCollectionListener;
import com.workflow.listener.ProcessEndListener;
import com.workflow.listener.ProcessCcEventListener;
import com.workflow.process.action.FlowActionEngineEventListener;
import com.workflow.service.WorkflowAutoSkipService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Flowable 事件监听器配置
 */
@Configuration
@RequiredArgsConstructor
public class FlowableEventListenerConfig {

    private final RuntimeService runtimeService;
    private final EntityStatusUpdateListener entityStatusUpdateListener;
    private final ProcessEndListener processEndListener;
    private final MultiInstanceCollectionListener multiInstanceCollectionListener;
    private final WorkflowAutoSkipService workflowAutoSkipService;
    private final FlowActionEngineEventListener flowActionEngineEventListener;
    private final ProcessCcEventListener processCcEventListener;

    @PostConstruct
    public void init() {
        // 注册任务完成事件监听器（不指定事件类型，监听所有事件，在监听器中过滤）
        runtimeService.addEventListener(entityStatusUpdateListener);

        // 注册流程结束事件监听器（监听所有事件，在监听器中过滤）
        runtimeService.addEventListener(processEndListener);

        // 注册多实例集合变量自动准备监听器
        runtimeService.addEventListener(multiInstanceCollectionListener);

        // 注册自动跳过节点监听器：流程运行中途到达配置为跳过的用户任务节点时实时自动完成，
        // 弥补原先仅在流程启动时一次性跳过的不足（解决中途到达的跳过节点不生效问题）。
        runtimeService.addEventListener(workflowAutoSkipService);

        // 统一流程动作事件监听器：流程、节点、任务、连线均从这里分发。
        runtimeService.addEventListener(flowActionEngineEventListener);

        // 自动知会：流程/任务时机统一解析 ccConfig 并写入知会收件箱与Outbox。
        runtimeService.addEventListener(processCcEventListener);
    }
}
