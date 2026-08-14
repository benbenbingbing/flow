package com.workflow.process.task.application.nextapproval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.identity.resolver.PersonResolveUsage;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.process.audit.infrastructure.persistence.mapper.ProcessOperationLogMapper;
import com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog;
import com.workflow.process.task.api.request.NextApprovalPreviewRequest;
import com.workflow.process.task.api.request.NextApproverSelectionRequest;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.Activity;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.engine.RuntimeService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 提交前重验人工覆盖，并通过 Flowable 流程变量暂存一次性指令。
 */
@Service
@RequiredArgsConstructor
public class NextApproverOverrideService {

    public static final String VARIABLE_NAME =
            NextApproverOverrideStore.VARIABLE_NAME;

    private final RuntimeService runtimeService;
    private final NextApprovalRouteService routeService;
    private final NextApproverCandidateService candidateService;
    private final ProcessOperationLogMapper operationLogMapper;
    private final SysUserService sysUserService;
    private final ObjectMapper objectMapper;

    public void validateAndStage(
            Task task,
            String action,
            String actionLabel,
            String comment,
            String submittedScopeKey,
            List<NextApproverSelectionRequest> selections) {
        validateAndStage(
                task,
                action,
                actionLabel,
                comment,
                submittedScopeKey,
                selections,
                false);
    }

