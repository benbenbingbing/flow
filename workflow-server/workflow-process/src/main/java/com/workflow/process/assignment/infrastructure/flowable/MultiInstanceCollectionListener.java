package com.workflow.process.assignment.infrastructure.flowable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysUserGroupMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserRoleMapper;
import com.workflow.process.assignment.application.LegacyMultiInstanceAssignmentParser;
import com.workflow.process.assignment.application.NodeAssignmentReferenceResolver;
import com.workflow.process.assignment.application.NodeAssignmentReferenceResolver.ResolvedAssignment;
import com.workflow.process.assignment.application.PersonResolverRuntimeService;
import com.workflow.process.assignment.entity.EntityUserReferenceFieldConfig;
import com.workflow.process.assignment.relative.RelativeOrgPositionConfig;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import com.workflow.process.task.infrastructure.MultiInstanceVariableNames;
import com.workflow.process.task.application.nextapproval.NextApproverOverrideStore;
import com.workflow.process.engine.infrastructure.flowable.ConfiguredTaskPropertyReader;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.event.FlowableActivityEvent;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.bpmn.model.Activity;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.bpmn.model.SubProcess;
import org.flowable.bpmn.model.UserTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 多实例集合变量自动准备监听器
 * 
 * 支持两种工作模式：
 * 1. 流程启动时预计算（主要方式）：prepareVariables() 在 startProcessInstance 前调用
 * 2. 运行时兜底（次要方式）：监听 ACTIVITY_STARTED 事件，处理子流程等动态场景
 */
@Slf4j
@Component
public class MultiInstanceCollectionListener implements FlowableEventListener {

    @Autowired
    private RuntimeService runtimeService;

    /** 流程定义服务，用 Flowable 定义ID安全解析流程键 */
    @Autowired
    private RepositoryService repositoryService;

    /** 发布版本 Mapper，仅通过部署 ID 解析该版本所属的稳定流程配置身份。 */
    @Autowired
    private ProcessVersionHistoryMapper processVersionMapper;

    /** 用户组 Mapper，按组码查询成员 */
    @Autowired
    private SysGroupMapper groupMapper;

    /** 用户-用户组关联 Mapper，查询组成员 */
    @Autowired
    private SysUserGroupMapper userGroupMapper;

    /** 角色 Mapper，按角色编码查询 */
    @Autowired
    private SysRoleMapper roleMapper;

    /** 用户-角色关联 Mapper，查询角色成员 */
    @Autowired
    private SysUserRoleMapper userRoleMapper;

    /** 用户 Mapper，将所有配置结果统一归一为启用本地用户名。 */
    @Autowired
    private SysUserMapper userMapper;

    /** JSON 序列化工具 */
    @Autowired
    private ObjectMapper objectMapper;

    /** 统一人员解析器运行时 */
    @Autowired
    private PersonResolverRuntimeService personResolverRuntimeService;

    /** 已部署人员配置解析器；required=false 仅兼容直接 new 的旧测试。 */
    @Autowired(required = false)
    private MultiInstanceAssignmentResolver multiInstanceAssignmentResolver;

    /** 部署内节点引用解析器；required=false 仅兼容直接 new 的旧测试。 */
    @Autowired(required = false)
    private NodeAssignmentReferenceResolver nodeReferenceResolver;

    /** 人工指定的下一多实例审批人一次性覆盖。 */
    @Autowired
    private NextApproverOverrideStore nextApproverOverrideStore;

