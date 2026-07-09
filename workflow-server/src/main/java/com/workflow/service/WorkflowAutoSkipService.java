package com.workflow.service;

import com.workflow.entity.NodeConfig;
import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.mapper.NodeConfigMapper;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.event.FlowableActivityEvent;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 流程节点自动跳过服务。
 *
 * <p>用于 BPMN 的 flowable:skipExpression 未生效时的运行时兜底：
 * 流程启动后，自动完成所有配置了 skip_node=true 的用户任务节点。</p>
 *
 * <p>同时实现 {@link FlowableEventListener}，监听 ACTIVITY_STARTED 事件，
 * 当流程运行中途到达配置为跳过的用户任务节点时，实时自动完成，
 * 弥补原先仅在流程启动时一次性跳过的不足。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowAutoSkipService implements FlowableEventListener {

    private final NodeConfigMapper nodeConfigMapper;
    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final ProcessDefinitionConfigMapper processDefinitionConfigMapper;

    /** 跳过节点总数硬上限，防止异常流程（如环路）导致死循环 */
    private static final int MAX_SKIP_TOTAL = 500;
    /** 实时跳过的递归深度上限：complete 会同步触发下一个 ACTIVITY_STARTED，链式跳过靠递归完成 */
    private static final ThreadLocal<Integer> SKIP_DEPTH = ThreadLocal.withInitial(() -> 0);

    /**
     * 自动完成指定流程实例中配置为跳过的节点任务（启动时一次性兜底）。
     *
     * @param processInstanceId 流程实例ID
     * @param processConfigId   流程配置ID（node_config 表）
     */
    public void autoSkipNodes(String processInstanceId, String processConfigId) {
        if (processInstanceId == null || processInstanceId.isEmpty()
                || processConfigId == null || processConfigId.isEmpty()) {
            return;
        }

        List<NodeConfig> nodes = nodeConfigMapper.findByProcessConfigId(processConfigId);
        Set<String> skipNodeIds = nodes.stream()
                .filter(n -> n.getNodeId() != null)
                .filter(n -> Boolean.TRUE.equals(n.getSkipNode()))
                .map(NodeConfig::getNodeId)
                .collect(Collectors.toSet());

        if (skipNodeIds.isEmpty()) {
            return;
        }

        Set<String> completedTaskIds = new HashSet<>();
        int totalSkipped = 0;
        // 改为 while 循环：只要还有可跳过的活跃任务就继续，直到没有为止。
        // 用 totalSkipped 硬上限防止异常流程导致死循环（替代原先固定 10 次的上限）。
        while (totalSkipped < MAX_SKIP_TOTAL) {
            List<Task> activeTasks = taskService.createTaskQuery()
                    .processInstanceId(processInstanceId)
                    .active()
                    .list();

            List<Task> skipTasks = activeTasks.stream()
                    .filter(t -> skipNodeIds.contains(t.getTaskDefinitionKey()))
                    .filter(t -> !completedTaskIds.contains(t.getId()))
                    .collect(Collectors.toList());

            if (skipTasks.isEmpty()) {
                break;
            }

            for (Task task : skipTasks) {
                try {
                    taskService.addComment(task.getId(), processInstanceId, "系统自动跳过此节点");
                    taskService.setVariable(task.getId(), "approved", "approve");
                    taskService.complete(task.getId(), Map.of("approved", "approve"));
                    completedTaskIds.add(task.getId());
                    totalSkipped++;
                    log.info("自动跳过节点: processInstanceId={}, taskId={}, taskDefKey={}, taskName={}",
                            processInstanceId, task.getId(), task.getTaskDefinitionKey(), task.getName());
                } catch (Exception e) {
                    log.error("自动跳过节点失败: processInstanceId={}, taskId={}", processInstanceId, task.getId(), e);
                }
            }
        }
    }

    // ==================== 实时跳过：监听 ACTIVITY_STARTED ====================

    @Override
    public void onEvent(FlowableEvent event) {
        if (!(event instanceof FlowableActivityEvent)) {
            return;
        }
        FlowableActivityEvent activityEvent = (FlowableActivityEvent) event;
        if (!"userTask".equals(activityEvent.getActivityType())) {
            return;
        }

        String activityId = activityEvent.getActivityId();
        String processInstanceId = activityEvent.getProcessInstanceId();
        if (activityId == null || processInstanceId == null) {
            return;
        }

        // 递归深度保护：taskService.complete 会同步触发下一个节点的 ACTIVITY_STARTED，
        // 链式跳过（连续多个跳过节点）通过递归完成，深度上限防止环路导致栈溢出。
        int depth = SKIP_DEPTH.get();
        if (depth >= MAX_SKIP_TOTAL) {
            log.warn("实时自动跳过递归深度超限({})，停止跳过: processInstanceId={}", depth, processInstanceId);
            return;
        }
        SKIP_DEPTH.set(depth + 1);
        try {
            skipIfConfigured(processInstanceId, activityId);
        } catch (Exception e) {
            log.error("实时自动跳过失败: processInstanceId={}, activityId={}", processInstanceId, activityId, e);
        } finally {
            SKIP_DEPTH.set(depth);
        }
    }

    /**
     * 判断当前到达的用户任务节点是否配置了跳过，若配置则自动完成。
     */
    private void skipIfConfigured(String processInstanceId, String activityId) {
        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (pi == null) {
            return;
        }

        String processDefinitionId = pi.getProcessDefinitionId();
        int colonIdx = processDefinitionId.indexOf(':');
        String processKey = colonIdx > 0 ? processDefinitionId.substring(0, colonIdx) : processDefinitionId;
        ProcessDefinitionConfig config = processDefinitionConfigMapper.findByProcessKey(processKey).orElse(null);
        if (config == null) {
            return;
        }

        NodeConfig nodeConfig = nodeConfigMapper.selectByNodeIdAndProcessId(activityId, config.getId());
        if (nodeConfig == null || !Boolean.TRUE.equals(nodeConfig.getSkipNode())) {
            return;
        }

        List<Task> tasks = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .taskDefinitionKey(activityId)
                .active()
                .list();
        for (Task task : tasks) {
            try {
                taskService.addComment(task.getId(), processInstanceId, "系统自动跳过此节点");
                taskService.setVariable(task.getId(), "approved", "approve");
                taskService.complete(task.getId(), Map.of("approved", "approve"));
                log.info("实时自动跳过节点: processInstanceId={}, taskId={}, taskDefKey={}",
                        processInstanceId, task.getId(), activityId);
            } catch (org.flowable.common.engine.api.FlowableException e) {
                // 任务已被启动兜底逻辑完成/删除时会抛 "already deleted"，属于正常竞态，降级为 debug
                log.debug("实时自动跳过跳过（任务可能已被处理）: processInstanceId={}, taskId={}, msg={}",
                        processInstanceId, task.getId(), e.getMessage());
            } catch (Exception e) {
                log.error("实时自动跳过节点失败: processInstanceId={}, taskId={}", processInstanceId, task.getId(), e);
            }
        }
    }

    @Override
    public boolean isFailOnException() {
        // 任何异常都不影响流程继续（跳过失败时任务停留在该节点，等同未配置跳过）
        return false;
    }

    @Override
    public String getOnTransaction() {
        return null;
    }

    @Override
    public boolean isFireOnTransactionLifecycleEvent() {
        return false;
    }
}
