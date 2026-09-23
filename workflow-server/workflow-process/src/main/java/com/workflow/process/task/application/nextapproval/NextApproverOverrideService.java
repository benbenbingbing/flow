package com.workflow.process.task.application.nextapproval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.process.assignment.model.PersonResolveUsage;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.process.audit.infrastructure.persistence.mapper.ProcessOperationLogMapper;
import com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog;
import com.workflow.process.task.api.request.NextApprovalPreviewRequest;
import com.workflow.process.task.api.request.NextApproverSelectionRequest;
import com.workflow.process.task.infrastructure.MultiInstanceVariableNames;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.Activity;
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

    /**
     * 校验与{@code stage}；不满足约束时阻止后续处理。
     *
     * @param task 任务，供本方法校验与{@code stage}时使用
     * @param action 动作标识，决定后续与{@code stage}采用的处理分支
     * @param actionLabel 动作标签，后续用于校验与{@code stage}时匹配或展示
     * @param comment 注释，供本方法校验与{@code stage}时使用
     * @param submittedScopeKey 已提交作用域键，后续用于授权校验、关联或幂等去重
     * @param selections {@code selections}，供本方法校验与{@code stage}时使用
     */
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

    /**
     * 校验与{@code stage}；不满足约束时阻止后续处理。
     *
     * @param task 任务，作为 {@code routeService.resolve} 的输入影响后续处理
     * @param action 动作标识，决定后续与{@code stage}采用的处理分支
     * @param actionLabel 动作标签，后续用于校验与{@code stage}时匹配或展示
     * @param comment 注释，作为 {@code request.setComment} 的输入影响后续处理
     * @param submittedScopeKey 已提交作用域键，后续用于授权校验、关联或幂等去重
     * @param selections {@code selections}，供本方法校验与{@code stage}时使用
     * @param previewWasDeferred 预览{@code was}{@code deferred}，供本方法校验与{@code stage}时使用
     */
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
                // 下一节点若在本次 complete 内立刻进入，Flowable 会先解析
                // collection 再发 ACTIVITY_STARTED。store 供监听器重入消费，
                // 集合变量保证当前命令内就能创建多实例。
                runtimeService.setVariable(
                        task.getProcessInstanceId(),
                        variableName,
                        List.copyOf(usernames));
            }
            // 会签/或签覆盖写入一次性 store，由集合监听器在节点进入时消费。
            // 不在这里直接改 collection，避免路径未走到该节点或循环重入时沿用旧人选。
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("sourceTaskId", task.getId());
            entry.put("targetNodeId", target.userTask().getId());
            entry.put("assignmentMode", policy.assignmentMode());
            entry.put("usernames", List.copyOf(usernames));
            entry.put("scopeKey", resolution.scopeKey());
            entry.put("createdAt", Instant.now().toString());
            overrides.put(target.userTask().getId(), entry);
            audits.add(new AuditEntry(
                    target.userTask().getId(),
                    target.userTask().getName(),
                    defaultsByNode.getOrDefault(
                            target.userTask().getId(), List.of()),
                    List.copyOf(usernames)));
        }
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
     *
     * @param task 任务，作为 {@code routeService.resolve} 的输入影响后续处理
     * @param action 动作标识，决定后续预览是否{@code deferred}采用的处理分支
     * @param actionLabel 动作标签，后续用于处理预览是否{@code deferred}时匹配或展示
     * @param comment 注释，作为 {@code request.setComment} 的输入影响后续处理
     * @param formData 表单数据，作为 {@code request.setFormData} 的输入影响后续处理
     * @return 预览是否{@code deferred}条件成立时为 true，否则为 false
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
     *
     * @param task 任务，作为 {@code routeService.resolve} 的输入影响后续处理
     * @param action 动作标识，决定后续需要人工选择{@code deferred}{@code completion}采用的处理分支
     * @param actionLabel 动作标签，后续用于处理需要人工选择{@code deferred}{@code completion}时匹配或展示
     * @param comment 注释，作为 {@code request.setComment} 的输入影响后续处理
     * @param formData 表单数据，作为 {@code request.setFormData} 的输入影响后续处理
     * @return 需要人工选择{@code deferred}{@code completion}条件成立时为 true，否则为 false
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

    /**
     * 生成多实例集合变量文本，供后续匹配或展示。
     *
     * @param activity 活动，作为 {@code MultiInstanceVariableNames.resolveCollectionVariable} 的输入影响后续处理
     * @return 处理后的多实例集合变量文本，供调用方比较或展示
     */
    private String multiInstanceCollectionVariable(Activity activity) {
        return MultiInstanceVariableNames.resolveCollectionVariable(
                activity);
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
     * @return 下一步审批人覆盖集合，供调用方遍历或展示
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
     * 将动态值转换为键值映射，供后续字段读取和校验。
     *
     * @param value 待处理映射值的原始输入，结果供调用方继续使用
     * @return 映射值键值结果，供调用方继续处理
     */
    private Map<String, Object> mapValue(Map<?, ?> value) {
        Map<String, Object> result = new LinkedHashMap<>();
        value.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    /**
     * 生成安全消息文本，供后续匹配或展示。
     *
     * @param exception 异常，供本方法处理安全消息时使用
     * @return 处理后的安全消息文本，供调用方比较或展示
     */
    private String safeMessage(RuntimeException exception) {
        return StringUtils.hasText(exception.getMessage())
                ? exception.getMessage()
                : exception.getClass().getSimpleName();
    }

    /**
     * 构造{@code deferred}默认必填异常，供调用方区分失败原因。
     *
     * @param target 目标，供本方法处理{@code deferred}默认必填时使用
     * @param reason 原因，供本方法处理{@code deferred}默认必填时使用
     * @return 处理后的{@code deferred}默认必填结果，供调用方继续处理
     */
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

    /**
     * 构造业务冲突异常，供调用方刷新或重试。
     *
     * @param code 编码，后续用于处理冲突时定位或关联目标
     * @param message 消息，作为 {@code BusinessConflictException} 的输入影响后续处理
     * @return 处理后的冲突结果，供调用方继续处理
     */
    private BusinessConflictException conflict(
            String code,
            String message) {
        return new BusinessConflictException(code, message);
    }

    /**
     * 记录审计；供后续追溯或审计使用。
     *
     * @param sourceTask 来源任务，作为 {@code log.setProcessInstanceId} 的输入影响后续处理
     * @param entry 入口，作为 {@code log.setOperationComment} 的输入影响后续处理
     */
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

    /**
     * 生成JSON文本，供后续匹配或展示。
     *
     * @param value 待处理JSON的原始输入，结果供调用方继续使用
     * @return 处理后的JSON文本，供调用方比较或展示
     */
    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw conflict(
                    "NEXT_APPROVER_RESOLUTION_FAILED",
                    "下一审批人审计数据序列化失败");
        }
    }

    /**
     * 按候选顺序取首个非空文本，供后续匹配或展示使用。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的首个文本文本，供调用方比较或展示
     */
    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 封装审计入口的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param nodeId 节点ID，后续用于处理审计入口时定位或关联目标
     * @param nodeName 节点名称，后续用于处理审计入口时匹配或展示
     * @param defaultUsernames 默认{@code usernames}，保存在对象中供后续校验、查询或展示
     * @param usernames {@code usernames}，保存在对象中供后续校验、查询或展示
     */
    private record AuditEntry(
            String nodeId,
            String nodeName,
            List<String> defaultUsernames,
            List<String> usernames) {
    }
}
