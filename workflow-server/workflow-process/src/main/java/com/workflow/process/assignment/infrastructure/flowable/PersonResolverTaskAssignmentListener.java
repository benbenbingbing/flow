package com.workflow.process.assignment.infrastructure.flowable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.identity.resolver.PersonResolveRequest;
import com.workflow.contracts.identity.resolver.PersonResolveUsage;
import com.workflow.process.assignment.application.PersonResolverRuntimeService;
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
import java.util.List;
import java.util.Map;

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
            if (NextApproverAssignmentRequirement.declaresSelection(
                    configDocument)) {
                throw new RequiredAssignmentException(
                        "已声明下一审批人展示的节点人员配置无法解析",
                        exception);
            }
            throw exception;
        }
        String type = text(assigneeConfig.get("assigneeType"));
        if (!"interface".equalsIgnoreCase(type)
                && !"resolver".equalsIgnoreCase(type)) {
            return;
        }
        String resolverCode = firstText(
                assigneeConfig.get("resolverCode"),
                assigneeConfig.get("interfaceName"));
        boolean visibleNextApproverSelection =
                NextApproverAssignmentRequirement.isRequired(
                        assigneeConfig);
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
                    mapValue(assigneeConfig.get("extraParams"));
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
            if (visibleNextApproverSelection) {
                throw new RequiredAssignmentException(
                        "已启用下一审批人展示的节点无法解析默认办理人",
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
