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
    /** 实体状态更新监听器：监听任务完成事件以同步实体流程状态 */
    private final EntityStatusUpdateListener entityStatusUpdateListener;
    /** 流程结束监听器：监听流程结束事件以执行收尾逻辑 */
    private final ProcessEndListener processEndListener;
    /** 多实例集合监听器：为会签/多实例任务自动准备集合变量 */
    private final MultiInstanceCollectionListener multiInstanceCollectionListener;
    /** 自动跳过服务：到达配置为跳过的用户任务节点时自动完成 */
    private final WorkflowAutoSkipService workflowAutoSkipService;
    /** 统一流程动作事件监听器：流程/节点/任务/连线事件的统一分发入口 */
    private final FlowActionEngineEventListener flowActionEngineEventListener;
    /** 自动知会监听器：解析 ccConfig 并写入知会收件箱与 Outbox */
    private final ProcessCcEventListener processCcEventListener;

    /**
     * 注册 Flowable 运行时事件监听器。
     *
     * <p>统一向 {@link RuntimeService} 注册各业务监听器，监听器内部按事件类型自行过滤，
     * 确保流程运行过程中的状态同步、自动跳过、动作分发、知会等扩展逻辑均能被触发。
     */
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
