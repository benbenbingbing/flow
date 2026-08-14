package com.workflow.process.task.application.nextapproval;

import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.Activity;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 下一审批人一次性覆盖的轻量运行时存储。
 *
 * <p>该组件只读取和消费 Flowable 变量，不做路径预览、候选人校验或实体写入。
 * Flowable 监听器依赖此叶子组件，可以避免把完整审批编排服务带入流程启动链路。</p>
 */
@Service
@RequiredArgsConstructor
public class NextApproverOverrideStore {

    public static final String VARIABLE_NAME =
            "_wfNextApproverOverrides_";

    private final RuntimeService runtimeService;
    private final RepositoryService repositoryService;

    /** TASK_CREATED 消费入口；多实例节点由集合监听器消费。 */
    public NextApproverOverride consumeForTask(Task task) {
        if (task == null || isMultiInstance(
                task.getProcessDefinitionId(),
                task.getTaskDefinitionKey())) {
            return null;
        }
        return consume(
                task.getProcessInstanceId(),
                task.getTaskDefinitionKey());
    }

    /** 多实例集合准备入口。 */
    public List<String> consumeForMultiInstance(
            String processInstanceId,
            String nodeId) {
        NextApproverOverride override = consume(
                processInstanceId, nodeId);
        return override == null
                ? List.of()
                : override.usernames();
    }

    /** 判断指定节点是否存在尚未消费的人工覆盖。 */
    public boolean hasStagedOverride(
            String processInstanceId,
            String nodeId) {
        Object raw = runtimeService.getVariable(
                processInstanceId, VARIABLE_NAME);
        return raw instanceof Map<?, ?> overrides
                && overrides.containsKey(nodeId);
    }

    private synchronized NextApproverOverride consume(
            String processInstanceId,
            String nodeId) {
        Map<String, Object> overrides = currentOverrides(
                processInstanceId);
        Object rawEntry = overrides.remove(nodeId);
        if (!(rawEntry instanceof Map<?, ?> rawMap)) {
            return null;
        }
        if (overrides.isEmpty()) {
            runtimeService.removeVariable(
                    processInstanceId, VARIABLE_NAME);
        } else {
            runtimeService.setVariable(
                    processInstanceId, VARIABLE_NAME, overrides);
        }
        Map<String, Object> entry = mapValue(rawMap);
        return new NextApproverOverride(
                text(entry.get("sourceTaskId")),
                nodeId,
                text(entry.get("assignmentMode")),
                normalizedKeys(collectionValue(entry.get("usernames"))));
    }

    private boolean isMultiInstance(
            String processDefinitionId,
            String nodeId) {
        BpmnModel model = repositoryService.getBpmnModel(
                processDefinitionId);
        FlowElement element = model == null || model.getMainProcess() == null
                ? null
                : model.getMainProcess().getFlowElement(nodeId, true);
        return element instanceof Activity activity
                && activity.hasMultiInstanceLoopCharacteristics();
    }

    private Map<String, Object> currentOverrides(
            String processInstanceId) {
        Object raw = runtimeService.getVariable(
                processInstanceId, VARIABLE_NAME);
        return raw instanceof Map<?, ?> map
                ? mapValue(map)
                : new LinkedHashMap<>();
    }

    private List<String> normalizedKeys(Collection<?> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::valueOf)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private Collection<?> collectionValue(Object value) {
        return value instanceof Collection<?> collection
                ? collection : List.of();
    }

    private Map<String, Object> mapValue(Map<?, ?> value) {
        Map<String, Object> result = new LinkedHashMap<>();
        value.forEach((key, item) ->
                result.put(String.valueOf(key), item));
        return result;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