    /**
     * 流程启动前预计算多实例集合变量（主要入口）
     *
     * @param processDefinitionId 流程定义 ID，用于读取对应的已发布流程配置
     * @param variables 流程变量，后续传给流程引擎或规则求值器使用
     */
    public void prepareVariables(
            String processDefinitionId,
            Map<String, Object> variables) {
        if (!StringUtils.hasText(processDefinitionId)
                || variables == null) {
            return;
        }
        BpmnModel model = repositoryService.getBpmnModel(
                processDefinitionId);
        if (model == null || model.getMainProcess() == null) {
            log.warn(
                    "多实例变量预计算跳过未知部署模型: processDefinitionId={}",
                    processDefinitionId);
            return;
        }
        ProcessDefinition definition = repositoryService
                .getProcessDefinition(processDefinitionId);
        String processConfigId = publishedProcessConfigId(definition);
        List<Activity> activities = new ArrayList<>();
        collectActivities(
                model.getMainProcess().getFlowElements(), activities);
        for (Activity activity : activities) {
            if (!activity.hasMultiInstanceLoopCharacteristics()) {
                continue;
            }
            String assigneeDocument = ConfiguredTaskPropertyReader.read(
                    activity, "assigneeConfig");
            boolean safetyCriticalDocument =
                    NextApproverAssignmentRequirement.requiresFailClosed(
                            assigneeDocument);
            initializeOutcomeVariables(variables, activity.getId());
            boolean configParsed = false;
            boolean required = false;
            boolean visible = false;
            boolean editable = false;
            try {
                String varName = MultiInstanceVariableNames
                        .resolveCollectionVariable(activity);
                if (!StringUtils.hasText(varName)) {
                    continue;
                }
                Map<String, Object> assigneeConfig =
                        deployedAssigneeConfig(activity);
                configParsed = true;
                visible = NextApproverAssignmentRequirement.isRequired(
                        assigneeConfig);
                editable = NextApproverAssignmentRequirement.isEditable(
                        assigneeConfig);
                if (editable && !visible) {
                    throw required(
                            "下一审批人可修改时必须同时允许展示",
                            null);
                }
                if (assigneeConfig.isEmpty()) {
                    continue;
                }
                boolean unifiedAssignment =
                        requireSupportedAssignmentConfigVersion(
                                assigneeConfig) == 2;
                // v2 多实例没有可回退的旧独立人员来源，即使下一审批人隐藏
                // 也必须失败关闭，不能用空 collection 静默跳过审批。
                required = visible || editable || unifiedAssignment;
                if (variables.containsKey(varName)
                        && !unifiedAssignment) {
                    continue;
                }
                if (unifiedAssignment) {
                    // v2 明确要求参与人来自基础办理人配置，不能让启动请求中
                    // 同名流程变量绕过已发布的人员边界。
                    variables.remove(varName);
                }
                EffectiveAssignment effective = effectiveAssignment(
                        model, activity, assigneeConfig);
                if (usesEntryDynamicResolver(
                        effective.assigneeConfig(),
                        effective.sourceActivity()
                                .hasMultiInstanceLoopCharacteristics())) {
                    // 可变权威来源必须在节点进入时解析。发布器已写入 collection
                    // handler。Flowable 会先计算原 collection EL，之后才调用
                    // handler，因此这里必须用可信空集合覆盖调用方伪造值：既
                    // 保证 EL 可计算，又不在后续表单保存前冻结实体用户字段。
                    variables.put(varName, List.of());
                    continue;
                }
                List<String> userIds = resolvePublishedUsers(
                        processConfigId,
                        activity.getId(),
                        activity.getName(),
                        effective.assigneeConfig(),
                        variables,
                        null,
                        processDefinitionId,
                        effective.sourceActivity()
                                .hasMultiInstanceLoopCharacteristics());
                if (!userIds.isEmpty()) {
                    variables.put(varName, userIds);
                    log.info(
                            "按已部署 BPMN 预计算多实例变量: processDefinitionId={}, nodeId={}, varName={}, users={}",
                            processDefinitionId,
                            activity.getId(),
                            varName,
                            userIds);
                } else if (required && !(visible && editable)) {
                    throw required(
                            "已开启下一审批人展示的多实例节点没有可用审批人",
                            null);
                } else if (required) {
                    // 可编辑节点允许启动时没有默认人员，前序任务稍后必须通过
                    // 一次性覆盖补齐；真正进入节点时仍会严格拒绝空集合。
                    log.debug(
                            "可编辑下一审批人多实例节点启动时暂无默认人员: processDefinitionId={}, nodeId={}",
                            processDefinitionId,
                            activity.getId());
                }
            } catch (RequiredMultiInstanceAssignmentException exception) {
                throw exception;
            } catch (Exception e) {
                if (required
                        || (!configParsed && safetyCriticalDocument)) {
                    throw required(
                            "安全关键多实例人员配置无法解析或执行",
                            e);
                }
                log.error(
                        "多实例变量预计算失败: processDefinitionId={}, nodeId={}",
                        processDefinitionId,
                        activity.getId(),
                        e);
            }
        }
    }

    /* ==================== 运行时兜底：全局事件监听 ==================== */