    public void validateAndStage(
            Task task,
            String action,
            String actionLabel,
            String comment,
            String submittedScopeKey,
            List<NextApproverSelectionRequest> selections,
            boolean previewWasDeferred) {
        List<NextApproverSelectionRequest> requestedSelections =
                selections == null ? List.of() : selections;
        NextApprovalPreviewRequest request =
                new NextApprovalPreviewRequest();
        request.setAction(action);
        request.setActionLabel(actionLabel);
        request.setComment(comment);
        NextApprovalResolution resolution = routeService.resolve(
                task, request, false);
        if (resolution.status()
                == com.workflow.process.task.api.response.NextApprovalPreviewStatus.DEFERRED) {
            if (!requestedSelections.isEmpty()) {
                throw conflict(
                        "NEXT_APPROVAL_SCOPE_CHANGED",
                        "下一审批节点需等待引擎运行后确定，不能提交人工覆盖");
            }
            return;
        }
        if (!resolution.ready()) {
            throw conflict(
                    "NEXT_APPROVER_RESOLUTION_FAILED",
                    resolution.message() == null
                            ? "下一审批节点无法安全解析"
                            : resolution.message());
        }
        if (!requestedSelections.isEmpty()
                && (!StringUtils.hasText(submittedScopeKey)
                || !submittedScopeKey.equals(resolution.scopeKey()))) {
            throw conflict(
                    "NEXT_APPROVAL_SCOPE_CHANGED",
                    "下一审批路径或人员范围已变化，请刷新后重试");
        }

        Map<String, NextApprovalTarget> targets = new LinkedHashMap<>();
        resolution.targets().forEach(target -> targets.put(
                target.userTask().getId(), target));
        Map<String, List<String>> hitMultiInstanceTargets =
                new LinkedHashMap<>();
        for (NextApprovalTarget target : resolution.targets()) {
            if (!target.userTask()
                    .hasMultiInstanceLoopCharacteristics()) {
                continue;
            }
            String variableName = multiInstanceCollectionVariable(
                    target.userTask());
            if (StringUtils.hasText(variableName)) {
                hitMultiInstanceTargets.computeIfAbsent(
                                variableName,
                                ignored -> new ArrayList<>())
                        .add(target.userTask().getId());
            }
        }
        Set<String> selectedNodes = new LinkedHashSet<>();
        Map<String, NextApproverSelectionRequest> selectionsByNode =
                new LinkedHashMap<>();
        Map<String, List<String>> defaultsByNode =
                new LinkedHashMap<>();
        for (NextApproverSelectionRequest selection : requestedSelections) {
            if (selection == null
                    || !StringUtils.hasText(selection.getNodeId())
                    || selectionsByNode.putIfAbsent(
                            selection.getNodeId(), selection) != null) {
                throw conflict(
                        "NEXT_APPROVAL_TARGET_INVALID",
                        "下一审批节点为空或重复");
            }
        }

        for (NextApprovalTarget target : resolution.targets()) {
            NextApproverSelectionPolicy policy = target.selectionPolicy();
            if (!policy.visible()) {
                continue;
            }
            List<com.workflow.process.task.api.response.NextApproverCandidateDTO>
                    defaults;
            try {
                defaults = candidateService.defaultAssignees(
                        resolution, target);
            } catch (RuntimeException exception) {
                if (previewWasDeferred
                        && policy.editable()
                        && !selectionsByNode.containsKey(
                        target.userTask().getId())) {
                    throw deferredDefaultRequired(
                            target,
                            "默认审批人解析失败: "
                                    + safeMessage(exception));
                }
                throw conflict(
                        "NEXT_APPROVER_RESOLUTION_FAILED",
                        "下一节点默认审批人解析失败: "
                                + target.userTask().getId()
                                + ", "
                                + safeMessage(exception));
            }
            defaultsByNode.put(
                    target.userTask().getId(),
                    defaults.stream()
                            .map(com.workflow.process.task.api.response.NextApproverCandidateDTO::getUsername)
                            .toList());
            if (defaults.isEmpty()
                    && (!policy.editable()
                    || !selectionsByNode.containsKey(
                    target.userTask().getId()))) {
                if (previewWasDeferred && policy.editable()) {
                    throw deferredDefaultRequired(
                            target,
                            "未解析到启用且未删除的默认审批人");
                }
                throw conflict(
                        "NEXT_APPROVER_RESOLUTION_FAILED",
                        "下一节点没有可用默认审批人: "
                                + target.userTask().getId());
            }
        }

        Map<String, Object> overrides = currentOverrides(
                task.getProcessInstanceId());
        Map<String, List<String>> multiInstanceVariables =
                new LinkedHashMap<>();
        Map<String, String> multiInstanceVariableTargets =
                new LinkedHashMap<>();
        List<AuditEntry> audits = new ArrayList<>();
        for (NextApproverSelectionRequest selection : requestedSelections) {
            if (selection == null
                    || !StringUtils.hasText(selection.getNodeId())) {
                throw conflict(
                        "NEXT_APPROVAL_TARGET_INVALID",
                        "下一审批节点不能为空");
            }
            if (!selectedNodes.add(selection.getNodeId())) {
                throw conflict(
                        "NEXT_APPROVAL_TARGET_INVALID",
                        "下一审批节点重复选择: " + selection.getNodeId());
            }
            NextApprovalTarget target = targets.get(selection.getNodeId());
            if (target == null) {
                throw conflict(
                        "NEXT_APPROVAL_TARGET_INVALID",
                        "所选节点不属于当前命中的下一审批路径: "
                                + selection.getNodeId());
            }
            NextApproverSelectionPolicy policy = target.selectionPolicy();
            if (!policy.visible() || !policy.editable()) {
                throw conflict(
                        "NEXT_APPROVAL_TARGET_INVALID",
                        "下一节点不允许修改审批人: "
                                + selection.getNodeId());
            }
            List<String> requestedKeys = normalizedKeys(
                    selection.getUserKeys());
            if (requestedKeys.isEmpty()) {
                throw conflict(
                        "NEXT_APPROVER_CARDINALITY_INVALID",
                        "下一节点审批人不能为空: "
                                + selection.getNodeId());
            }
            if (!policy.multiple() && requestedKeys.size() != 1) {
                throw conflict(
                        "NEXT_APPROVER_CARDINALITY_INVALID",
                        "下一节点只允许选择一名审批人: "
                                + selection.getNodeId());
            }
            Map<String, String> allowed = new LinkedHashMap<>();
            try {
                for (SysUser user : candidateService.resolveAllowed(
                        resolution,
                        target,
                        PersonResolveUsage.CANDIDATE)) {
                    allowed.put(user.getUsername(), user.getUsername());
                    allowed.put(user.getId(), user.getUsername());
                }
            } catch (RuntimeException exception) {
                throw conflict(
                        "NEXT_APPROVER_RESOLUTION_FAILED",
                        "下一节点候选审批人解析失败: "
                                + safeMessage(exception));
            }
            List<String> usernames = new ArrayList<>();
            for (String key : requestedKeys) {
                String username = allowed.get(key);
                if (!StringUtils.hasText(username)) {
                    throw conflict(
                            "NEXT_APPROVER_OUT_OF_SCOPE",
                            "所选审批人已停用或不在允许范围内: " + key);
                }
                if (!usernames.contains(username)) {
                    usernames.add(username);
                }
            }
            if ("MULTI_INSTANCE".equals(policy.assignmentMode())) {
                String variableName = multiInstanceCollectionVariable(
                        target.userTask());
                if (!StringUtils.hasText(variableName)) {
                    throw conflict(
                            "NEXT_APPROVER_RESOLUTION_FAILED",
                            "多实例下一节点缺少集合变量: "
                                    + target.userTask().getId());
                }
                List<String> variableTargets =
                        hitMultiInstanceTargets.getOrDefault(
                                variableName, List.of());
                if (variableTargets.size() > 1) {
                    throw conflict(
                            "NEXT_APPROVER_RESOLUTION_FAILED",
                            "命中的多个多实例节点共用集合变量 "
                                    + variableName
                                    + ": "
                                    + String.join(", ", variableTargets));
                }
                String previousTarget = multiInstanceVariableTargets.putIfAbsent(
                        variableName, target.userTask().getId());
                if (previousTarget != null) {
                    throw conflict(
                            "NEXT_APPROVER_RESOLUTION_FAILED",
                            "多个命中的多实例节点共用了集合变量 "
                                    + variableName
                                    + ": "
                                    + previousTarget
                                    + ", "
                                    + target.userTask().getId());
                }
                multiInstanceVariables.put(
                        variableName, List.copyOf(usernames));
            } else {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("sourceTaskId", task.getId());
                entry.put("targetNodeId", target.userTask().getId());
                entry.put("assignmentMode", policy.assignmentMode());
                entry.put("usernames", List.copyOf(usernames));
                entry.put("scopeKey", resolution.scopeKey());
                entry.put("createdAt", Instant.now().toString());
                overrides.put(target.userTask().getId(), entry);
            }
            audits.add(new AuditEntry(
                    target.userTask().getId(),
                    target.userTask().getName(),
                    defaultsByNode.getOrDefault(
                            target.userTask().getId(), List.of()),
                    List.copyOf(usernames)));
        }
        multiInstanceVariables.forEach((variableName, usernames) ->
                runtimeService.setVariable(
                        task.getProcessInstanceId(),
                        variableName,
                        usernames));
        if (!overrides.isEmpty()) {
            runtimeService.setVariable(
                    task.getProcessInstanceId(),
                    VARIABLE_NAME,
                    overrides);
        }
        audits.forEach(entry -> recordAudit(task, entry));
    }

