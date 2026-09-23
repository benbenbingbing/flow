package com.workflow.process.task.application.operation;

import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 转办、加签和终止三个操作的服务端权威能力入口。
 *
 * <p>新三开关优先；未配置新开关时继续叠加旧矩阵判定，确保已部署流程不会扩大权限。</p>
 */
@Service
@RequiredArgsConstructor
public class NodeOperationCapabilityService {

    private static final List<AddSignOperation> ADD_SIGN_OPERATIONS = List.of(
            new AddSignOperation("BEFORE", NodeOperationPolicy.Operation.ADD_SIGN_BEFORE),
            new AddSignOperation("PARALLEL", NodeOperationPolicy.Operation.ADD_SIGN_PARALLEL),
            new AddSignOperation("AFTER", NodeOperationPolicy.Operation.ADD_SIGN_AFTER));

    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final RepositoryService repositoryService;
    private final NodeOperationConfigReader configReader;
    private final NodeOperationDecisionService legacyDecisionService;

    /**
     * 返回当前用户在任务上的三个有效能力。
     *
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @return 处理后的可用能力集合结果，供调用方继续处理
     */
    public OperationCapabilities availableCapabilities(String taskId) {
        Task task = requireTask(taskId);
        NodeOperationConfig config = config(task);
        Map<String, NodeOperationDecisionService.ActionDecision> legacy =
                legacyDecisionService.availableActions(taskId);

        boolean transfer = config.allowTransfer()
                && legacy.get(NodeOperationPolicy.Operation.TRANSFER.apiCode()).allowed();
        LinkedHashSet<String> addSignTypes = new LinkedHashSet<>();
        if (config.allowAddSign()) {
            for (AddSignOperation candidate : ADD_SIGN_OPERATIONS) {
                NodeOperationDecisionService.ActionDecision decision =
                        legacy.get(candidate.operation().apiCode());
                if (legacyAllowsAddSignType(decision, candidate.type())) {
                    addSignTypes.add(candidate.type());
                }
            }
        }
        String currentUserId = UserContext.getUserId();
        if (!StringUtils.hasText(currentUserId)) {
            currentUserId = UserContext.getUsername();
        }
        boolean terminate = StringUtils.hasText(task.getProcessInstanceId())
                && canTerminateProcess(task.getProcessInstanceId(), currentUserId);
        return new OperationCapabilities(
                transfer,
                !addSignTypes.isEmpty(),
                terminate,
                Collections.unmodifiableSet(addSignTypes));
    }

    /**
     * 在产生任务副作用前强制校验转办或任一加签类型。
     *
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @param operation 操作标识，决定后续允许采用的处理分支
     * @param context 执行上下文，向后续允许步骤传递身份、配置或状态
     */
    public void requireAllowed(
            String taskId,
            NodeOperationPolicy.Operation operation,
            NodeOperationDecisionService.CheckContext context) {
        if (!isTaskOperation(operation)) {
            throw new IllegalArgumentException("仅支持校验转办和加签操作");
        }
        Task task = requireTask(taskId);
        requireConfiguredAllowed(task, operation);
        // 新字段存在时旧解析器会返回兼容放行；仅旧矩阵时此处保持原条件、角色和目标约束。
        legacyDecisionService.requireAllowed(taskId, operation, context);
    }

    /**
     * 仅校验当前部署节点的新三开关。
     *
     * <p>SLA 等系统动作不具备交互用户语义，但仍不得绕过节点的转办/加签总开关。
     * 存量部署未配置新字段时按允许处理，不将旧矩阵的角色和条件强加给系统动作。</p>
     *
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @param operation 操作标识，决定后续已配置允许采用的处理分支
     */
    public void requireConfiguredAllowed(
            String taskId,
            NodeOperationPolicy.Operation operation) {
        if (!isTaskOperation(operation)) {
            throw new IllegalArgumentException("仅支持校验转办和加签操作");
        }
        requireConfiguredAllowed(requireTask(taskId), operation);
    }