    /**
     * 监听活动开始事件，作为运行时兜底补充多实例集合变量。
     *
     * @param event Flowable 事件
     */
    @Override
    public void onEvent(FlowableEvent event) {
        if (event.getType() != FlowableEngineEventType.ACTIVITY_STARTED
                || !(event instanceof FlowableActivityEvent)) {
            return;
        }
        FlowableActivityEvent activityEvent = (FlowableActivityEvent) event;

        String activityId = activityEvent.getActivityId();
        String processInstanceId = activityEvent.getProcessInstanceId();

        try {
            prepareMultiInstanceCollection(
                    processInstanceId,
                    activityId,
                    activityEvent.getProcessDefinitionId(),
                    activityEvent.getExecutionId());
        } catch (RequiredMultiInstanceAssignmentException exception) {
            log.error(
                    "安全关键多实例人员准备失败，将回滚活动进入: processInstanceId={}, activityId={}, message={}",
                    processInstanceId,
                    activityId,
                    exception.getMessage(),
                    exception);
            throw exception;
        } catch (Exception e) {
            // 旧流程未开启下一审批人展示时保留兼容语义：记录错误但不阻断。
            log.error(
                    "旧版多实例集合变量运行时准备失败，按兼容模式继续: processInstanceId={}, activityId={}",
                    processInstanceId,
                    activityId,
                    e);
        }
    }

    /**
     * 运行时兜底：当多实例节点集合变量不存在时，按节点配置补充设置。
     * <p>
     * 通过实例绑定的部署定义定位多实例节点，只读取该部署 BPMN 中的人员配置并写入变量。
     *
     * @param processInstanceId 流程实例ID
     * @param activityId        活动（节点）ID
     * @param eventProcessDefinitionId 事件流程定义ID，后续用于准备多实例集合时定位或关联目标
     * @throws Exception 查询流程实例或解析配置失败时抛出
     */
    private void prepareMultiInstanceCollection(
            String processInstanceId,
            String activityId,
            String eventProcessDefinitionId) throws Exception {
        prepareMultiInstanceCollection(
                processInstanceId, activityId, eventProcessDefinitionId, null);
    }

