package com.workflow.process.task.application.nextapproval;

import com.workflow.process.task.application.nextapproval.model.NextApproverOverride;

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

    /**
     * TASK_CREATED 消费入口；多实例节点由集合监听器消费。
     *
     * @param task 任务，作为 {@code consume} 的输入影响后续处理
     * @return 处理后的消费任务结果，供调用方继续处理
     */
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

    /**
     * 多实例集合准备入口。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param nodeId 节点ID，后续用于处理消费多实例时定位或关联目标
     * @return 下一步审批人覆盖{@code store}集合，供调用方遍历或展示
     */
    public List<String> consumeForMultiInstance(
            String processInstanceId,
            String nodeId) {
        NextApproverOverride override = consume(
                processInstanceId, nodeId);
        return override == null
                ? List.of()
                : override.usernames();
    }

    /**
     * 判断指定节点是否存在尚未消费的人工覆盖。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param nodeId 节点ID，后续用于判断是否具有{@code staged}覆盖时定位或关联目标
     * @return {@code staged}覆盖条件成立时为 true，否则为 false
     */
    public boolean hasStagedOverride(
            String processInstanceId,
            String nodeId) {
        Object raw = runtimeService.getVariable(
                processInstanceId, VARIABLE_NAME);
        return raw instanceof Map<?, ?> overrides
                && overrides.containsKey(nodeId);
    }

    /**
     * 处理消费，并将结果传给后续步骤。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param nodeId 节点ID，后续用于处理消费时定位或关联目标
     * @return 处理后的消费结果，供调用方继续处理
     */
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

    /**
     * 判断是否多实例；判断结果决定调用方的后续分支。
     *
     * @param processDefinitionId 流程定义 ID，用于读取对应的已发布流程配置
     * @param nodeId 节点ID，后续用于判断是否多实例时定位或关联目标
     * @return 多实例条件成立时为 true，否则为 false
     */
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

    /**
     * 整理当前{@code overrides}数据，供调用方遍历或继续处理。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @return 当前{@code overrides}键值结果，供调用方继续处理
     */
    private Map<String, Object> currentOverrides(
            String processInstanceId) {
        Object raw = runtimeService.getVariable(
                processInstanceId, VARIABLE_NAME);
        return raw instanceof Map<?, ?> map
                ? mapValue(map)
                : new LinkedHashMap<>();
    }

    /**
     * 整理规范化键集合数据，供调用方遍历或继续处理。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 下一步审批人覆盖{@code store}集合，供调用方遍历或展示
     */
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

    /**
     * 整理集合值数据，供调用方遍历或继续处理。
     *
     * @param value 待处理集合值的原始输入，结果供调用方继续使用
     * @return {@code collection<?>}集合，供调用方遍历或展示
     */
    private Collection<?> collectionValue(Object value) {
        return value instanceof Collection<?> collection
                ? collection : List.of();
    }

    /**
     * 将动态值转换为键值映射，供后续字段读取和校验。
     *
     * @param value 待处理映射值的原始输入，结果供调用方继续使用
     * @return 映射值键值结果，供调用方继续处理
     */
    private Map<String, Object> mapValue(Map<?, ?> value) {
        Map<String, Object> result = new LinkedHashMap<>();
        value.forEach((key, item) ->
                result.put(String.valueOf(key), item));
        return result;
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
