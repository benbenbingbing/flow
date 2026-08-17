package com.workflow.process.assignment.infrastructure.flowable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.identity.resolver.PersonPrincipal;
import com.workflow.contracts.identity.resolver.PersonPrincipalType;
import com.workflow.contracts.identity.resolver.PersonResolveRequest;
import com.workflow.contracts.identity.resolver.PersonResolveUsage;
import com.workflow.process.assignment.application.PersonResolverRuntimeService;
import com.workflow.process.assignment.application.LegacyMultiInstanceAssignmentParser;
import com.workflow.process.assignment.application.LegacyMultiInstanceAssignmentParser.LegacyAssignment;
import com.workflow.process.assignment.application.NodeAssignmentReferenceResolver;
import com.workflow.process.assignment.application.NodeAssignmentReferenceResolver.ResolvedAssignment;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import com.workflow.process.engine.infrastructure.flowable.ConfiguredTaskPropertyReader;
import com.workflow.process.task.application.nextapproval.NextApproverOverride;
import com.workflow.process.task.application.nextapproval.NextApproverOverrideStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.common.engine.api.delegate.event.FlowableEntityEvent;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.UserTask;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 在用户任务创建时调用受控人员解析器分配办理人。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PersonResolverTaskAssignmentListener
        implements FlowableEventListener {

    private final ProcessVersionHistoryMapper processVersionMapper;
    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final PersonResolverRuntimeService resolverRuntimeService;
    private final ObjectMapper objectMapper;

    @Autowired
    private NextApproverOverrideStore nextApproverOverrideStore;

    /** 部署内节点引用解析器；required=false 仅兼容直接构造的旧测试。 */
    @Autowired(required = false)
    private NodeAssignmentReferenceResolver nodeReferenceResolver;

    @Override
    public void onEvent(FlowableEvent event) {
        if (event.getType() == null
                || !"TASK_CREATED".equals(event.getType().name())
                || !(event instanceof FlowableEntityEvent entityEvent)
                || !(entityEvent.getEntity() instanceof Task task)) {
            return;
        }
        try {
            assign(task);
        } catch (RequiredAssignmentException exception) {
            log.error(
                    "安全关键人员分配失败，将回滚任务创建: taskId={}, nodeId={}, message={}",
                    task.getId(),
                    task.getTaskDefinitionKey(),
                    exception.getMessage(),
                    exception);
            throw exception;
        } catch (Exception exception) {
            log.error(
                    "人员解析器分配任务失败: taskId={}, nodeId={}, message={}",
                    task.getId(),
                    task.getTaskDefinitionKey(),
                    exception.getMessage(),
                    exception);
        }
    }

    @SuppressWarnings("unchecked")
    private void assign(Task task) throws Exception {
        if (nextApproverOverrideStore != null) {
            try {
                NextApproverOverride override =
                        nextApproverOverrideStore.consumeForTask(task);
                if (override != null) {
                    if (override.usernames().isEmpty()) {
                        throw new IllegalStateException(
                                "覆盖审批人列表为空");
                    }
                    clearDefaultAssignments(task.getId());
                    if ("CANDIDATE".equalsIgnoreCase(
                            override.assignmentMode())) {
                        override.usernames().forEach(user ->
                                taskService.addCandidateUser(
                                        task.getId(), user));
                    } else {
                        taskService.setAssignee(
                                task.getId(), override.usernames().get(0));
                        override.usernames().stream()
                                .skip(1)
                                .forEach(user -> taskService.addCandidateUser(
                                        task.getId(), user));
                    }
                    log.info(
                            "已消费下一审批人覆盖: sourceTaskId={}, taskId={}, nodeId={}, assignmentMode={}, userCount={}",
                            override.sourceTaskId(),
                            task.getId(),
                            task.getTaskDefinitionKey(),
                            override.assignmentMode(),
                            override.usernames().size());
                    return;
                }
            } catch (RuntimeException exception) {
                throw new OverrideApplicationException(
                        "应用下一审批人覆盖失败", exception);
            }
        }

        String processDefinitionId = task.getProcessDefinitionId();
        ProcessDefinition definition = repositoryService
                .createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId)
                .singleResult();
        if (definition == null) {
            log.warn(
                    "人员解析器跳过未知流程定义: taskId={}, processDefinitionId={}",
                    task.getId(),
                    processDefinitionId);
            return;
        }
        String processKey = definition.getKey();
        String processConfigId = publishedProcessConfigId(definition);
        BpmnModel bpmnModel = repositoryService.getBpmnModel(
                processDefinitionId);
        FlowElement element = bpmnModel == null
                || bpmnModel.getMainProcess() == null
                ? null
                : bpmnModel.getMainProcess().getFlowElement(
                        task.getTaskDefinitionKey(), true);
        if (!(element instanceof UserTask userTask)) {
            return;
        }
        if (userTask.hasMultiInstanceLoopCharacteristics()) {
            return;
        }
        String configDocument = ConfiguredTaskPropertyReader.read(
                userTask, "assigneeConfig");
        if (!StringUtils.hasText(configDocument)) {
            return;
        }
        Map<String, Object> assigneeConfig;
        try {
            assigneeConfig = objectMapper.readValue(
                    configDocument, Map.class);
        } catch (Exception exception) {
            if (NextApproverAssignmentRequirement.requiresFailClosed(
                    configDocument)) {
                throw new RequiredAssignmentException(
                        "安全关键节点人员配置无法解析",
                        exception);
            }
            throw exception;
        }
        int assignmentVersion;
        try {
            assignmentVersion = assignmentConfigVersion(assigneeConfig);
        } catch (IllegalArgumentException exception) {
            throw new RequiredAssignmentException(
                    "assignmentConfigVersion 必须是整数",
                    exception);
        }
        if (assigneeConfig.containsKey("assignmentConfigVersion")
                && assignmentVersion != 2) {
            throw new RequiredAssignmentException(
                    "不支持的 assignmentConfigVersion: "
                            + assignmentVersion,
                    null);
        }
        boolean visible = NextApproverAssignmentRequirement.isRequired(
                assigneeConfig);
        boolean editable = NextApproverAssignmentRequirement.isEditable(
                assigneeConfig);
        if (editable && !visible) {
            throw new RequiredAssignmentException(
                    "下一审批人可修改时必须同时允许展示",
                    null);
        }
        boolean strictAssignment = assignmentVersion == 2
                || visible
                || editable;
        ResolvedAssignment resolvedAssignment;
        try {
            resolvedAssignment = nodeReferenceResolver().resolve(
                    bpmnModel, userTask, assigneeConfig);
        } catch (RuntimeException exception) {
            if (strictAssignment
                    || NodeAssignmentReferenceResolver.isNodeReference(
                    assigneeConfig)) {
                throw new RequiredAssignmentException(
                        "安全关键节点无法解析审批人节点引用",
                        exception);
            }
            throw exception;
        }
        Map<String, Object> effectiveConfig =
                resolvedAssignment.assigneeConfig();
        int effectiveVersion;
        try {
            effectiveVersion = assignmentConfigVersion(effectiveConfig);
        } catch (IllegalArgumentException exception) {
            throw new RequiredAssignmentException(
                    "被引用节点 assignmentConfigVersion 必须是整数",
                    exception);
        }
        if (effectiveConfig.containsKey("assignmentConfigVersion")
                && effectiveVersion != 2) {
            throw new RequiredAssignmentException(
                    "被引用节点使用不支持的 assignmentConfigVersion: "
                            + effectiveVersion,
                    null);
        }
        String type = normalizeAssignmentType(
                effectiveConfig.get("assigneeType"));

        if (resolvedAssignment.referenced()) {
            try {
                Map<String, Object> variables = runtimeService.getVariables(
                        task.getProcessInstanceId());
                List<String> users = resolveReferencedUsers(
                        task,
                        processConfigId,
                        resolvedAssignment.sourceTask(),
                        effectiveConfig,
                        effectiveVersion,
                        variables);
                if (users.isEmpty()) {
                    throw new IllegalStateException(
                            "被引用节点没有可用办理人: "
                                    + resolvedAssignment.sourceTask().getId());
                }
                applyResolvedUsers(
                        task,
                        users,
                        NodeAssignmentReferenceResolver.assignmentMode(
                                userTask,
                                resolvedAssignment.sourceTask(),
                                effectiveConfig));
                log.info(
                        "按部署节点引用分配任务: taskId={}, nodeId={}, referencedNodeId={}, userCount={}",
                        task.getId(),
                        task.getTaskDefinitionKey(),
                        resolvedAssignment.sourceTask().getId(),
                        users.size());
                return;
            } catch (RuntimeException exception) {
                throw new RequiredAssignmentException(
                        "安全关键节点无法应用被引用节点的办理人规则",
                        exception);
            }
        }
        if (!"interface".equalsIgnoreCase(type)
                && !"resolver".equalsIgnoreCase(type)) {
            // 覆盖已在方法入口优先消费；此处仍无实际 assignee/candidate
            // 表示首节点或不可预览路径没有任何人可办理，必须回滚任务创建。
            if (strictAssignment) {
                try {
                    if (!hasCurrentAssignment(task)) {
                        throw new RequiredAssignmentException(
                                "安全关键节点没有有效的实际办理人或候选人",
                                null);
                    }
                } catch (RequiredAssignmentException exception) {
                    throw exception;
                } catch (RuntimeException exception) {
                    throw new RequiredAssignmentException(
                            "校验安全关键节点实际办理人失败",
                            exception);
                }
            }
            return;
        }
        String resolverCode = firstText(
                effectiveConfig.get("resolverCode"),
                effectiveConfig.get("interfaceName"));
        try {
            if (!resolverRuntimeService.supportsConfigured(
                    resolverCode, PersonResolveUsage.ASSIGNEE)) {
                throw new IllegalArgumentException(
                        "人员接口未注册、未启用或不支持办理人用途: "
                                + resolverCode);
            }

            Map<String, Object> variables =
                    runtimeService.getVariables(
                            task.getProcessInstanceId());
            Map<String, Object> extraParams =
                    mapValue(effectiveConfig.get("extraParams"));
            List<String> users = resolverRuntimeService.resolveUsernames(
                    resolverCode,
                    new PersonResolveRequest(
                            1,
                            text(variables.get("traceId")),
                            "ASSIGNEE:" + task.getId(),
                            PersonResolveUsage.ASSIGNEE,
                            processConfigId,
                            task.getProcessDefinitionId(),
                            task.getProcessInstanceId(),
                            firstText(
                                    variables.get("businessKey"),
                                    variables.get("entityDataId")),
                            task.getTaskDefinitionKey(),
                            task.getName(),
                            task.getId(),
                            text(variables.get("entityCode")),
                            text(variables.get("entityDataId")),
                            firstText(
                                    variables.get("startUserId"),
                                    variables.get("submitterId"),
                                    variables.get("initiator")),
                            null,
                            variables,
                            mapValue(variables.get("entityData")),
                            extraParams));
            if (users.isEmpty()) {
                throw new IllegalStateException(
                        "人员接口未返回可用办理人: " + resolverCode);
            }
            taskService.setAssignee(task.getId(), users.get(0));
            users.stream()
                    .skip(1)
                    .forEach(user -> taskService.addCandidateUser(
                            task.getId(), user));
            log.info(
                    "人员解析器分配任务完成: resolverCode={}, processKey={}, processInstanceId={}, taskId={}, nodeId={}, assignee={}, candidateCount={}",
                    resolverCode,
                    processKey,
                    task.getProcessInstanceId(),
                    task.getId(),
                    task.getTaskDefinitionKey(),
                    users.get(0),
                    Math.max(0, users.size() - 1));
        } catch (RuntimeException exception) {
            if (strictAssignment) {
                throw new RequiredAssignmentException(
                        "安全关键节点无法解析默认办理人",
                        exception);
            }
            throw exception;
        }
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

    /**
     * 将被引用节点的 legacy/v2 静态规则或受控解析器展开为本地用户名。
     * 解析器用途由当前任务决定为 ASSIGNEE，不能继承源节点的多实例属性。
     */
    private List<String> resolveReferencedUsers(
            Task currentTask,
            String processConfigId,
            UserTask sourceTask,
            Map<String, Object> config,
            int assignmentVersion,
            Map<String, Object> variables) {
        LegacyAssignment legacy =
                LegacyMultiInstanceAssignmentParser.parse(config);
        if (assignmentVersion < 2
                && sourceTask.hasMultiInstanceLoopCharacteristics()
                && legacy.effective()) {
            if (legacy.resolver()) {
                return resolveReferencedResolverUsers(
                        currentTask,
                        processConfigId,
                        legacy.resolverCode(),
                        legacy.resolverExtraParams(),
                        variables);
            }
            List<PersonPrincipal> principals = new ArrayList<>();
            legacy.userKeys().forEach(value -> principals.add(
                    PersonPrincipal.user(value)));
            legacy.groupKeys().forEach(value -> principals.add(
                    new PersonPrincipal(PersonPrincipalType.GROUP, value)));
            legacy.roleKeys().forEach(value -> principals.add(
                    new PersonPrincipal(PersonPrincipalType.ROLE, value)));
            return resolverRuntimeService.resolvePrincipalUsernames(
                    principals);
        }

        String type = normalizeAssignmentType(
                config.get("assigneeType"));
        if ("resolver".equals(type)) {
            return resolveReferencedResolverUsers(
                    currentTask,
                    processConfigId,
                    firstText(
                            config.get("resolverCode"),
                            config.get("interfaceName")),
                    mapValue(config.get("extraParams")),
                    variables);
        }
        if ("expression".equals(type)) {
            throw new IllegalArgumentException(
                    "被引用节点使用无法安全枚举的表达式办理人: "
                            + sourceTask.getId());
        }
        if (NodeAssignmentReferenceResolver.isNodeReference(config)) {
            throw new IllegalArgumentException(
                    "被引用节点引用链未完全解析: " + sourceTask.getId());
        }

        List<PersonPrincipal> principals = new ArrayList<>();
        if (literalSourceAssignment(sourceTask.getAssignee())) {
            principals.add(PersonPrincipal.user(
                    sourceTask.getAssignee().trim()));
        } else if (StringUtils.hasText(sourceTask.getAssignee())
                && !sourceTask.hasMultiInstanceLoopCharacteristics()) {
            throw new IllegalArgumentException(
                    "被引用节点的 BPMN assignee 是动态表达式: "
                            + sourceTask.getId());
        }
        if ("user".equals(type) || "candidate".equals(type)) {
            addPrincipals(
                    principals,
                    config.get("assigneeValue"),
                    PersonPrincipalType.USER);
            addPrincipals(
                    principals,
                    config.get("candidateUsers"),
                    PersonPrincipalType.USER);
        } else if ("group".equals(type)) {
            addPrincipals(
                    principals,
                    config.get("assigneeValue"),
                    PersonPrincipalType.GROUP);
        } else if ("role".equals(type)) {
            addPrincipals(
                    principals,
                    config.get("assigneeValue"),
                    PersonPrincipalType.ROLE);
        }
        if (sourceTask.getCandidateUsers() != null) {
            for (String value : sourceTask.getCandidateUsers()) {
                requireLiteralReferenceValue(
                        sourceTask, value, "candidateUsers");
                if (StringUtils.hasText(value)) {
                    principals.add(PersonPrincipal.user(value.trim()));
                }
            }
        }
        if (sourceTask.getCandidateGroups() != null) {
            for (String value : sourceTask.getCandidateGroups()) {
                requireLiteralReferenceValue(
                        sourceTask, value, "candidateGroups");
                if (StringUtils.hasText(value)) {
                    addGroupOrRolePrincipal(principals, value.trim());
                }
            }
        }
        addGroupOrRolePrincipals(
                principals, config.get("candidateGroups"));
        return resolverRuntimeService.resolvePrincipalUsernames(
                principals);
    }

    private List<String> resolveReferencedResolverUsers(
            Task task,
            String processConfigId,
            String resolverCode,
            Map<String, Object> extraParams,
            Map<String, Object> variables) {
        resolverRuntimeService.requireConfigured(
                resolverCode, PersonResolveUsage.ASSIGNEE);
        return resolverRuntimeService.resolveUsernames(
                resolverCode,
                new PersonResolveRequest(
                        1,
                        text(variables.get("traceId")),
                        "ASSIGNEE:" + task.getId(),
                        PersonResolveUsage.ASSIGNEE,
                        processConfigId,
                        task.getProcessDefinitionId(),
                        task.getProcessInstanceId(),
                        firstText(
                                variables.get("businessKey"),
                                variables.get("entityDataId")),
                        task.getTaskDefinitionKey(),
                        task.getName(),
                        task.getId(),
                        text(variables.get("entityCode")),
                        text(variables.get("entityDataId")),
                        firstText(
                                variables.get("startUserId"),
                                variables.get("submitterId"),
                                variables.get("initiator")),
                        null,
                        variables,
                        mapValue(variables.get("entityData")),
                        extraParams));
    }

    private void applyResolvedUsers(
            Task task,
            List<String> users,
            String assignmentMode) {
        clearDefaultAssignments(task.getId());
        if ("CANDIDATE".equals(assignmentMode)) {
            users.forEach(user -> taskService.addCandidateUser(
                    task.getId(), user));
            return;
        }
        taskService.setAssignee(task.getId(), users.get(0));
        users.stream().skip(1).forEach(user ->
                taskService.addCandidateUser(task.getId(), user));
    }

    private void addPrincipals(
            List<PersonPrincipal> principals,
            Object raw,
            PersonPrincipalType type) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        addCsv(values, raw);
        for (String value : values) {
            principals.add(new PersonPrincipal(type, value));
        }
    }

    private void addGroupOrRolePrincipals(
            List<PersonPrincipal> principals,
            Object raw) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        addCsv(values, raw);
        values.forEach(value -> addGroupOrRolePrincipal(
                principals, value));
    }

    private void addGroupOrRolePrincipal(
            List<PersonPrincipal> principals,
            String value) {
        if (value.startsWith("ROLE_")) {
            principals.add(new PersonPrincipal(
                    PersonPrincipalType.ROLE, value.substring(5)));
        } else {
            principals.add(new PersonPrincipal(
                    PersonPrincipalType.GROUP, value));
        }
    }

    private void addCsv(Set<String> target, Object raw) {
        if (raw instanceof Iterable<?> values) {
            for (Object value : values) {
                String item = text(value);
                if (StringUtils.hasText(item)) {
                    target.add(item.trim());
                }
            }
            return;
        }
        String value = text(raw);
        if (!StringUtils.hasText(value)) {
            return;
        }
        for (String item : value.split(",")) {
            if (StringUtils.hasText(item)) {
                target.add(item.trim());
            }
        }
    }

    private boolean literalSourceAssignment(String value) {
        return StringUtils.hasText(value)
                && !value.contains("${")
                && !value.contains("#{");
    }

    private void requireLiteralReferenceValue(
            UserTask sourceTask,
            String value,
            String field) {
        if (StringUtils.hasText(value)
                && (value.contains("${") || value.contains("#{"))) {
            throw new IllegalArgumentException(
                    "被引用节点的 BPMN " + field
                            + " 是动态表达式: " + sourceTask.getId());
        }
    }

    private String normalizeAssignmentType(Object value) {
        String type = text(value);
        if (!StringUtils.hasText(type)) {
            return "";
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        return "interface".equals(normalized)
                ? "resolver" : normalized;
    }

    private NodeAssignmentReferenceResolver nodeReferenceResolver() {
        if (nodeReferenceResolver != null) {
            return nodeReferenceResolver;
        }
        return new NodeAssignmentReferenceResolver(objectMapper);
    }

    /**
     * 覆盖人员必须替换而非叠加 BPMN 创建任务时生成的默认分配。
     */
    private void clearDefaultAssignments(String taskId) {
        taskService.setAssignee(taskId, null);
        List<IdentityLink> identityLinks =
                taskService.getIdentityLinksForTask(taskId);
        if (identityLinks == null) {
            return;
        }
        // Flowable 的实现可能返回由命令上下文管理的 live list；循环内删除
        // identity link 会同步修改该集合，因此必须先建立快照。
        for (IdentityLink identityLink : new ArrayList<>(identityLinks)) {
            if (identityLink == null
                    || !"candidate".equalsIgnoreCase(
                    identityLink.getType())) {
                continue;
            }
            if (StringUtils.hasText(identityLink.getUserId())) {
                taskService.deleteCandidateUser(
                        taskId, identityLink.getUserId());
            }
            if (StringUtils.hasText(identityLink.getGroupId())) {
                taskService.deleteCandidateGroup(
                        taskId, identityLink.getGroupId());
            }
        }
    }

    /**
     * 判断 Flowable 已创建的任务是否包含实际办理人或候选身份。
     * 仅检查运行时事实，不信任配置 JSON 中尚未落到任务上的默认值。
     */
    private boolean hasCurrentAssignment(Task task) {
        if (StringUtils.hasText(task.getAssignee())) {
            return !resolverRuntimeService.resolvePrincipalUsernames(
                    List.of(PersonPrincipal.user(
                            task.getAssignee()))).isEmpty();
        }
        List<IdentityLink> identityLinks =
                taskService.getIdentityLinksForTask(task.getId());
        if (identityLinks == null) {
            return false;
        }
        List<PersonPrincipal> principals = new ArrayList<>();
        for (IdentityLink identityLink : identityLinks) {
            if (identityLink == null
                    || !"candidate".equalsIgnoreCase(
                    identityLink.getType())) {
                continue;
            }
            if (StringUtils.hasText(identityLink.getUserId())) {
                principals.add(PersonPrincipal.user(
                        identityLink.getUserId()));
            }
            if (StringUtils.hasText(identityLink.getGroupId())) {
                String group = identityLink.getGroupId().trim();
                if (group.startsWith("ROLE_")) {
                    principals.add(new PersonPrincipal(
                            PersonPrincipalType.ROLE,
                            group.substring(5)));
                } else {
                    principals.add(new PersonPrincipal(
                            PersonPrincipalType.GROUP, group));
                }
            }
        }
        return !resolverRuntimeService.resolvePrincipalUsernames(
                principals).isEmpty();
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
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return null;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int assignmentConfigVersion(Map<String, Object> config) {
        Object value = config.get("assignmentConfigVersion");
        if (value == null) {
            return 1;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "assignmentConfigVersion 必须是整数",
                    exception);
        }
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

    private static class RequiredAssignmentException
            extends RuntimeException {

        private RequiredAssignmentException(
                String message,
                Throwable cause) {
            super(message, cause);
        }
    }

    private static final class OverrideApplicationException
            extends RequiredAssignmentException {

        private OverrideApplicationException(
                String message,
                Throwable cause) {
            super(message, cause);
        }
    }
}