    /**
     * 在正式表单处理前重算一次预览状态。
     *
     * <p>该结果只用于识别“提交前处理无法安全预执行”的场景；正式提交仍会在
     * 权威表单处理完成后重新推导路径，不能把此结果当作 scopeKey 使用。</p>
     */
    public boolean previewIsDeferred(
            Task task,
            String action,
            String actionLabel,
            String comment,
            Map<String, Object> formData) {
        NextApprovalPreviewRequest request =
                new NextApprovalPreviewRequest();
        request.setAction(action);
        request.setActionLabel(actionLabel);
        request.setComment(comment == null ? "" : comment);
        request.setFormData(formData);
        return routeService.resolve(task, request, true).status()
                == com.workflow.process.task.api.response.NextApprovalPreviewStatus.DEFERRED;
    }

    /**
     * 判断后台延迟提交是否必须恢复为人工确认。
     *
     * <p>加签收口没有下一审批人选择界面；若命中的可编辑节点没有默认审批人，
     * 后台继续完成会创建无人任务，因此必须把源任务恢复给原办理人。无法解析的
     * 只读默认办理人仍按 fail-closed 处理；DEFERRED 则交由 Flowable 原生分配。</p>
     */
    public boolean requiresManualSelectionForDeferredCompletion(
            Task task,
            String action,
            String actionLabel,
            String comment,
            Map<String, Object> formData) {
        NextApprovalPreviewRequest request =
                new NextApprovalPreviewRequest();
        request.setAction(action);
        request.setActionLabel(actionLabel);
        request.setComment(comment == null ? "" : comment);
        request.setFormData(formData);
        NextApprovalResolution resolution = routeService.resolve(
                task, request, true);
        if (resolution.status()
                == com.workflow.process.task.api.response.NextApprovalPreviewStatus.DEFERRED) {
            return false;
        }
        if (!resolution.ready()) {
            throw conflict(
                    "NEXT_APPROVER_RESOLUTION_FAILED",
                    resolution.message() == null
                            ? "下一审批节点无法安全解析"
                            : resolution.message());
        }
        for (NextApprovalTarget target : resolution.targets()) {
            NextApproverSelectionPolicy policy =
                    target.selectionPolicy();
            if (!policy.visible()) {
                continue;
            }
            List<com.workflow.process.task.api.response.NextApproverCandidateDTO>
                    defaults;
            try {
                defaults = candidateService.defaultAssignees(
                        resolution, target);
            } catch (RuntimeException exception) {
                throw conflict(
                        "NEXT_APPROVER_RESOLUTION_FAILED",
                        "下一节点默认审批人解析失败: "
                                + target.userTask().getId()
                                + ", "
                                + safeMessage(exception));
            }
            if (defaults.isEmpty()) {
                if (policy.editable()) {
                    return true;
                }
                throw conflict(
                        "NEXT_APPROVER_RESOLUTION_FAILED",
                        "下一节点没有可用默认审批人: "
                                + target.userTask().getId());
            }
        }
        return false;
    }

