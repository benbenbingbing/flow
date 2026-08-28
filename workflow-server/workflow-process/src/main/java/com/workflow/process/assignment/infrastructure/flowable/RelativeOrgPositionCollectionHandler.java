package com.workflow.process.assignment.infrastructure.flowable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.identity.resolver.PersonResolutionException;
import com.workflow.contracts.identity.resolver.PersonPrincipal;
import com.workflow.contracts.identity.resolver.PersonPrincipalType;
import com.workflow.process.assignment.application.AssigneeIncidentRecorder;
import com.workflow.process.assignment.application.AssigneeResolutionService;
import com.workflow.process.assignment.application.EmptyAssigneePolicyResolver;
import com.workflow.process.assignment.application.LegacyMultiInstanceAssignmentParser;
import com.workflow.process.assignment.application.NodeAssignmentReferenceResolver;
import com.workflow.process.assignment.application.NodeAssignmentReferenceResolver.ResolvedAssignment;
import com.workflow.process.assignment.relative.RelativeOrgPositionConfig;
import com.workflow.process.assignment.domain.AssigneeResolutionResult;
import com.workflow.process.assignment.domain.EmptyAssigneePolicy;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import com.workflow.process.engine.infrastructure.flowable.ConfiguredTaskPropertyReader;
import com.workflow.process.task.application.nextapproval.NextApproverOverrideStore;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.impl.delegate.FlowableCollectionHandler;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Flowable 读取多实例 collection 时动态解析相对组织职务。
 *
 * <p>仅由发布器写入的平台受控 delegateExpression 调用。该时点在引擎创建
 * 多实例执行之前；返回空人员时直接抛出结构化失败，保证不会以 0 实例
 * 静默通过审批。</p>
 */
