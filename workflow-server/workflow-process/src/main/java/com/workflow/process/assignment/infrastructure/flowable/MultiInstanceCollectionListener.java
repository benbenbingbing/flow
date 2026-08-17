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
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
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
            boolean configParsed = false;
            boolean required = false;
            boolean visible = false;
            boolean editable = false;
            try {
                String varName = collectionVariable(
                        activity.getLoopCharacteristics());
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
                List<String> userIds = resolvePublishedUsers(
                        processConfigId,
                        activity.getId(),
                        activity.getName(),
                        effective.assigneeConfig(),
                        variables,
                        null,
                        processDefinitionId);
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
                    activityEvent.getProcessDefinitionId());
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
     * @throws Exception 查询流程实例或解析配置失败时抛出
     */
    private void prepareMultiInstanceCollection(
            String processInstanceId,
            String activityId,
            String eventProcessDefinitionId) throws Exception {
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
            String varName = collectionVariable(
                    deployedActivity.getLoopCharacteristics());
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
                    processDefinitionId);
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

    private boolean hasStagedOverride(
            String processInstanceId,
            String activityId) {
        return nextApproverOverrideStore != null
                && nextApproverOverrideStore.hasStagedOverride(
                        processInstanceId, activityId);
    }

    private RequiredMultiInstanceAssignmentException required(
            String message,
            Throwable cause) {
        return new RequiredMultiInstanceAssignmentException(
                message, cause);
    }

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
     */
    private EffectiveAssignment effectiveAssignment(
            BpmnModel model,
            Activity currentActivity,
            Map<String, Object> currentConfig) throws Exception {
        if (!(currentActivity instanceof UserTask currentTask)
                || !NodeAssignmentReferenceResolver.isNodeReference(
                currentConfig)) {
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

    private boolean literal(String value) {
        return StringUtils.hasText(value)
                && !value.contains("${")
                && !value.contains("#{");
    }

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

    @Override
    public boolean isFailOnException() {
        return true;
    }

    @Override
    public String getOnTransaction() {
        return null;
    }

    @Override
    public boolean isFireOnTransactionLifecycleEvent() {
        return false;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?>
                ? (Map<String, Object>) value
                : Map.of();
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String text = text(value);
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String collectionVariable(
            MultiInstanceLoopCharacteristics loop) {
        if (loop == null) {
            return null;
        }
        String expression = loop.getInputDataItem();
        if (expression == null || expression.isBlank()) {
            expression = loop.getCollectionString();
        }
        if (expression == null || expression.isBlank()) {
            return null;
        }
        String value = expression.trim();
        if ((value.startsWith("${") || value.startsWith("#{"))
                && value.endsWith("}")) {
            value = value.substring(2, value.length() - 1).trim();
        }
        return value.matches("[A-Za-z_][A-Za-z0-9_]*")
                ? value : null;
    }

    /** 将人员展开委托给独立组件，监听器只保留版本门禁和变量编排。 */
    private List<String> resolvePublishedUsers(
            String processConfigId,
            String nodeId,
            String nodeName,
            Map<String, Object> assigneeConfig,
            Map<String, Object> variables,
            String processInstanceId,
            String processDefinitionId) {
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
                assignmentVersion);
    }

    /**
     * 兼容直接构造监听器的轻量测试；生产环境使用 Spring 注入的共享解析器。
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

    private NodeAssignmentReferenceResolver nodeReferenceResolver() {
        if (nodeReferenceResolver != null) {
            return nodeReferenceResolver;
        }
        return new NodeAssignmentReferenceResolver(objectMapper);
    }

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

    private static final class RequiredMultiInstanceAssignmentException
            extends RuntimeException {

        private RequiredMultiInstanceAssignmentException(
                String message,
                Throwable cause) {
            super(message, cause);
        }
    }

    private record EffectiveAssignment(
            Activity sourceActivity,
            Map<String, Object> assigneeConfig) {
    }
}
