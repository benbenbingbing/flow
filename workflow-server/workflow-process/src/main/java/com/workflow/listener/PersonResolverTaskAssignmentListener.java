package com.workflow.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.identity.resolver.PersonResolveRequest;
import com.workflow.contracts.identity.resolver.PersonResolveUsage;
import com.workflow.entity.NodeConfig;
import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.mapper.NodeConfigMapper;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import com.workflow.service.PersonResolverRuntimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.event.impl.FlowableEntityEventImpl;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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

    private final ProcessDefinitionConfigMapper processMapper;
    private final NodeConfigMapper nodeMapper;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final PersonResolverRuntimeService resolverRuntimeService;
    private final ObjectMapper objectMapper;

    @Override
    public void onEvent(FlowableEvent event) {
        if (event.getType() == null
                || !"TASK_CREATED".equals(event.getType().name())
                || !(event instanceof FlowableEntityEventImpl entityEvent)
                || !(entityEvent.getEntity() instanceof Task task)) {
            return;
        }
        try {
            assign(task);
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
        String processKey = processKey(task.getProcessDefinitionId());
        ProcessDefinitionConfig process =
                processMapper.findByProcessKey(processKey).orElse(null);
        if (process == null) {
            return;
        }
        NodeConfig node = nodeMapper.selectByNodeIdAndProcessId(
                task.getTaskDefinitionKey(), process.getId());
        if (node == null || !StringUtils.hasText(node.getConfigJson())) {
            return;
        }
        Map<String, Object> nodeConfig =
                objectMapper.readValue(node.getConfigJson(), Map.class);
        if (Boolean.TRUE.equals(nodeConfig.get("multiInstance"))) {
            return;
        }
        Object raw = nodeConfig.get("assigneeConfig");
        if (!(raw instanceof Map<?, ?> rawConfig)) {
            return;
        }
        Map<String, Object> assigneeConfig =
                (Map<String, Object>) rawConfig;
        String type = text(assigneeConfig.get("assigneeType"));
        if (!"interface".equalsIgnoreCase(type)
                && !"resolver".equalsIgnoreCase(type)) {
            return;
        }
        String resolverCode = firstText(
                assigneeConfig.get("resolverCode"),
                assigneeConfig.get("interfaceName"));
        if (!resolverRuntimeService.supports(
                resolverCode, PersonResolveUsage.ASSIGNEE)) {
            throw new IllegalArgumentException(
                    "人员接口未注册、未启用或不支持办理人用途: "
                            + resolverCode);
        }

        Map<String, Object> variables =
                runtimeService.getVariables(task.getProcessInstanceId());
        Map<String, Object> extraParams =
                mapValue(assigneeConfig.get("extraParams"));
        List<String> users = resolverRuntimeService.resolveUsernames(
                resolverCode,
                new PersonResolveRequest(
                        1,
                        text(variables.get("traceId")),
                        "ASSIGNEE:" + task.getId(),
                        PersonResolveUsage.ASSIGNEE,
                        process.getId(),
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
                .forEach(user ->
                        taskService.addCandidateUser(task.getId(), user));
    }

    private String processKey(String processDefinitionId) {
        int delimiter = processDefinitionId == null
                ? -1
                : processDefinitionId.indexOf(':');
        return delimiter > 0
                ? processDefinitionId.substring(0, delimiter)
                : processDefinitionId;
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
        return false;
    }

    @Override
    public String getOnTransaction() {
        return null;
    }

    @Override
    public boolean isFireOnTransactionLifecycleEvent() {
        return false;
    }
}