    private String multiInstanceCollectionVariable(Activity activity) {
        MultiInstanceLoopCharacteristics loop =
                activity.getLoopCharacteristics();
        if (loop == null) {
            return null;
        }
        String expression = StringUtils.hasText(loop.getInputDataItem())
                ? loop.getInputDataItem()
                : loop.getCollectionString();
        if (!StringUtils.hasText(expression)) {
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

    private Map<String, Object> mapValue(Map<?, ?> value) {
        Map<String, Object> result = new LinkedHashMap<>();
        value.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private String safeMessage(RuntimeException exception) {
        return StringUtils.hasText(exception.getMessage())
                ? exception.getMessage()
                : exception.getClass().getSimpleName();
    }

    private BusinessConflictException deferredDefaultRequired(
            NextApprovalTarget target,
            String reason) {
        String nodeName = StringUtils.hasText(
                target.userTask().getName())
                ? target.userTask().getName()
                : target.userTask().getId();
        return conflict(
                "NEXT_APPROVER_DEFERRED_DEFAULT_REQUIRED",
                "提交前处理无法无副作用预览，节点“"
                        + nodeName
                        + "”必须配置可用默认审批人后才能审批；"
                        + reason
                        + "。这是流程配置错误，请勿重复提交");
    }

    private BusinessConflictException conflict(
            String code,
            String message) {
        return new BusinessConflictException(code, message);
    }

    private void recordAudit(Task sourceTask, AuditEntry entry) {
        String operatorId = firstText(
                UserContext.getUserId(), UserContext.getUsername());
        ProcessOperationLog log = new ProcessOperationLog();
        log.setProcessInstanceId(sourceTask.getProcessInstanceId());
        log.setTaskId(sourceTask.getId());
        log.setOperationType("NEXT_ASSIGNEE_OVERRIDE");
        log.setOperatorId(operatorId);
        log.setOperatorName(StringUtils.hasText(operatorId)
                ? sysUserService.getDisplayName(operatorId)
                : operatorId);
        log.setOperationTime(LocalDateTime.now());
        log.setOperationComment(
                "指定下一节点审批人: "
                        + (StringUtils.hasText(entry.nodeName())
                        ? entry.nodeName() : entry.nodeId()));
        log.setOldValue(json(Map.of(
                "targetNodeId", entry.nodeId(),
                "defaultUserKeys", entry.defaultUsernames())));
        log.setNewValue(json(Map.of(
                "targetNodeId", entry.nodeId(),
                "usernames", entry.usernames())));
        log.setOldValueFormat("JSON");
        log.setNewValueFormat("JSON");
        operationLogMapper.insert(log);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw conflict(
                    "NEXT_APPROVER_RESOLUTION_FAILED",
                    "下一审批人审计数据序列化失败");
        }
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private record AuditEntry(
            String nodeId,
            String nodeName,
            List<String> defaultUsernames,
            List<String> usernames) {
    }
}