    /**
     * 准备多实例集合；结果供调用方的后续步骤使用。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param activityId 活动ID，后续用于准备多实例集合时定位或关联目标
     * @param eventProcessDefinitionId 事件流程定义ID，后续用于准备多实例集合时定位或关联目标
     * @param executionId 执行ID，后续用于准备多实例集合时定位或关联目标
     * @throws Exception 下游操作失败时向调用方传递
     */
    private void prepareMultiInstanceCollection(
            String processInstanceId,
            String activityId,
            String eventProcessDefinitionId,
            String executionId) throws Exception {
        if (processInstanceId == null || processInstanceId.isBlank()
                || activityId == null || activityId.isBlank()) {
            return;
        }
        String processDefinitionId = eventProcessDefinitionId;
        if (processDefinitionId == null || processDefinitionId.isBlank()) {
            var processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            if (processInstance == null) {
                return;
            }
            processDefinitionId = processInstance.getProcessDefinitionId();
        }
        ProcessDefinition processDefinition = repositoryService
                .createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId)
                .singleResult();
        if (processDefinition == null) {
            log.debug(
                    "多实例运行时兜底跳过未知流程定义: processDefinitionId={}",
                    processDefinitionId);
            return;
        }
        BpmnModel bpmnModel = repositoryService.getBpmnModel(
                processDefinitionId);
        FlowElement deployedElement = bpmnModel == null
                || bpmnModel.getMainProcess() == null
                ? null
                : bpmnModel.getMainProcess().getFlowElement(
                        activityId, true);
        if (!(deployedElement instanceof Activity deployedActivity)
                || !deployedActivity.hasMultiInstanceLoopCharacteristics()) {
            return;
        }
        String assigneeDocument = ConfiguredTaskPropertyReader.read(
                deployedActivity, "assigneeConfig");
        boolean safetyCriticalDocument =
                NextApproverAssignmentRequirement.requiresFailClosed(
                        assigneeDocument);
        resetOutcomeVariablesIfNewCycle(
                processInstanceId, executionId, deployedActivity.getId());
        boolean configParsed = false;
        boolean required = false;
        try {
            Map<String, Object> deployedAssigneeConfig =
                    deployedAssigneeConfig(deployedActivity);
            configParsed = true;
            int assignmentVersion =
                    requireSupportedAssignmentConfigVersion(
                            deployedAssigneeConfig);
            required = assignmentVersion == 2
                    || NextApproverAssignmentRequirement.isRequired(
                    deployedAssigneeConfig)
                    || NextApproverAssignmentRequirement.isEditable(
                    deployedAssigneeConfig);
            if (NextApproverAssignmentRequirement.isEditable(
                    deployedAssigneeConfig)
                    && !NextApproverAssignmentRequirement.isRequired(
                    deployedAssigneeConfig)) {
                throw required(
                        "下一审批人可修改时必须同时允许展示",
                        null);
            }
            String varName = MultiInstanceVariableNames
                    .resolveCollectionVariable(deployedActivity);
            if (varName == null) {
                if (required) {
                    throw required(
                            "已开启下一审批人展示的多实例节点缺少有效集合变量",
                            null);
                }
                log.warn(
                        "多实例运行时兜底跳过无效集合表达式: processDefinitionId={}, activityId={}",
                        processDefinitionId,
                        activityId);
                return;
            }

            // 必须优先于“变量已存在”判断，人工覆盖拥有最高优先级。
            boolean overrideExpected = hasStagedOverride(
                    processInstanceId, activityId);
            if (nextApproverOverrideStore != null
                    && (required || overrideExpected)) {
                List<String> overrideUsers;
                try {
                    overrideUsers = nextApproverOverrideStore
                            .consumeForMultiInstance(
                                    processInstanceId, activityId);
                } catch (RuntimeException exception) {
                    if (required || overrideExpected) {
                        throw required(
                                "消费下一审批人多实例覆盖失败",
                                exception);
                    }
                    throw exception;
                }
                if (!overrideUsers.isEmpty()) {
                    try {
                        runtimeService.setVariable(
                                processInstanceId,
                                varName,
                                overrideUsers);
                    } catch (RuntimeException exception) {
                        // consume 与 setVariable 必须在同一 Flowable 事务回滚。
                        throw required(
                                "写入下一审批人多实例覆盖失败",
                                exception);
                    }
                    log.info(
                            "多实例集合已消费下一审批人覆盖: processInstanceId={}, activityId={}, varName={}, userCount={}",
                            processInstanceId,
                            activityId,
                            varName,
                            overrideUsers.size());
                    return;
                }
            }

            // 运行时兜底：只有变量不存在时才补充设置。可见节点的空集合
            // 不能被视为已准备，否则会静默进入无人多实例。
            Object existingCollection = runtimeService.getVariable(
                    processInstanceId, varName);
            if (existingCollection != null) {
                if (required && !hasParticipants(existingCollection)) {
                    throw required(
                            "已开启下一审批人展示的多实例节点人员集合为空",
                            null);
                }
                return;
            }
            if (deployedAssigneeConfig.isEmpty()) {
                // 已尝试从同一 BPMN 的历史扩展属性和字面量
                // assignee/candidate 属性恢复；仍为空时绝不能回查当前可变节点表。
                return;
            }
            Map<String, Object> variables =
                    runtimeService.getVariables(processInstanceId);
            EffectiveAssignment effective = effectiveAssignment(
                    bpmnModel,
                    deployedActivity,
                    deployedAssigneeConfig);
            List<String> userIds = resolvePublishedUsers(
                    publishedProcessConfigId(processDefinition),
                    activityId,
                    deployedActivity.getName(),
                    effective.assigneeConfig(),
                    variables,
                    processInstanceId,
                    processDefinitionId,
                    effective.sourceActivity()
                            .hasMultiInstanceLoopCharacteristics());
            if (userIds.isEmpty()) {
                if (required) {
                    throw required(
                            "已开启下一审批人展示的多实例节点没有可用审批人",
                            null);
                }
                return;
            }
            runtimeService.setVariable(
                    processInstanceId, varName, userIds);
            log.info("多实例集合变量运行时补充设置: processInstanceId={}, activityId={}, varName={}, users={}",
                    processInstanceId, activityId, varName, userIds);
        } catch (RequiredMultiInstanceAssignmentException exception) {
            throw exception;
        } catch (Exception exception) {
            if (required || (!configParsed && safetyCriticalDocument)) {
                throw required(
                        "安全关键多实例节点人员配置无法解析或准备",
                        exception);
            }
            throw exception;
        }
    }

    /**
     * 判断是否具有{@code participants}；判断结果决定调用方的后续分支。
     *
     * @param collection 集合，作为 {@code lang.reflect.Array.getLength} 的输入影响后续处理
     * @return {@code participants}条件成立时为 true，否则为 false
     */
    private boolean hasParticipants(Object collection) {
        if (collection instanceof Collection<?> values) {
            return values.stream().anyMatch(value -> value != null
                    && (!(value instanceof String text)
                    || StringUtils.hasText(text)));
        }
        if (collection != null && collection.getClass().isArray()) {
            return java.lang.reflect.Array.getLength(collection) > 0;
        }
        if (collection instanceof Iterable<?> values) {
            return values.iterator().hasNext();
        }
        return false;
    }

    /**
     * 判断是否具有{@code staged}覆盖；判断结果决定调用方的后续分支。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param activityId 活动ID，后续用于判断是否具有{@code staged}覆盖时定位或关联目标
     * @return {@code staged}覆盖条件成立时为 true，否则为 false
     */
    private boolean hasStagedOverride(
            String processInstanceId,
            String activityId) {
        return nextApproverOverrideStore != null
                && nextApproverOverrideStore.hasStagedOverride(
                        processInstanceId, activityId);
    }

    /**
     * 构造必填异常，供调用方区分失败原因。
     *
     * @param message 消息，作为 {@code RequiredMultiInstanceAssignmentException} 的输入影响后续处理
     * @param cause 原因，作为 {@code RequiredMultiInstanceAssignmentException} 的输入影响后续处理
     * @return 处理后的必填结果，供调用方继续处理
     */
    private RequiredMultiInstanceAssignmentException required(
            String message,
            Throwable cause) {
        return new RequiredMultiInstanceAssignmentException(
                message, cause);
    }

    /**
     * 收集{@code activities}；结果供调用方的后续步骤使用。
     *
     * @param elements {@code elements}，供本方法收集{@code activities}时使用
     * @param target 目标，供本方法收集{@code activities}时使用
     */
    private void collectActivities(
            Collection<FlowElement> elements,
            List<Activity> target) {
        if (elements == null) {
            return;
        }
        for (FlowElement element : elements) {
            if (element instanceof Activity activity) {
                target.add(activity);
            }
            if (element instanceof SubProcess subProcess) {
                collectActivities(subProcess.getFlowElements(), target);
            }
        }
    }

    /**
     * 只从部署 BPMN 恢复人员配置。新版本读取 assigneeConfig；历史版本可从
     * 同一部署的 multiInstanceConfig 及字面量 assignee/candidate 属性兼容。
     *
     * @param activity 活动，作为 {@code ConfiguredTaskPropertyReader.read} 的输入影响后续处理
     * @return {@code deployed}办理人配置键值结果，供调用方继续处理
     * @throws Exception 下游操作失败时向调用方传递
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> deployedAssigneeConfig(
            Activity activity) throws Exception {
        Map<String, Object> multiInstanceConfig = Map.of();
        String multiInstanceDocument = ConfiguredTaskPropertyReader.read(
                activity, "multiInstanceConfig");
        if (StringUtils.hasText(multiInstanceDocument)) {
            multiInstanceConfig = objectMapper.readValue(
                    multiInstanceDocument, Map.class);
        }
        Map<String, Object> assigneeConfig = Map.of();
        String assigneeDocument = ConfiguredTaskPropertyReader.read(
                activity, "assigneeConfig");
        if (StringUtils.hasText(assigneeDocument)) {
            assigneeConfig = objectMapper.readValue(
                    assigneeDocument, Map.class);
        }
        Map<String, Object> result =
                LegacyMultiInstanceAssignmentParser.mergeConfigs(
                        assigneeConfig, multiInstanceConfig);
        if (!(activity instanceof UserTask userTask)) {
            return result;
        }
        if ("2".equals(String.valueOf(
                result.get("assignmentConfigVersion")))) {
            return result;
        }
        LinkedHashSet<String> users = new LinkedHashSet<>();
        addCsv(users, result.get("multiInstanceUsernames"));
        if (literal(userTask.getAssignee())) {
            users.add(userTask.getAssignee().trim());
        }
        if (userTask.getCandidateUsers() != null) {
            userTask.getCandidateUsers().stream()
                    .filter(this::literal)
                    .map(String::trim)
                    .forEach(users::add);
        }
        if (!users.isEmpty()) {
            result.put("multiInstanceUsernames", List.copyOf(users));
        }
        LinkedHashSet<String> groups = new LinkedHashSet<>();
        LinkedHashSet<String> roles = new LinkedHashSet<>();
        addCsv(groups, result.get("multiInstanceGroupCodes"));
        addCsv(roles, result.get("multiInstanceRoleCodes"));
        if (userTask.getCandidateGroups() != null) {
            for (String group : userTask.getCandidateGroups()) {
                if (!literal(group)) {
                    continue;
                }
                String value = group.trim();
                if (value.startsWith("ROLE_")) {
                    roles.add(value.substring(5));
                } else {
                    groups.add(value);
                }
            }
        }
        if (!groups.isEmpty()) {
            result.put("multiInstanceGroupCodes", List.copyOf(groups));
        }
        if (!roles.isEmpty()) {
            result.put("multiInstanceRoleCodes", List.copyOf(roles));
        }
        return result;
    }

    /**
     * 解析同一部署中的节点引用，并用终端 UserTask 的历史 BPMN 字面量补齐
     * 兼容配置。引用者是否为多实例只影响后续输出，不受源节点循环属性影响。
     *
     * @param model 模型，供本方法处理有效分配时使用
     * @param currentActivity 当前活动，作为 {@code EffectiveAssignment} 的输入影响后续处理
     * @param currentConfig 当前配置内容，决定后续有效分配的处理规则
     * @return 处理后的有效分配结果，供调用方继续处理
     * @throws Exception 下游操作失败时向调用方传递
     */
    private EffectiveAssignment effectiveAssignment(
            BpmnModel model,
            Activity currentActivity,
            Map<String, Object> currentConfig) throws Exception {
        if (!(currentActivity instanceof UserTask currentTask)
                || !NodeAssignmentReferenceResolver
                .isEffectiveNodeReference(
                        currentConfig,
                        currentTask.hasMultiInstanceLoopCharacteristics())) {
            return new EffectiveAssignment(
                    currentActivity, currentConfig);
        }
        ResolvedAssignment resolved = nodeReferenceResolver().resolve(
                model, currentTask, currentConfig);
        Map<String, Object> sourceConfig = deployedAssigneeConfig(
                resolved.sourceTask());
        return new EffectiveAssignment(
                resolved.sourceTask(), sourceConfig);
    }

    /**
     * 判断字面值条件是否成立，供调用方选择后续分支。
     *
     * @param value 待处理字面值的原始输入，结果供调用方继续使用
     * @return 字面值条件成立时为 true，否则为 false
     */
    private boolean literal(String value) {
        return StringUtils.hasText(value)
                && !value.contains("${")
                && !value.contains("#{");
    }

    /**
     * 判断是否必须推迟到节点进入时读取权威业务状态。
     *
     * @param config 配置内容，决定后续使用入口动态解析器的处理规则
     * @param multiInstanceSource 多实例来源，作为 {@code effectiveResolver} 的输入影响后续处理
     * @return 使用入口动态解析器条件成立时为 true，否则为 false
     */
    private boolean usesEntryDynamicResolver(
            Map<String, Object> config,
            boolean multiInstanceSource) {
        String resolverCode = LegacyMultiInstanceAssignmentParser
                .effectiveResolver(config, multiInstanceSource)
                .resolverCode();
        return RelativeOrgPositionConfig.RESOLVER_CODE.equals(resolverCode)
                || EntityUserReferenceFieldConfig.RESOLVER_CODE.equals(
                resolverCode);
    }

    /**
     * 规范化分配类型；输出作为后续校验或处理的输入。
     *
     * @param raw 待规范化分配类型的原始输入，结果供调用方继续使用
     * @return 规范化后的分配类型文本，供调用方比较或展示
     */
    private String normalizeAssignmentType(Object raw) {
        String value = text(raw);
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim().toLowerCase(
                java.util.Locale.ROOT);
        return "interface".equals(normalized)
                ? "resolver" : normalized;
    }

    /**
     * 生成已发布流程配置ID文本，供后续匹配或展示。
     *
     * @param definition 定义，作为 {@code findByDeploymentId} 的输入影响后续处理
     * @return 处理后的已发布流程配置ID文本，供调用方比较或展示
     */
    private String publishedProcessConfigId(
            ProcessDefinition definition) {
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
     * 判断是否失败异常；判断结果决定调用方的后续分支。
     *
     * @return 失败异常条件成立时为 true，否则为 false
     */
    @Override
    public boolean isFailOnException() {
        return true;
    }

    /**
     * 读取事务；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的事务文本，供调用方比较或展示
     */
    @Override
    public String getOnTransaction() {
        return null;
    }

    /**
     * 判断是否{@code fire}事务生命周期事件；判断结果决定调用方的后续分支。
     *
     * @return {@code fire}事务生命周期事件条件成立时为 true，否则为 false
     */
    @Override
    public boolean isFireOnTransactionLifecycleEvent() {
        return false;
    }

    /**
     * 将动态值转换为键值映射，供后续字段读取和校验。
     *
     * @param value 待处理映射值的原始输入，结果供调用方继续使用
     * @return 映射值键值结果，供调用方继续处理
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?>
                ? (Map<String, Object>) value
                : Map.of();
    }

    /**
     * 按候选顺序取首个非空文本，供后续匹配或展示使用。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的首个文本文本，供调用方比较或展示
     */
    private String firstText(Object... values) {
        for (Object value : values) {
            String text = text(value);
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return null;
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

    /**
     * 生成空值安全文本，供后续匹配或展示。
     *
     * @param value 待处理空值安全的原始输入，结果供调用方继续使用
     * @return 处理后的空值安全文本，供调用方比较或展示
     */
    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    /**
     * 流程启动时为每个会签节点准备通过人数与否决标记。
     * 启动阶段每次都写成初始值，避免沿用调用方传入的脏计数。
     *
     * @param variables 流程变量，后续传给流程引擎或规则求值器使用
     * @param activityId 活动ID，后续用于处理{@code initialize}结果流程变量时定位或关联目标
     */
    private void initializeOutcomeVariables(
            Map<String, Object> variables,
            String activityId) {
        if (variables == null || activityId == null || activityId.isBlank()) {
            return;
        }
        variables.put(
                MultiInstanceVariableNames.buildApprovedCountVariableName(activityId),
                0);
        variables.put(
                MultiInstanceVariableNames.buildRejectedVariableName(activityId),
                false);
    }

    /**
     * 节点重入时重置计数。串行会签后续实例的 ACTIVITY_STARTED 不能清零，
     * 因此只在 nrOfCompletedInstances 仍为 0（新一轮多实例）时重置。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param executionId 执行ID，后续用于处理重置结果流程变量条件新{@code cycle}时定位或关联目标
     * @param activityId 活动ID，后续用于处理重置结果流程变量条件新{@code cycle}时定位或关联目标
     */
    private void resetOutcomeVariablesIfNewCycle(
            String processInstanceId,
            String executionId,
            String activityId) {
        if (processInstanceId == null || processInstanceId.isBlank()
                || activityId == null || activityId.isBlank()) {
            return;
        }
        if (!isNewMultiInstanceCycle(processInstanceId, executionId)) {
            return;
        }
        runtimeService.setVariable(
                processInstanceId,
                MultiInstanceVariableNames.buildApprovedCountVariableName(activityId),
                0);
        runtimeService.setVariable(
                processInstanceId,
                MultiInstanceVariableNames.buildRejectedVariableName(activityId),
                false);
    }

    /**
     * 判断是否新多实例{@code cycle}；判断结果决定调用方的后续分支。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param executionId 执行ID，后续用于判断是否新多实例{@code cycle}时定位或关联目标
     * @return 新多实例{@code cycle}条件成立时为 true，否则为 false
     */
    private boolean isNewMultiInstanceCycle(
            String processInstanceId,
            String executionId) {
        Object completed = null;
        if (StringUtils.hasText(executionId)) {
            try {
                completed = runtimeService.getVariableLocal(
                        executionId, "nrOfCompletedInstances");
                if (completed == null) {
                    var execution = runtimeService.createExecutionQuery()
                            .executionId(executionId)
                            .singleResult();
                    if (execution != null
                            && StringUtils.hasText(execution.getParentId())) {
                        completed = runtimeService.getVariableLocal(
                                execution.getParentId(),
                                "nrOfCompletedInstances");
                    }
                }
            } catch (RuntimeException ignored) {
                // 回退到流程变量
            }
        }
        if (completed == null) {
            completed = runtimeService.getVariable(
                    processInstanceId, "nrOfCompletedInstances");
        }
        if (completed == null) {
            return true;
        }
        if (completed instanceof Number number) {
            return number.intValue() == 0;
        }
        try {
            return Integer.parseInt(String.valueOf(completed).trim()) == 0;
        } catch (NumberFormatException exception) {
            return true;
        }
    }

    /**
     * 将人员展开委托给独立组件，监听器只保留版本门禁和变量编排。
     *
     * @param processConfigId 流程配置ID，后续用于解析已发布用户集合时定位或关联目标
     * @param nodeId 节点ID，后续用于解析已发布用户集合时定位或关联目标
     * @param nodeName 节点名称，后续用于解析已发布用户集合时匹配或展示
     * @param assigneeConfig 办理人配置内容，决定后续已发布用户集合的处理规则
     * @param variables 流程变量，后续传给流程引擎或规则求值器使用
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param processDefinitionId 流程定义 ID，用于读取对应的已发布流程配置
     * @param multiInstanceSource 多实例来源，供本方法解析已发布用户集合时使用
     * @return 多实例集合监听器集合，供调用方遍历或展示
     */
    private List<String> resolvePublishedUsers(
            String processConfigId,
            String nodeId,
            String nodeName,
            Map<String, Object> assigneeConfig,
            Map<String, Object> variables,
            String processInstanceId,
            String processDefinitionId,
            boolean multiInstanceSource) {
        int assignmentVersion = requireSupportedAssignmentConfigVersion(
                assigneeConfig);
        return assignmentResolver().resolve(
                processConfigId,
                nodeId,
                nodeName,
                assigneeConfig,
                variables,
                processInstanceId,
                processDefinitionId,
                assignmentVersion,
                multiInstanceSource);
    }

    /**
     * 兼容直接构造监听器的轻量测试；生产环境使用 Spring 注入的共享解析器。
     *
     * @return 处理后的分配解析器结果，供调用方继续处理
     */
    private MultiInstanceAssignmentResolver assignmentResolver() {
        if (multiInstanceAssignmentResolver != null) {
            return multiInstanceAssignmentResolver;
        }
        return new MultiInstanceAssignmentResolver(
                groupMapper,
                userGroupMapper,
                roleMapper,
                userRoleMapper,
                userMapper,
                personResolverRuntimeService);
    }

    /**
     * 处理节点引用解析器，并将结果传给后续步骤。
     *
     * @return 处理后的节点引用解析器结果，供调用方继续处理
     */
    private NodeAssignmentReferenceResolver nodeReferenceResolver() {
        if (nodeReferenceResolver != null) {
            return nodeReferenceResolver;
        }
        return new NodeAssignmentReferenceResolver(objectMapper);
    }

    /**
     * 处理分配配置版本，并将结果传给后续步骤。
     *
     * @param config 配置内容，决定后续分配配置版本的处理规则
     * @return 处理后的分配配置版本结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private int assignmentConfigVersion(
            Map<String, Object> config) {
        Object raw = config.get("assignmentConfigVersion");
        if (raw == null) {
            return 1;
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "assignmentConfigVersion 必须是整数", exception);
        }
    }

    /**
     * 运行时仅接受当前已实现的显式 v2；无版本才按历史 v1 读取。
     * 即使集合变量已由客户端提供也必须先校验，防止未知版本绕过部署语义。
     *
     * @param config 配置内容，决定后续{@code supported}分配配置版本的处理规则
     * @return 校验并获取后的{@code supported}分配配置版本结果，供调用方继续处理
     */
    private int requireSupportedAssignmentConfigVersion(
            Map<String, Object> config) {
        int version;
        try {
            version = assignmentConfigVersion(config);
        } catch (IllegalArgumentException exception) {
            throw required(
                    "assignmentConfigVersion 必须是整数",
                    exception);
        }
        if (config.containsKey("assignmentConfigVersion")
                && version != 2) {
            throw required(
                    "不支持的 assignmentConfigVersion: " + version,
                    null);
        }
        return version;
    }


    /**
     * 添加CSV；结果供后续流程传递或持久化。
     *
     * @param target 目标，作为 {@code forEach} 的输入影响后续处理
     * @param raw 待添加CSV的原始输入，结果供调用方继续使用
     */
    private void addCsv(
            java.util.Set<String> target,
            Object raw) {
        if (raw instanceof java.util.Collection<?> values) {
            values.stream()
                    .map(this::text)
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .forEach(target::add);
            return;
        }
        String value = text(raw);
        if (value == null || value.isBlank()) {
            return;
        }
        for (String item : value.split(",")) {
            if (!item.isBlank()) {
                target.add(item.trim());
            }
        }
    }

    /**
     * 表示必填多实例分配处理失败；调用方可据此区分错误并终止后续操作。
     */
    private static final class RequiredMultiInstanceAssignmentException
            extends RuntimeException {

        /**
         * 初始化必填多实例分配异常，保存构造参数供后续方法使用。
         *
         * @param message 消息，保存在对象中供后续校验、查询或展示
         * @param cause 原因，保存在对象中供后续校验、查询或展示
         */
        private RequiredMultiInstanceAssignmentException(
                String message,
                Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 封装有效分配的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param sourceActivity 来源活动，保存在对象中供后续校验、查询或展示
     * @param assigneeConfig 办理人配置内容，决定后续有效分配的处理规则
     */
    private record EffectiveAssignment(
            Activity sourceActivity,
            Map<String, Object> assigneeConfig) {
    }
}