    /**
     * 终止采用所有活动用户任务均允许的聚合规则；任一分支关闭即拒绝整个流程。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param context 执行上下文，向后续终止允许步骤传递身份、配置或状态
     */
    public void requireTerminateAllowed(
            String processInstanceId,
            NodeOperationDecisionService.CheckContext context) {
        List<Task> tasks = activeTasks(processInstanceId);
        if (tasks.isEmpty()) {
            throw new ForbiddenException("当前流程没有可校验的活动任务");
        }
        requireConfiguredTerminateAllowed(tasks);
        // 旧矩阵同样按所有活动分支 AND 聚合；新字段存在时解析器自动屏蔽旧矩阵。
        legacyDecisionService.requireAllowedForProcess(
                processInstanceId,
                NodeOperationPolicy.Operation.TERMINATE,
                context);
    }

    /**
     * 仅校验流程当前所有活动用户节点的“允许终止”开关。
     *
     * <p>用于 Open API 取消、撤回等非标准终止入口的硬门禁。并行分支按 AND
     * 聚合；没有活动用户任务时不存在可应用的节点开关，保持该入口原有行为。</p>
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     */
    public void requireConfiguredTerminateAllowed(String processInstanceId) {
        requireConfiguredTerminateAllowed(activeTasks(processInstanceId));
    }

