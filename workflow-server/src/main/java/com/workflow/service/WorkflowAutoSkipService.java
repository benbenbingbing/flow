package com.workflow.service;

import com.workflow.entity.NodeConfig;
import com.workflow.mapper.NodeConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;

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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowAutoSkipService {

    private final NodeConfigMapper nodeConfigMapper;
    private final TaskService taskService;
    private final RuntimeService runtimeService;

    /**
     * 自动完成指定流程实例中配置为跳过的节点任务。
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
        int maxIterations = 10;
        for (int i = 0; i < maxIterations; i++) {
            List<Task> activeTasks = taskService.createTaskQuery()
                    .processInstanceId(processInstanceId)
                    .active()
                    .list();

            List<Task> skipTasks = activeTasks.stream()
                    .filter(t -> skipNodeIds.contains(t.getTaskDefinitionKey()))
                    .filter(t -> !completedTaskIds.contains(t.getId()))
                    .toList();

            if (skipTasks.isEmpty()) {
                break;
            }

            for (Task task : skipTasks) {
                try {
                    taskService.addComment(task.getId(), processInstanceId, "系统自动跳过此节点");
                    taskService.setVariable(task.getId(), "approved", "approve");
                    taskService.complete(task.getId(), Map.of("approved", "approve"));
                    completedTaskIds.add(task.getId());
                    log.info("自动跳过节点: processInstanceId={}, taskId={}, taskDefKey={}, taskName={}",
                            processInstanceId, task.getId(), task.getTaskDefinitionKey(), task.getName());
                } catch (Exception e) {
                    log.error("自动跳过节点失败: processInstanceId={}, taskId={}", processInstanceId, task.getId(), e);
                }
            }
        }
    }
}
