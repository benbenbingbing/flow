package com.workflow.process.assignment.infrastructure.flowable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.process.assignment.error.PersonResolutionException;
import com.workflow.contracts.process.assignment.model.PersonPrincipal;
import com.workflow.contracts.process.assignment.model.PersonPrincipalType;
import com.workflow.process.assignment.application.AssigneeIncidentRecorder;
import com.workflow.process.assignment.application.AssigneeResolutionService;
import com.workflow.process.assignment.application.EmptyAssigneePolicyResolver;
import com.workflow.process.assignment.application.LegacyMultiInstanceAssignmentParser;
import com.workflow.process.assignment.application.NodeAssignmentReferenceResolver;
import com.workflow.process.assignment.application.NodeAssignmentReferenceResolver.ResolvedAssignment;
import com.workflow.process.assignment.entity.EntityUserReferenceFieldConfig;
import com.workflow.process.assignment.relative.RelativeOrgPositionConfig;
import com.workflow.process.assignment.domain.AssigneeResolutionResult;
import com.workflow.process.assignment.domain.EmptyAssigneePolicy;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import com.workflow.process.engine.infrastructure.flowable.ConfiguredTaskPropertyReader;
import com.workflow.process.task.application.nextapproval.NextApproverOverrideStore;
import com.workflow.process.task.infrastructure.MultiInstanceVariableNames;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
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
 * Flowable 读取多实例 collection 时动态解析节点进入期人员来源。
 *
 * <p>仅由发布器写入的平台受控 delegateExpression 调用。该时点在引擎创建
 * 多实例执行之前；相对职务与实体用户关系字段都在这里读取最新权威状态。
 * 返回空人员时直接抛出结构化失败，保证不会以 0 实例静默通过审批。
 * Bean 名保留历史名称，以兼容既有已部署 BPMN。</p>
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

    /**
     * 初始化相对组织位置集合处理器，保存构造参数供后续方法使用。
     *
     * @param repositoryService 仓储服务依赖，保存到当前对象供后续业务方法调用
     * @param processVersionMapper 流程版本映射器依赖，保存到当前对象供后续业务方法调用
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     * @param assignmentResolver 分配解析器依赖，保存到当前对象供后续业务方法调用
     * @param referenceResolver 引用解析器依赖，保存到当前对象供后续业务方法调用
     * @param overrideStore 覆盖{@code store}依赖，保存到当前对象供后续业务方法调用
     * @param emptyPolicyResolver 空策略解析器依赖，保存到当前对象供后续业务方法调用
     * @param assigneeResolutionService 办理人解析服务依赖，保存到当前对象供后续业务方法调用
     * @param incidentRecorder 异常事件{@code recorder}依赖，保存到当前对象供后续业务方法调用
     */
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
     *
     * @param ignoredOriginalCollection {@code ignored}原始集合，作为 {@code resolveRequiredCollection} 的输入影响后续处理
     * @param execution 执行，作为 {@code resolveRequiredCollection} 的输入影响后续处理
     * @return {@code collection<?>}集合，供调用方遍历或展示
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

    /**
     * 解析必填集合；输出作为后续校验或处理的输入。
     *
     * @param ignoredOriginalCollection {@code ignored}原始集合，供本方法解析必填集合时使用
     * @param execution 执行，作为 {@code requireCurrentTask} 的输入影响后续处理
     * @return {@code collection<?>}集合，供调用方遍历或展示
     */
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
        boolean sourceMultiInstance = effective.sourceTask()
                .hasMultiInstanceLoopCharacteristics();
        String resolverCode = requireEntryDynamicResolver(
                effectiveConfig, sourceMultiInstance);
        List<String> users = stableUsers(assignmentResolver.resolve(
                publishedProcessConfigId(execution.getProcessDefinitionId()),
                currentTask.getId(),
                currentTask.getName(),
                effectiveConfig,
                execution.getVariables(),
                execution.getProcessInstanceId(),
                execution.getProcessDefinitionId(),
                assignmentVersion(effectiveConfig),
                sourceMultiInstance));
        if (users.isEmpty()) {
            throw noAvailableUsers(resolverCode);
        }
        storeResolvedCollection(execution, cycleRoot, currentTask, users);
        markNodeEntryRecovered(execution, currentTask);
        return users;
    }

    /**
     * 多实例尚未创建 Task，因此在节点进入边界直接执行可用的用户/组兜底；
     * 其余策略用 REQUIRES_NEW 记录 taskId 为空的节点级 incident，然后抛出原失败
     * 回滚源任务完成，严禁 Flowable 以 0 个实例继续。
     *
     * @param execution 执行，作为 {@code requireCurrentTask} 的输入影响后续处理
     * @param failure 失败，供本方法应用节点入口空策略时使用
     * @return {@code collection<?>}集合，供调用方遍历或展示
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
        Map<String, Object> incidentConfig = effectiveConfigOrSelf(
                model, task, config);
        String resolverCode = configuredDynamicResolverCode(
                incidentConfig,
                effectiveSourceIsMultiInstance(model, task, config));
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
                StringUtils.hasText(resolverCode)
                        ? resolverCode : "entryDynamicPersonResolver",
                mapValue(incidentConfig.get("extraParams")),
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

    /**
     * 整理兜底用户集合数据，供调用方遍历或继续处理。
     *
     * @param principals {@code principals}，作为 {@code resolvePrincipals} 的输入影响后续处理
     * @param reasonCode 原因编码，后续用于处理兜底用户集合时定位或关联目标
     * @return 相对组织位置集合，供调用方遍历或展示
     */
    private List<String> fallbackUsers(
            List<PersonPrincipal> principals,
            String reasonCode) {
        AssigneeResolutionResult result = assigneeResolutionService
                .resolvePrincipals(principals, reasonCode);
        return result.resolved()
                ? stableUsers(result.usernames()) : List.of();
    }

    /**
     * 校验并获取当前任务；不满足约束时阻止后续处理。
     *
     * @param execution 执行，作为 {@code repositoryService.getBpmnModel} 的输入影响后续处理
     * @return 校验并获取后的当前任务结果，供调用方继续处理
     */
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

    /**
     * 整理{@code deployed}分配配置数据，供调用方遍历或继续处理。
     *
     * @param userTask 用户任务，作为 {@code ConfiguredTaskPropertyReader.read} 的输入影响后续处理
     * @return {@code deployed}分配配置键值结果，供调用方继续处理
     */
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

    /**
     * collection handler 只能运行发布器明确允许的节点进入期解析器。
     *
     * @param config 配置内容，决定后续入口动态解析器的处理规则
     * @param multiInstanceSource 多实例来源，作为 {@code configuredDynamicResolverCode} 的输入影响后续处理
     * @return 校验并获取后的入口动态解析器文本，供调用方比较或展示
     */
    private String requireEntryDynamicResolver(
            Map<String, Object> config,
            boolean multiInstanceSource) {
        String resolverCode = configuredDynamicResolverCode(
                config, multiInstanceSource);
        if (!StringUtils.hasText(resolverCode)) {
            throw new PersonResolutionException(
                    "ORG_SNAPSHOT_INVALID",
                    "动态人员 collection handler 绑定了非目标 resolver");
        }
        return resolverCode;
    }

    /**
     * 生成已配置动态解析器编码文本，供后续匹配或展示。
     *
     * @param config 配置内容，决定后续已配置动态解析器编码的处理规则
     * @param multiInstanceSource 多实例来源，作为 {@code supportedDynamicResolver} 的输入影响后续处理
     * @return 处理后的已配置动态解析器编码文本，供调用方比较或展示
     */
    private String configuredDynamicResolverCode(
            Map<String, Object> config,
            boolean multiInstanceSource) {
        return supportedDynamicResolver(
                LegacyMultiInstanceAssignmentParser
                        .effectiveResolver(config, multiInstanceSource)
                        .resolverCode());
    }

    /**
     * 列出支持的动态解析器；结果供调用方的后续步骤使用。
     *
     * @param resolverCode 解析器编码，后续用于列出支持的动态解析器时定位或关联目标
     * @return 列出支持的后的动态解析器文本，供调用方比较或展示
     */
    private String supportedDynamicResolver(String resolverCode) {
        return RelativeOrgPositionConfig.RESOLVER_CODE.equals(resolverCode)
                || EntityUserReferenceFieldConfig.RESOLVER_CODE.equals(
                resolverCode)
                ? resolverCode : "";
    }

    /**
     * incident 也记录节点引用最终指向的解析器和参数。
     *
     * @param model 模型，作为 {@code referenceResolver.resolve} 的输入影响后续处理
     * @param task 任务，作为 {@code referenceResolver.resolve} 的输入影响后续处理
     * @param config 配置内容，决定后续有效配置或{@code self}的处理规则
     * @return 有效配置或{@code self}键值结果，供调用方继续处理
     */
    private Map<String, Object> effectiveConfigOrSelf(
            BpmnModel model,
            UserTask task,
            Map<String, Object> config) {
        try {
            return referenceResolver.resolve(model, task, config)
                    .assigneeConfig();
        } catch (RuntimeException ignored) {
            // 原异常仍是节点进入失败的权威原因；incident 至少保留当前节点配置。
            return config;
        }
    }

    /**
     * 判断有效来源是否多实例条件是否成立，供调用方选择后续分支。
     *
     * @param model 模型，作为 {@code referenceResolver.resolve} 的输入影响后续处理
     * @param task 任务，作为 {@code referenceResolver.resolve} 的输入影响后续处理
     * @param config 配置内容，决定后续有效来源是否多实例的处理规则
     * @return 有效来源是否多实例条件成立时为 true，否则为 false
     */
    private boolean effectiveSourceIsMultiInstance(
            BpmnModel model,
            UserTask task,
            Map<String, Object> config) {
        try {
            return referenceResolver.resolve(model, task, config)
                    .sourceTask()
                    .hasMultiInstanceLoopCharacteristics();
        } catch (RuntimeException ignored) {
            return task.hasMultiInstanceLoopCharacteristics();
        }
    }

    /**
     * 构造无可用用户集合异常，供调用方区分失败原因。
     *
     * @param resolverCode 解析器编码，后续用于处理无可用用户集合时定位或关联目标
     * @return 处理后的无可用用户集合结果，供调用方继续处理
     */
    private PersonResolutionException noAvailableUsers(
            String resolverCode) {
        if (EntityUserReferenceFieldConfig.RESOLVER_CODE.equals(
                resolverCode)) {
            return new PersonResolutionException(
                    "ENTITY_USER_REFERENCE_EMPTY",
                    "实体用户关系字段多实例没有可用参与人");
        }
        return new PersonResolutionException(
                "POSITION_NO_ACTIVE_HOLDER",
                "相对组织职务多实例没有可用参与人");
    }

    /**
     * 处理分配版本，并将结果传给后续步骤。
     *
     * @param config 配置内容，决定后续分配版本的处理规则
     * @return 处理后的分配版本结果，供调用方继续处理
     */
    private int assignmentVersion(Map<String, Object> config) {
        Object value = config.get("assignmentConfigVersion");
        if (value instanceof Number number && number.intValue() == 2) {
            return 2;
        }
        if (value == null) {
            // 无版本配置按历史 v1 运行。既支持 collectionResolverCode，
            // 也支持更早期仅使用基础 resolver 的多实例部署。
            return 1;
        }
        throw new PersonResolutionException(
                "ORG_SNAPSHOT_INVALID",
                "节点进入期动态解析器多实例缺少受支持的人员配置版本");
    }

    /**
     * 生成已发布流程配置ID文本，供后续匹配或展示。
     *
     * @param processDefinitionId 流程定义 ID，用于读取对应的已发布流程配置
     * @return 处理后的已发布流程配置ID文本，供调用方比较或展示
     */
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

    /**
     * 处理{@code store}已解析集合，并将结果传给后续步骤。
     *
     * @param execution 执行，供本方法处理{@code store}已解析集合时使用
     * @param cycleRoot {@code cycle}根，作为 {@code cache.put} 的输入影响后续处理
     * @param task 任务，作为 {@code cache.put} 的输入影响后续处理
     * @param users 用户集合，作为 {@code cache.put} 的输入影响后续处理
     */
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
        String variableName = MultiInstanceVariableNames
                .resolveCollectionVariable(task);
        if (StringUtils.hasText(variableName)) {
            // 写入当前解析结果供审计与 ACTIVITY_STARTED 兼容监听器读取。
            // 下次循环会创建新 MI root，因而不会命中本轮 local 快照。
            execution.setVariable(variableName, List.copyOf(users));
        }
    }

    /**
     * 读取并验证当轮快照。本地变量若被损坏必须失败关闭，不能
     * 在已创建部分实例后重算出另一组人数。
     *
     * @param cycleRoot {@code cycle}根，供本方法读取{@code cycle}集合时使用
     * @param task 任务，供本方法读取{@code cycle}集合时使用
     * @return 相对组织位置集合，供调用方遍历或展示
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

    /**
     * 找到当轮 MI root；首次计算时传入执行本身就是 root。
     *
     * @param execution 执行，供本方法处理多实例根时使用
     * @return 处理后的多实例根结果，供调用方继续处理
     */
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

    /**
     * 处理整数值，并将结果传给后续步骤。
     *
     * @param value 待处理整数值的原始输入，结果供调用方继续使用
     * @return 处理后的整数值结果，供调用方继续处理
     */
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

    /**
     * 判断相同文本条件是否成立，供调用方选择后续分支。
     *
     * @param value 待处理相同文本的原始输入，结果供调用方继续使用
     * @param expected 预期，供本方法处理相同文本时使用
     * @return 相同文本条件成立时为 true，否则为 false
     */
    private boolean sameText(Object value, String expected) {
        return value != null
                && expected != null
                && expected.equals(String.valueOf(value));
    }

    /**
     * 标记节点入口{@code recovered}；后续读取或执行将使用更新后的状态。
     *
     * @param execution 执行，作为 {@code incidentRecorder.resolveOpenNodeEntry} 的输入影响后续处理
     * @param task 任务，供本方法标记节点入口{@code recovered}时使用
     */
    private void markNodeEntryRecovered(
            DelegateExecution execution,
            UserTask task) {
        incidentRecorder.resolveOpenNodeEntry(
                execution.getProcessInstanceId(), task.getId());
    }

    /**
     * 整理稳定用户集合数据，供调用方遍历或继续处理。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 相对组织位置集合，供调用方遍历或展示
     */
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

    /**
     * 按候选顺序取首个非空文本，供后续匹配或展示使用。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的首个文本文本，供调用方比较或展示
     */
    private String firstText(Object... values) {
        for (Object value : values) {
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }

    /**
     * 将动态值转换为键值映射，供后续字段读取和校验。
     *
     * @param value 待处理映射值的原始输入，结果供调用方继续使用
     * @return 映射值键值结果，供调用方继续处理
     */
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