    /**
     * 查询发起人是否可终止运行中流程。身份校验与节点能力都满足才返回 true。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @return 终止流程条件成立时为 true，否则为 false
     */
    public boolean canTerminateProcess(String processInstanceId, String userId) {
        if (!isRuntimeStarter(processInstanceId, userId)) return false;
        try {
            requireTerminateAllowed(
                    processInstanceId,
                    NodeOperationDecisionService.CheckContext.availability());
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * 发起人撤回的只读能力查询。使用撤回矩阵（不是终止矩阵），并行分支全部通过才开放。
     * 理由等提交参数由写接口再次校验；此处只评估权限、状态、时限和节点开关。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @return {@code withdraw}流程条件成立时为 true，否则为 false
     */
    public boolean canWithdrawProcess(String processInstanceId, String userId) {
        if (!isRuntimeStarter(processInstanceId, userId)) return false;
        try {
            List<Task> tasks = activeTasks(processInstanceId);
            if (tasks.isEmpty()) return false;
            requireConfiguredTerminateAllowed(tasks);
            return tasks.stream().allMatch(task -> {
                NodeOperationDecisionService.ActionDecision decision =
                        legacyDecisionService.availableActions(task.getId()).get("withdraw");
                return decision != null && decision.allowed();
            });
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * 与撤回写接口一致，运行时缺失发起人时查询历史，无法确认身份则拒绝。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @return 运行时{@code starter}条件成立时为 true，否则为 false
     */
    private boolean isRuntimeStarter(String processInstanceId, String userId) {
        if (!StringUtils.hasText(processInstanceId) || !StringUtils.hasText(userId)) {
            return false;
        }
        ProcessInstance runtime = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (runtime == null) {
            return false;
        }
        String startUserId = runtime.getStartUserId();
        if (!StringUtils.hasText(startUserId)) {
            HistoricProcessInstance historic = historyService
                    .createHistoricProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            startUserId = historic == null ? null : historic.getStartUserId();
        }
        if (!userId.equals(startUserId)) {
            return false;
        }
        return true;
    }

    /**
     * 整理活动任务集合数据，供调用方遍历或继续处理。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @return 任务集合，供调用方遍历或展示
     */
    private List<Task> activeTasks(String processInstanceId) {
        List<Task> tasks = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .active()
                .list();
        return tasks == null ? List.of() : tasks;
    }

    /**
     * 校验并获取任务；不满足约束时阻止后续处理。
     *
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @return 校验并获取后的任务结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private Task requireTask(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }
        return task;
    }

    /**
     * 处理配置，并将结果传给后续步骤。
     *
     * @param task 任务，作为 {@code repositoryService.getBpmnModel} 的输入影响后续处理
     * @return 处理后的配置结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private NodeOperationConfig config(Task task) {
        BpmnModel model = repositoryService.getBpmnModel(task.getProcessDefinitionId());
        FlowElement element = findElement(model, task.getTaskDefinitionKey());
        if (!(element instanceof UserTask userTask)) {
            throw new IllegalArgumentException(
                    "任务节点不存在于绑定的流程版本: " + task.getTaskDefinitionKey());
        }
        return configReader.read(userTask).orElseGet(NodeOperationConfig::allowAll);
    }

    /**
     * 校验并获取已配置允许；不满足约束时阻止后续处理。
     *
     * @param task 任务，供本方法校验并获取已配置允许时使用
     * @param operation 操作标识，决定后续已配置允许采用的处理分支
     * @throws ForbiddenException 当前用户缺少所需访问权限时抛出
     */
    private void requireConfiguredAllowed(
            Task task,
            NodeOperationPolicy.Operation operation) {
        if (!config(task).allows(operation)) {
            throw new ForbiddenException(deniedMessage(operation));
        }
    }

    /**
     * 校验并获取已配置终止允许；不满足约束时阻止后续处理。
     *
     * @param tasks 任务集合，供本方法校验并获取已配置终止允许时使用
     * @throws ForbiddenException 当前用户缺少所需访问权限时抛出
     */
    private void requireConfiguredTerminateAllowed(List<Task> tasks) {
        for (Task task : tasks) {
            if (!config(task).allowTerminate()) {
                throw new ForbiddenException("当前节点不允许终止流程");
            }
        }
    }

    /**
     * 查询元素；查询结果供调用方展示或继续处理。
     *
     * @param model 模型，供本方法查询元素时使用
     * @param elementId 元素ID，后续用于查询元素时定位或关联目标
     * @return 符合条件的流程元素结果，供调用方继续处理
     */
    private FlowElement findElement(BpmnModel model, String elementId) {
        if (model == null || !StringUtils.hasText(elementId)) {
            return null;
        }
        for (org.flowable.bpmn.model.Process process : model.getProcesses()) {
            FlowElement element = process.getFlowElement(elementId, true);
            if (element != null) {
                return element;
            }
        }
        return null;
    }

    /**
     * 判断旧版{@code allows}添加签名类型条件是否成立，供调用方选择后续分支。
     *
     * @param decision 决策，供本方法处理旧版{@code allows}添加签名类型时使用
     * @param type 类型标识，决定后续旧版{@code allows}添加签名类型采用的处理分支
     * @return 旧版{@code allows}添加签名类型条件成立时为 true，否则为 false
     */
    private boolean legacyAllowsAddSignType(
            NodeOperationDecisionService.ActionDecision decision,
            String type) {
        if (decision == null || !decision.allowed()) {
            return false;
        }
        Set<String> restricted = decision.rule().allowedAddSignTypes();
        return restricted.isEmpty() || restricted.contains(type);
    }

    /**
     * 判断是否任务操作；判断结果决定调用方的后续分支。
     *
     * @param operation 操作标识，决定后续任务操作采用的处理分支
     * @return 任务操作条件成立时为 true，否则为 false
     */
    private boolean isTaskOperation(NodeOperationPolicy.Operation operation) {
        return operation == NodeOperationPolicy.Operation.TRANSFER
                || (operation != null && operation.isAddSign());
    }

    /**
     * 生成已拒绝消息文本，供后续匹配或展示。
     *
     * @param operation 操作标识，决定后续已拒绝消息采用的处理分支
     * @return 处理后的已拒绝消息文本，供调用方比较或展示
     */
    private String deniedMessage(NodeOperationPolicy.Operation operation) {
        return operation != null && operation.isAddSign()
                ? "当前节点不允许加签"
                : "当前节点不允许转办";
    }

    /**
     * 三个开关在当前用户、流程状态及旧策略叠加后的有效结果。
     *
     * @param transfer 转办，保存在对象中供后续校验、查询或展示
     * @param addSign 添加签名，保存在对象中供后续校验、查询或展示
     * @param terminate 终止，保存在对象中供后续校验、查询或展示
     * @param allowedAddSignTypes 允许添加签名类型集合，保存在对象中供后续校验、查询或展示
     */
    public record OperationCapabilities(
            boolean transfer,
            boolean addSign,
            boolean terminate,
            Set<String> allowedAddSignTypes) {
    }

    /**
     * 封装添加签名操作的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param type 类型标识，决定后续添加签名操作采用的处理分支
     * @param operation 操作标识，决定后续添加签名操作采用的处理分支
     */
    private record AddSignOperation(
            String type,
            NodeOperationPolicy.Operation operation) {
    }
}