@Component("relativeOrgPositionCollectionHandler")
public class RelativeOrgPositionCollectionHandler
        implements FlowableCollectionHandler {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Flowable 7.2 在计算实例数、为每个实例取 element 以及部分
     * leave 路径都会重复调用 collection handler。缓存必须是当轮
     * MI root execution-local：同轮稳定，root 销毁后下次循环自动重算。
     */
    static final String CYCLE_CACHE_VARIABLE =
            "_wfRelativeOrgPositionMiCycleV1";
    private static final int CYCLE_CACHE_VERSION = 1;

    private final RepositoryService repositoryService;
    private final ProcessVersionHistoryMapper processVersionMapper;
    private final ObjectMapper objectMapper;
    private final MultiInstanceAssignmentResolver assignmentResolver;
    private final NodeAssignmentReferenceResolver referenceResolver;
    private final NextApproverOverrideStore overrideStore;
    private final EmptyAssigneePolicyResolver emptyPolicyResolver;
    private final AssigneeResolutionService assigneeResolutionService;
    private final AssigneeIncidentRecorder incidentRecorder;

    public RelativeOrgPositionCollectionHandler(
            RepositoryService repositoryService,
            ProcessVersionHistoryMapper processVersionMapper,
            ObjectMapper objectMapper,
            MultiInstanceAssignmentResolver assignmentResolver,
            NodeAssignmentReferenceResolver referenceResolver,
            NextApproverOverrideStore overrideStore,
            EmptyAssigneePolicyResolver emptyPolicyResolver,
            AssigneeResolutionService assigneeResolutionService,
            AssigneeIncidentRecorder incidentRecorder) {
        this.repositoryService = repositoryService;
        this.processVersionMapper = processVersionMapper;
        this.objectMapper = objectMapper;
        this.assignmentResolver = assignmentResolver;
        this.referenceResolver = referenceResolver;
        this.overrideStore = overrideStore;
        this.emptyPolicyResolver = emptyPolicyResolver;
        this.assigneeResolutionService = assigneeResolutionService;
        this.incidentRecorder = incidentRecorder;
    }

    /**
     * 人工下一审批人覆盖优先于目录动态解析，且在当前 Flowable 事务内一次性消费。
     */
    @Override
    public Collection<?> resolveCollection(
            Object ignoredOriginalCollection,
            DelegateExecution execution) {
        try {
            return resolveRequiredCollection(
                    ignoredOriginalCollection, execution);
        } catch (PersonResolutionException exception) {
            return applyNodeEntryEmptyPolicy(execution, exception);
        }
    }

    private Collection<?> resolveRequiredCollection(
            Object ignoredOriginalCollection,
            DelegateExecution execution) {
        if (execution == null
                || !StringUtils.hasText(execution.getProcessDefinitionId())
                || !StringUtils.hasText(execution.getProcessInstanceId())) {
            throw new PersonResolutionException(
                    "ORG_SNAPSHOT_INVALID",
                    "多实例人员解析缺少流程执行上下文");
        }
        UserTask currentTask = requireCurrentTask(execution);
        DelegateExecution cycleRoot = multiInstanceRoot(execution);
        List<String> cached = readCycleCollection(
                cycleRoot, currentTask);
        if (cached != null) {
            return cached;
        }
        boolean stagedOverride = overrideStore.hasStagedOverride(
                execution.getProcessInstanceId(), currentTask.getId());
        if (stagedOverride) {
            List<String> overrideUsers = stableUsers(
                    overrideStore.consumeForMultiInstance(
                            execution.getProcessInstanceId(),
                            currentTask.getId()));
            if (overrideUsers.isEmpty()) {
                throw new PersonResolutionException(
                        "POSITION_NO_ACTIVE_HOLDER",
                        "多实例下一审批人覆盖为空");
            }
            storeResolvedCollection(
                    execution, cycleRoot, currentTask, overrideUsers);
            markNodeEntryRecovered(execution, currentTask);
            return overrideUsers;
        }

        BpmnModel model = repositoryService.getBpmnModel(
                execution.getProcessDefinitionId());
        Map<String, Object> config = deployedAssignmentConfig(currentTask);
        ResolvedAssignment effective = referenceResolver.resolve(
                model, currentTask, config);
        Map<String, Object> effectiveConfig = effective.assigneeConfig();
        requireRelativePositionResolver(effectiveConfig);
        List<String> users = stableUsers(assignmentResolver.resolve(
                publishedProcessConfigId(execution.getProcessDefinitionId()),
                currentTask.getId(),
                currentTask.getName(),
                effectiveConfig,
                execution.getVariables(),
                execution.getProcessInstanceId(),
                execution.getProcessDefinitionId(),
                requireVersionTwo(effectiveConfig)));
        if (users.isEmpty()) {
            throw new PersonResolutionException(
                    "POSITION_NO_ACTIVE_HOLDER",
                    "相对组织职务多实例没有可用参与人");
        }
        storeResolvedCollection(execution, cycleRoot, currentTask, users);
        markNodeEntryRecovered(execution, currentTask);
        return users;
    }

    /**
     * 多实例尚未创建 Task，因此在节点进入边界直接执行可用的用户/组兜底；
     * 其余策略用 REQUIRES_NEW 记录 taskId 为空的节点级 incident，然后抛出原失败
     * 回滚源任务完成，严禁 Flowable 以 0 个实例继续。
     */
    private Collection<?> applyNodeEntryEmptyPolicy(
            DelegateExecution execution,
            PersonResolutionException failure) {
        if (execution == null
                || !StringUtils.hasText(execution.getProcessDefinitionId())
                || !StringUtils.hasText(execution.getProcessInstanceId())) {
            throw failure;
        }
        UserTask task = requireCurrentTask(execution);
        BpmnModel model = repositoryService.getBpmnModel(
                execution.getProcessDefinitionId());
        Map<String, Object> config = deployedAssignmentConfig(task);
        EmptyAssigneePolicy policy = emptyPolicyResolver.resolve(
                model, config);
        List<String> fallbackUsers = switch (policy.strategy()) {
            case FALLBACK_USER -> fallbackUsers(List.of(
                    PersonPrincipal.user(policy.fallbackUser())),
                    "FALLBACK_USER_INVALID");
            case FALLBACK_GROUP -> fallbackUsers(List.of(
                    new PersonPrincipal(
                            PersonPrincipalType.GROUP,
                            policy.fallbackGroup())),
                    "FALLBACK_GROUP_INVALID");
            default -> List.of();
        };
        if (!fallbackUsers.isEmpty()) {
            storeResolvedCollection(
                    execution,
                    multiInstanceRoot(execution),
                    task,
                    fallbackUsers);
            markNodeEntryRecovered(execution, task);
            return fallbackUsers;
        }

        boolean waiting = policy.strategy()
                == EmptyAssigneePolicy.Strategy.WAIT_AND_RETRY;
        LocalDateTime nextRetryAt = waiting
                ? LocalDateTime.now().plusSeconds(
                policy.initialDelaySeconds())
                : null;
        incidentRecorder.create(new AssigneeIncidentRecorder.CreateCommand(
                publishedProcessConfigId(execution.getProcessDefinitionId()),
                execution.getProcessDefinitionId(),
                execution.getProcessInstanceId(),
                null,
                task.getId(),
                task.getName(),
                policy.strategy().name(),
                waiting ? "RETRY_SCHEDULED" : "OPEN",
                failure.reasonCode(),
                failure.getMessage(),
                RelativeOrgPositionConfig.RESOLVER_CODE,
                mapValue(config.get("extraParams")),
                policy.fallbackUser(),
                policy.fallbackGroup(),
                policy.responsibilityOwner(),
                waiting ? policy.maxRetries() : 0,
                policy.initialDelaySeconds(),
                policy.backoffMultiplier(),
                nextRetryAt,
                Map.of(
                        "scope", "MULTI_INSTANCE_NODE_ENTRY",
                        "failureDetails", failure.details())));
        throw failure;
    }

    private List<String> fallbackUsers(
            List<PersonPrincipal> principals,
            String reasonCode) {
        AssigneeResolutionResult result = assigneeResolutionService
                .resolvePrincipals(principals, reasonCode);
        return result.resolved()
                ? stableUsers(result.usernames()) : List.of();
    }

    private UserTask requireCurrentTask(DelegateExecution execution) {
        FlowElement current = execution.getCurrentFlowElement();
        if (current instanceof UserTask userTask) {
            return userTask;
        }
        BpmnModel model = repositoryService.getBpmnModel(
                execution.getProcessDefinitionId());
        String activityId = execution.getCurrentActivityId();
        FlowElement deployed = model == null || model.getMainProcess() == null
                ? null
                : model.getMainProcess().getFlowElement(activityId, true);
        if (deployed instanceof UserTask userTask) {
            return userTask;
        }
        throw new PersonResolutionException(
                "ORG_SNAPSHOT_INVALID",
                "多实例人员解析无法定位已部署 UserTask");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deployedAssignmentConfig(
            UserTask userTask) {
        try {
            Map<String, Object> primary = Map.of();
            String assigneeDocument = ConfiguredTaskPropertyReader.read(
                    userTask, "assigneeConfig");
            if (StringUtils.hasText(assigneeDocument)) {
                primary = objectMapper.readValue(
                        assigneeDocument, Map.class);
            }
            Map<String, Object> fallback = Map.of();
            String multiInstanceDocument = ConfiguredTaskPropertyReader.read(
                    userTask, "multiInstanceConfig");
            if (StringUtils.hasText(multiInstanceDocument)) {
                fallback = objectMapper.readValue(
                        multiInstanceDocument, Map.class);
            }
            return LegacyMultiInstanceAssignmentParser.mergeConfigs(
                    primary, fallback);
        } catch (Exception exception) {
            throw new PersonResolutionException(
                    "ORG_SNAPSHOT_INVALID",
                    "已部署多实例人员配置无法解析",
                    Map.of(),
                    exception);
        }
    }

    private void requireRelativePositionResolver(
            Map<String, Object> config) {
        String resolverCode = firstText(
                config.get("resolverCode"),
                config.get("interfaceName"));
        if (!RelativeOrgPositionConfig.RESOLVER_CODE.equals(resolverCode)) {
            throw new PersonResolutionException(
                    "ORG_SNAPSHOT_INVALID",
                    "相对职务 collection handler 绑定了非目标 resolver");
        }
    }

    private int requireVersionTwo(Map<String, Object> config) {
        Object value = config.get("assignmentConfigVersion");
        if (value instanceof Number number && number.intValue() == 2) {
            return 2;
        }
        throw new PersonResolutionException(
                "ORG_SNAPSHOT_INVALID",
                "relativeOrgPosition 多实例必须使用 assignmentConfigVersion=2");
    }

    private String publishedProcessConfigId(String processDefinitionId) {
        ProcessDefinition definition = repositoryService
                .createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId)
                .singleResult();
        if (definition == null
                || !StringUtils.hasText(definition.getDeploymentId())) {
            return null;
        }
        return processVersionMapper
                .findByDeploymentId(definition.getDeploymentId())
                .map(history -> history.getProcessConfigId())
                .orElse(null);
    }

    private void storeResolvedCollection(
            DelegateExecution execution,
            DelegateExecution cycleRoot,
            UserTask task,
            List<String> users) {
        // 先写 execution-local 当轮快照，再写业务 collection 变量。
        // 两者与 Flowable 节点进入处在同一事务，后续失败会一并回滚。
        Map<String, Object> cache = new LinkedHashMap<>();
        cache.put("version", CYCLE_CACHE_VERSION);
        cache.put("rootExecutionId", cycleRoot.getId());
        cache.put("activityId", task.getId());
        cache.put("users", List.copyOf(users));
        cycleRoot.setVariableLocal(
                CYCLE_CACHE_VARIABLE,
                Map.copyOf(cache));
        MultiInstanceLoopCharacteristics loop =
                task.getLoopCharacteristics();
        String variableName = collectionVariable(loop);
        if (StringUtils.hasText(variableName)) {
            // 写入当前解析结果供审计与 ACTIVITY_STARTED 兼容监听器读取。
            // 下次循环会创建新 MI root，因而不会命中本轮 local 快照。
            execution.setVariable(variableName, List.copyOf(users));
        }
    }

    /**
     * 读取并验证当轮快照。本地变量若被损坏必须失败关闭，不能
     * 在已创建部分实例后重算出另一组人数。
     */
    private List<String> readCycleCollection(
            DelegateExecution cycleRoot,
            UserTask task) {
        Object raw = cycleRoot.getVariableLocal(CYCLE_CACHE_VARIABLE);
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof Map<?, ?> cache)
                || intValue(cache.get("version"))
                != CYCLE_CACHE_VERSION
                || !sameText(cache.get("rootExecutionId"),
                cycleRoot.getId())
                || !sameText(cache.get("activityId"), task.getId())
                || !(cache.get("users") instanceof Collection<?> values)) {
            throw new PersonResolutionException(
                    "ORG_SNAPSHOT_INVALID",
                    "多实例当轮人员快照无效");
        }
        ArrayList<String> rawUsers = new ArrayList<>();
        for (Object value : values) {
            if (!(value instanceof String user)
                    || !StringUtils.hasText(user)) {
                throw new PersonResolutionException(
                        "ORG_SNAPSHOT_INVALID",
                        "多实例当轮人员快照包含无效用户");
            }
            rawUsers.add(user);
        }
        List<String> users = stableUsers(rawUsers);
        if (users.isEmpty()) {
            throw new PersonResolutionException(
                    "POSITION_NO_ACTIVE_HOLDER",
                    "多实例当轮人员快照为空");
        }
        return users;
    }

    /** 找到当轮 MI root；首次计算时传入执行本身就是 root。 */
    private DelegateExecution multiInstanceRoot(
            DelegateExecution execution) {
        DelegateExecution current = execution;
        while (current != null) {
            if (current.isMultiInstanceRoot()) {
                return current;
            }
            current = current.getParent();
        }
        return execution;
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException exception) {
            return -1;
        }
    }

    private boolean sameText(Object value, String expected) {
        return value != null
                && expected != null
                && expected.equals(String.valueOf(value));
    }

    private void markNodeEntryRecovered(
            DelegateExecution execution,
            UserTask task) {
        incidentRecorder.resolveOpenNodeEntry(
                execution.getProcessInstanceId(), task.getId());
    }

    private String collectionVariable(MultiInstanceLoopCharacteristics loop) {
        if (loop == null) {
            return null;
        }
        String raw = StringUtils.hasText(loop.getInputDataItem())
                ? loop.getInputDataItem() : loop.getCollectionString();
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        if ((value.startsWith("${") || value.startsWith("#{"))
                && value.endsWith("}")) {
            value = value.substring(2, value.length() - 1).trim();
        }
        return value.matches("[A-Za-z_][A-Za-z0-9_]*")
                ? value : null;
    }

    private List<String> stableUsers(Collection<String> values) {
        LinkedHashSet<String> users = new LinkedHashSet<>();
        if (values != null) {
            values.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .forEach(users::add);
        }
        return List.copyOf(users);
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        java.util.LinkedHashMap<String, Object> result =
                new java.util.LinkedHashMap<>();
        map.forEach((key, item) ->
                result.put(String.valueOf(key), item));
        return result;
    }
}
