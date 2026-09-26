package com.workflow.biz.project.service;
import com.workflow.contracts.process.action.context.FlowActionContext;
import com.workflow.contracts.entity.model.EntityRecordData;
import com.workflow.contracts.entity.port.EntityRecordQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import static com.workflow.biz.project.service.ProjectGovernanceValues.bool;
import static com.workflow.biz.project.service.ProjectGovernanceValues.conflict;
import static com.workflow.biz.project.service.ProjectGovernanceValues.data;
import static com.workflow.biz.project.service.ProjectGovernanceValues.date;
import static com.workflow.biz.project.service.ProjectGovernanceValues.decimal;
import static com.workflow.biz.project.service.ProjectGovernanceValues.read;
import static com.workflow.biz.project.service.ProjectGovernanceValues.requireEntity;
import static com.workflow.biz.project.service.ProjectGovernanceValues.requireText;
import static com.workflow.biz.project.service.ProjectGovernanceValues.text;
import static com.workflow.biz.project.service.ProjectGovernanceValues.update;
import static com.workflow.biz.project.service.ProjectGovernanceValues.upper;
/**
 * 项目成员加入、离场、暂停、恢复和投入比例变更的跨实体业务规则。
 */
@Service
@RequiredArgsConstructor
public class ProjectMemberChangeService {
    private static final String REQUEST = "project_member_change_request";
    private static final String PROJECT = "project";
    private static final String PROJECT_MEMBER = "project_member";
    private static final String PROJECT_ROLE_ASSIGNMENT =
            "project_role_assignment";
    private static final Set<String> OPERATIONS = Set.of(
            "JOIN", "LEAVE", "SUSPEND", "RESUME",
            "ALLOCATION_CHANGE");
    private static final Set<String> ALLOWED_PROJECT_STATUSES = Set.of(
            "APPROVED", "ACTIVE", "PAUSED", "ACCEPTING");
    private final EntityRecordQueryPort entityDataService;
    private final ProjectEntityMutationExecutor mutationExecutor;
    private final ProjectMemberChangeRuleSupport rules;
    private final ProjectMemberChangeTraceSupport traceSupport;
    /**
     * 启动流程前执行完整跨实体校验并固化快照和路由变量。
     *
     * @param request 本次请求，后续经校验后用于校验变更
     * @param context 执行上下文，向后续变更步骤传递身份、配置或状态
     * @return 变更键值结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> validateChange(
            EntityRecordData request,
            FlowActionContext context) {
        return mutationExecutor.inSession(
                context,
                "MEMBER_CHANGE_PRECHECK",
                "项目成员变更前置校验",
                () -> {
                    Map<String, Object> result =
                            validateChangeInternal(request);
                    context.setProcessVariables(Map.of(
                            "access_review_required_flag",
                            result.get(
                                    "accessReviewRequired"),
                            "security_review_required_flag",
                            result.get(
                                    "securityReviewRequired"),
                            "handover_required_flag",
                            result.get(
                                    "handoverRequired")));
                    return result;
                });
    }
    /**
     * 校验变更；不满足约束时阻止后续处理。
     *
     * @param request 本次请求，后续经校验后用于校验变更
     * @return 变更键值结果，供调用方继续处理
     */
    Map<String, Object> validateChange(EntityRecordData request) {
        return mutationExecutor.inSession(
                null,
                "MEMBER_CHANGE_PRECHECK",
                "项目成员变更前置校验",
                () -> validateChangeInternal(request));
    }
    /**
     * 校验变更内部；不满足约束时阻止后续处理。
     *
     * @param request 成员变更申请，读取操作类型、目标人员和计划生效日期，并回写校验快照
     * @return 包含权限复核、交接和安全复核结论的校验结果，供后续审批节点使用
     */
    private Map<String, Object> validateChangeInternal(
            EntityRecordData request) {
        requireEntity(request, REQUEST);
        Map<String, Object> requestData = data(request);
        String operation = upper(read(
                requestData, "operation_type"));
        if (!OPERATIONS.contains(operation)) {
            conflict(
                    "PROJECT_MEMBER_OPERATION_INVALID",
                    "项目成员变更操作类型不合法");
        }
        String projectId = requireText(
                requestData, "project_id", "项目不能为空");
        EntityRecordData project =
                entityDataService.findById(PROJECT, projectId);
        if (!ALLOWED_PROJECT_STATUSES.contains(project.getStatus())) {
            conflict(
                    "PROJECT_MEMBER_PROJECT_STATUS_INVALID",
                    "项目必须处于已批准、进行中、暂停或验收中状态");
        }
        LocalDate effectiveDate = date(read(
                requestData, "effective_date"));
        if (effectiveDate == null) {
            conflict(
                    "PROJECT_MEMBER_EFFECTIVE_DATE_REQUIRED",
                    "计划生效日期不能为空");
        }
        validateProjectDate(project, effectiveDate);
        EntityRecordData member = null;
        String targetUserId;
        BigDecimal requestedAllocation = BigDecimal.ZERO;
        List<EntityRecordData> activeRoles = List.of();
        boolean accessReviewRequired;
        boolean securityReviewRequired;
        boolean handoverRequired = false;
        // 加入与存量成员变更的校验依据不同：加入检查容量，离开还需检查角色和资源交接。
        if ("JOIN".equals(operation)) {
            targetUserId = requireText(
                    requestData, "target_user_id", "加入人员不能为空");
            requireText(
                    requestData, "source_dept_id", "来源部门不能为空");
            String employmentType = requireText(
                    requestData, "employment_type", "人员类型不能为空");
            requestedAllocation = rules.allocation(
                    read(requestData, "new_allocation_percentage"));
            rules.ensureMemberDoesNotExist(projectId, targetUserId);
            rules.ensureAllocationAvailable(
                    targetUserId, null, requestedAllocation);
            validateJoinDates(
                    requestData, effectiveDate, employmentType);
            validateAccessScope(requestData);
            accessReviewRequired = bool(read(
                    requestData, "account_required_flag"))
                    || bool(read(
                    requestData,
                    "environment_access_required_flag"));
            securityReviewRequired =
                    rules.requiresSecurityReview(requestData);
        } else {
            String memberId = requireText(
                    requestData,
                    "project_member_id",
                    "目标项目成员不能为空");
            member = entityDataService.findById(
                    PROJECT_MEMBER, memberId);
            if (!Objects.equals(
                    projectId,
                    text(read(data(member), "project_id")))) {
                conflict(
                        "PROJECT_MEMBER_PROJECT_MISMATCH",
                        "目标成员不属于所选项目");
            }
            targetUserId = requireText(
                    data(member), "user_id", "目标成员未关联人员");
            validateOperationStatus(operation, member);
            validateEffectiveDate(member, effectiveDate);
            if ("ALLOCATION_CHANGE".equals(operation)) {
                requestedAllocation = rules.allocation(read(
                        requestData,
                        "new_allocation_percentage"));
                rules.ensureAllocationAvailable(
                        targetUserId,
                        member.getId(),
                        requestedAllocation);
            }
            activeRoles = rules.activeRoles(projectId, member.getId());
            if ("LEAVE".equals(operation)) {
                handoverRequired =
                        !activeRoles.isEmpty()
                                || bool(read(
                                data(member),
                                "account_required_flag"))
                                || bool(read(
                                data(member),
                                "environment_access_required_flag"));
                validateLeave(
                        requestData,
                        projectId,
                        member,
                        effectiveDate,
                        handoverRequired,
                        activeRoles);
            }
            accessReviewRequired =
                    "LEAVE".equals(operation)
                            || "SUSPEND".equals(operation)
                            || bool(read(
                            data(member),
                            "account_required_flag"))
                            || bool(read(
                            data(member),
                            "environment_access_required_flag"));
            securityReviewRequired =
                    rules.requiresSecurityReview(requestData)
                            || bool(read(
                            data(member),
                            "environment_access_required_flag"));
        }
        // 统一输出审批分支所需的复核标志，避免后续节点重复推导当前业务状态。
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("allowed", true);
        result.put("operation", operation);
        result.put("projectId", projectId);
        result.put("targetUserId", targetUserId);
        result.put("requestedAllocation", requestedAllocation);
        result.put("activeRoleCount", activeRoles.size());
        result.put("handoverRequired", handoverRequired);
        result.put(
                "accessReviewRequired",
                accessReviewRequired);
        result.put(
                "securityReviewRequired",
                securityReviewRequired);
        result.put("checkedAt", LocalDateTime.now());
        // 固化变更前后快照和本次冲突检查结果，供审批及审计复查，避免依赖后续可变数据。
        Map<String, Object> snapshotUpdate =
                new LinkedHashMap<>();
        snapshotUpdate.put(
                "target_user_id", targetUserId);
        snapshotUpdate.put(
                "before_snapshot",
                rules.json(member == null ? Map.of() : member));
        snapshotUpdate.put(
                "after_snapshot",
                rules.json(rules.proposedMemberData(
                        operation,
                        requestData,
                        member)));
        snapshotUpdate.put(
                "conflict_check_result", rules.json(result));
        snapshotUpdate.put(
                "access_review_required_flag",
                accessReviewRequired);
        snapshotUpdate.put(
                "security_review_required_flag",
                securityReviewRequired);
        snapshotUpdate.put(
                "handover_required_flag",
                handoverRequired);
        mutationExecutor.update(
                REQUEST,
                request.getId(),
                Map.of("data", snapshotUpdate));
        return result;
    }
    /**
     * 项目经理节点完成时写入业务检查点。
     *
     * @param request 本次请求，后续经校验后用于捕获{@code manager}{@code review}
     * @param context 执行上下文，向后续{@code manager}{@code review}步骤传递身份、配置或状态
     * @return {@code manager}{@code review}键值结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> captureManagerReview(
            EntityRecordData request,
            FlowActionContext context) {
        return mutationExecutor.inSession(
                context,
                "MEMBER_MANAGER_REVIEWED",
                "项目经理完成成员变更复核",
                () -> traceSupport.captureManagerReview(request, context));
    }
    /**
     * 最终审批连线被选中时记录决策轨迹。
     *
     * @param request 本次请求，后续经校验后用于记录决策
     * @param context 执行上下文，向后续决策步骤传递身份、配置或状态
     * @param decision 决策，供本方法记录决策时使用
     * @return 决策键值结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> recordDecision(
            EntityRecordData request,
            FlowActionContext context,
            String decision) {
        return mutationExecutor.inSession(
                context,
                "MEMBER_DECISION_RECORDED",
                "记录项目成员变更决策",
                () -> traceSupport.recordDecision(request, context, decision));
    }
    /**
     * 流程批准后幂等生效成员和角色变化。
     *
     * @param request 本次请求，后续经校验后用于应用变更
     * @param context 执行上下文，向后续变更步骤传递身份、配置或状态
     * @return 变更键值结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> applyChange(
            EntityRecordData request,
            FlowActionContext context) {
        return mutationExecutor.inSession(
                context,
                "CHANGE_EFFECTIVE",
                "项目成员变更审批生效",
                () -> applyChangeInternal(request));
    }
    /**
     * 应用变更，并将结果传给后续步骤。
     *
     * @param request 本次请求，后续经校验后用于应用变更
     * @return 变更键值结果，供调用方继续处理
     */
    Map<String, Object> applyChange(EntityRecordData request) {
        return mutationExecutor.inSession(
                null,
                "CHANGE_EFFECTIVE",
                "项目成员变更审批生效",
                () -> applyChangeInternal(request));
    }
    /**
     * 应用变更内部，并将结果传给后续步骤。
     *
     * @param request 本次请求，后续经校验后用于应用变更内部
     * @return 变更内部键值结果，供调用方继续处理
     */
    private Map<String, Object> applyChangeInternal(
            EntityRecordData request) {
        requireEntity(request, REQUEST);
        Map<String, Object> requestData = data(request);
        String existingMemberId = text(read(
                requestData, "effective_member_id"));
        if ("EFFECTIVE".equals(request.getStatus())
                && StringUtils.hasText(existingMemberId)) {
            return Map.of(
                    "requestId", request.getId(),
                    "memberId", existingMemberId,
                    "reused", true);
        }
        String operation = upper(read(
                requestData, "operation_type"));
        String projectId = requireText(
                requestData, "project_id", "项目不能为空");
        LocalDate effectiveDate = date(read(
                requestData, "effective_date"));
        EntityRecordData member;
        int transferredRoleCount = 0;
        switch (operation) {
            case "JOIN" -> member =
                    createMember(
                            request,
                            projectId,
                            effectiveDate);
            case "LEAVE" -> {
                member = rules.requireTargetMember(
                        requestData, projectId);
                transferredRoleCount =
                        applyLeave(
                                request,
                                member,
                                effectiveDate);
            }
            case "SUSPEND" -> {
                member = rules.requireTargetMember(
                        requestData, projectId);
                updateMemberAndRoles(
                        member,
                        "SUSPENDED",
                        "SUSPENDED",
                        Map.of(
                                "access_revoked_flag",
                                bool(read(
                                        data(member),
                                        "environment_access_required_flag")),
                                "source_process",
                                "F07"));
            }
            case "RESUME" -> {
                member = rules.requireTargetMember(
                        requestData, projectId);
                updateMemberAndRoles(
                        member,
                        "ACTIVE",
                        "ACTIVE",
                        Map.of(
                                "access_revoked_flag",
                                false,
                                "source_process",
                                "F07"));
            }
            case "ALLOCATION_CHANGE" -> {
                member = rules.requireTargetMember(
                        requestData, projectId);
                mutationExecutor.update(
                        PROJECT_MEMBER,
                        member.getId(),
                        update(
                                member.getStatus(),
                                Map.of(
                                        "allocation_percentage",
                                        rules.allocation(read(
                                                requestData,
                                                "new_allocation_percentage")),
                                        "source_process",
                                        "F07")));
            }
            default -> {
                conflict(
                        "PROJECT_MEMBER_OPERATION_INVALID",
                        "项目成员变更操作类型不合法");
                throw new IllegalStateException(
                        "unreachable");
            }
        }
        LocalDateTime effectiveAt = LocalDateTime.now();
        String resultText = switch (operation) {
            case "JOIN" -> "成员已加入项目";
            case "LEAVE" -> "成员已完成交接并退出项目";
            case "SUSPEND" -> "成员及其项目角色已暂停";
            case "RESUME" -> "成员及其项目角色已恢复";
            case "ALLOCATION_CHANGE" -> "成员投入比例已更新";
            default -> "成员变更已生效";
        };
        Map<String, Object> requestUpdate =
                new LinkedHashMap<>();
        requestUpdate.put(
                "effective_member_id", member.getId());
        requestUpdate.put(
                "implementation_result", resultText);
        requestUpdate.put(
                "effective_at", effectiveAt);
        requestUpdate.put(
                "transferred_role_count",
                transferredRoleCount);
        mutationExecutor.update(
                REQUEST,
                request.getId(),
                update("EFFECTIVE", requestUpdate));
        Map<String, Object> result =
                new LinkedHashMap<>();
        result.put("requestId", request.getId());
        result.put("operation", operation);
        result.put("memberId", member.getId());
        result.put(
                "transferredRoleCount",
                transferredRoleCount);
        result.put("effectiveAt", effectiveAt);
        result.put("reused", false);
        return result;
    }
    /**
     * 创建成员；结果供后续流程传递或持久化。
     *
     * @param request 本次请求，后续经校验后用于创建成员
     * @param projectId 项目ID，后续用于创建成员时定位或关联目标
     * @param effectiveDate 有效日期，后续用于判断有效期或展示该事件的发生时间
     * @return 创建后的成员结果，供调用方继续处理
     */
    private EntityRecordData createMember(
            EntityRecordData request,
            String projectId,
            LocalDate effectiveDate) {
        Map<String, Object> source = data(request);
        Map<String, Object> memberData =
                new LinkedHashMap<>();
        memberData.put("project_id", projectId);
        memberData.put(
                "user_id",
                requireText(
                        source,
                        "target_user_id",
                        "加入人员不能为空"));
        memberData.put(
                "source_dept_id",
                requireText(
                        source,
                        "source_dept_id",
                        "来源部门不能为空"));
        memberData.put(
                "employment_type",
                requireText(
                        source,
                        "employment_type",
                        "人员类型不能为空"));
        memberData.put("join_date", effectiveDate);
        memberData.put(
                "planned_leave_date",
                date(read(
                        source,
                        "planned_leave_date")));
        memberData.put(
                "allocation_percentage",
                rules.allocation(read(
                        source,
                        "new_allocation_percentage")));
        memberData.put(
                "join_reason",
                requireText(
                        source,
                        "change_reason",
                        "加入原因不能为空"));
        memberData.put(
                "account_required_flag",
                bool(read(
                        source,
                        "account_required_flag")));
        memberData.put(
                "environment_access_required_flag",
                bool(read(
                        source,
                        "environment_access_required_flag")));
        memberData.put(
                "environment_scope",
                read(source, "environment_scope"));
        memberData.put("access_revoked_flag", false);
        memberData.put(
                "handover_completed_flag", false);
        memberData.put("source_process", "F07");
        EntityRecordData dto = new EntityRecordData();
        dto.setEntityCode(PROJECT_MEMBER);
        dto.setName(
                "项目成员-"
                        + text(read(source, "target_user_id")));
        dto.setSubmitterId(request.getSubmitterId());
        dto.setSubmitterName(
                request.getSubmitterName());
        dto.setData(memberData);
        EntityRecordData created =
                mutationExecutor.save(dto);
        mutationExecutor.update(
                PROJECT_MEMBER,
                created.getId(),
                update(
                        "ACTIVE",
                        Map.of("source_process", "F07")));
        return created;
    }
    /**
     * 应用{@code leave}，并将结果传给后续步骤。
     *
     * @param request 本次请求，后续经校验后用于应用{@code leave}
     * @param member 成员，作为 {@code rules.activeRoles} 的输入影响后续处理
     * @param effectiveDate 有效日期，后续用于判断有效期或展示该事件的发生时间
     * @return 应用后的{@code leave}结果，供调用方继续处理
     */
    private int applyLeave(
            EntityRecordData request,
            EntityRecordData member,
            LocalDate effectiveDate) {
        Map<String, Object> requestData = data(request);
        String projectId = text(read(
                requestData, "project_id"));
        List<EntityRecordData> activeRoles =
                rules.activeRoles(projectId, member.getId());
        String handoverMemberId = text(read(
                requestData, "handover_member_id"));
        EntityRecordData handoverMember =
                StringUtils.hasText(handoverMemberId)
                        ? entityDataService.findById(
                        PROJECT_MEMBER,
                        handoverMemberId)
                        : null;
        Map<String, Object> memberUpdate =
                new LinkedHashMap<>();
        memberUpdate.put(
                "actual_leave_date", effectiveDate);
        memberUpdate.put(
                "allocation_percentage",
                BigDecimal.ZERO);
        memberUpdate.put(
                "leave_reason",
                text(read(
                        requestData,
                        "change_reason")));
        memberUpdate.put(
                "handover_user_id",
                handoverMemberId);
        memberUpdate.put(
                "handover_description",
                text(read(
                        requestData,
                        "handover_description")));
        memberUpdate.put(
                "access_revoked_flag", true);
        memberUpdate.put(
                "handover_completed_flag",
                handoverMember != null
                        || activeRoles.isEmpty());
        memberUpdate.put("source_process", "F07");
        mutationExecutor.update(
                PROJECT_MEMBER,
                member.getId(),
                update("LEFT", memberUpdate));
        if (activeRoles.isEmpty()) {
            return 0;
        }
        if (handoverMember == null) {
            conflict(
                    "PROJECT_MEMBER_HANDOVER_REQUIRED",
                    "成员存在有效项目角色，必须指定交接成员");
        }
        for (EntityRecordData assignment : activeRoles) {
            transferRole(
                    request,
                    assignment,
                    handoverMember,
                    effectiveDate);
        }
        return activeRoles.size();
    }
    /**
     * 处理转办角色，并将结果传给后续步骤。
     *
     * @param request 本次请求，后续经校验后用于处理转办角色
     * @param assignment 分配，作为 {@code data} 的输入影响后续处理
     * @param handoverMember {@code handover}成员，作为 {@code newData.put} 的输入影响后续处理
     * @param effectiveDate 有效日期，后续用于判断有效期或展示该事件的发生时间
     */
    private void transferRole(
            EntityRecordData request,
            EntityRecordData assignment,
            EntityRecordData handoverMember,
            LocalDate effectiveDate) {
        Map<String, Object> oldData = data(assignment);
        mutationExecutor.update(
                PROJECT_ROLE_ASSIGNMENT,
                assignment.getId(),
                update(
                        "REVOKED",
                        Map.of(
                                "effective_to",
                                effectiveDate,
                                "handover_required_flag",
                                true,
                                "handover_completed_flag",
                                true,
                                "source_process",
                                "F07")));
        Map<String, Object> newData =
                new LinkedHashMap<>(oldData);
        newData.put(
                "member_id", handoverMember.getId());
        newData.put(
                "user_id",
                text(read(
                        data(handoverMember),
                        "user_id")));
        newData.put(
                "effective_from", effectiveDate);
        newData.put("effective_to", null);
        newData.put(
                "predecessor_assignment_id",
                assignment.getId());
        newData.put(
                "handover_required_flag", true);
        newData.put(
                "handover_completed_flag", true);
        newData.put("source_process", "F07");
        EntityRecordData replacement =
                new EntityRecordData();
        replacement.setEntityCode(
                PROJECT_ROLE_ASSIGNMENT);
        replacement.setName(
                assignment.getName()
                        + "-交接");
        replacement.setSubmitterId(
                request.getSubmitterId());
        replacement.setSubmitterName(
                request.getSubmitterName());
        replacement.setData(newData);
        EntityRecordData created =
                mutationExecutor.save(replacement);
        mutationExecutor.update(
                PROJECT_ROLE_ASSIGNMENT,
                created.getId(),
                update(
                        "ACTIVE",
                        Map.of("source_process", "F07")));
    }
    /**
     * 更新成员与角色集合；后续读取或执行将使用更新后的状态。
     *
     * @param member 成员，作为 {@code mutationExecutor.update} 的输入影响后续处理
     * @param memberStatus 成员状态标识，决定后续成员与角色集合采用的处理分支
     * @param roleStatus 角色状态标识，决定后续成员与角色集合采用的处理分支
     * @param customData 自定义数据，供本方法更新成员与角色集合时使用
     */
    private void updateMemberAndRoles(
            EntityRecordData member,
            String memberStatus,
            String roleStatus,
            Map<String, Object> customData) {
        mutationExecutor.update(
                PROJECT_MEMBER,
                member.getId(),
                update(memberStatus, customData));
        for (EntityRecordData role :
                rules.rolesForMember(member.getId())) {
            if (Set.of("ACTIVE", "SUSPENDED")
                    .contains(role.getStatus())) {
                mutationExecutor.update(
                        PROJECT_ROLE_ASSIGNMENT,
                        role.getId(),
                        update(
                                roleStatus,
                                Map.of(
                                        "source_process",
                                        "F07")));
            }
        }
    }
    /**
     * 校验项目日期；不满足约束时阻止后续处理。
     *
     * @param project 项目，作为 {@code date} 的输入影响后续处理
     * @param effectiveDate 有效日期，后续用于判断有效期或展示该事件的发生时间
     */
    private void validateProjectDate(
            EntityRecordData project,
            LocalDate effectiveDate) {
        LocalDate projectStart = date(read(
                data(project), "planned_start_date"));
        LocalDate projectEnd = date(read(
                data(project), "planned_end_date"));
        if (projectStart != null
                && effectiveDate.isBefore(projectStart)) {
            conflict(
                    "PROJECT_MEMBER_DATE_OUTSIDE_PROJECT",
                    "成员变更生效日期不得早于项目开始日期");
        }
        if (projectEnd != null
                && effectiveDate.isAfter(projectEnd)) {
            conflict(
                    "PROJECT_MEMBER_DATE_OUTSIDE_PROJECT",
                    "成员变更生效日期不得晚于项目结束日期");
        }
    }
    /**
     * 校验{@code join}{@code dates}；不满足约束时阻止后续处理。
     *
     * @param requestData 请求数据，作为 {@code date} 的输入影响后续处理
     * @param effectiveDate 有效日期，后续用于判断有效期或展示该事件的发生时间
     * @param employmentType {@code employment}类型标识，决定后续{@code join}{@code dates}采用的处理分支
     */
    private void validateJoinDates(
            Map<String, Object> requestData,
            LocalDate effectiveDate,
            String employmentType) {
        LocalDate plannedLeaveDate = date(read(
                requestData, "planned_leave_date"));
        if (plannedLeaveDate != null
                && plannedLeaveDate.isBefore(effectiveDate)) {
            conflict(
                    "PROJECT_MEMBER_DATE_RANGE_INVALID",
                    "计划退出日期不得早于加入日期");
        }
        if (Set.of("VENDOR", "CONTRACTOR")
                .contains(upper(employmentType))
                && plannedLeaveDate == null) {
            conflict(
                    "PROJECT_MEMBER_PLANNED_LEAVE_REQUIRED",
                    "供应商和合同人员必须填写计划退出日期");
        }
    }
    /**
     * 校验访问作用域；不满足约束时阻止后续处理。
     *
     * @param requestData 请求数据，供本方法校验访问作用域时使用
     */
    private void validateAccessScope(
            Map<String, Object> requestData) {
        if (bool(read(
                requestData,
                "environment_access_required_flag"))
                && !rules.hasValues(read(
                requestData,
                "environment_scope"))) {
            conflict(
                    "PROJECT_MEMBER_ENVIRONMENT_SCOPE_REQUIRED",
                    "申请环境权限时必须选择环境范围");
        }
    }
    /**
     * 校验操作状态；不满足约束时阻止后续处理。
     *
     * @param operation 操作标识，决定后续操作状态采用的处理分支
     * @param member 成员，作为 {@code equals} 的输入影响后续处理
     */
    private void validateOperationStatus(
            String operation,
            EntityRecordData member) {
        boolean allowed = switch (operation) {
            case "LEAVE" -> Set.of(
                    "ACTIVE", "SUSPENDED")
                    .contains(member.getStatus());
            case "SUSPEND",
                 "ALLOCATION_CHANGE" ->
                    "ACTIVE".equals(member.getStatus());
            case "RESUME" ->
                    "SUSPENDED".equals(member.getStatus());
            default -> false;
        };
        if (!allowed) {
            conflict(
                    "PROJECT_MEMBER_STATUS_INVALID",
                    "目标成员当前状态不允许执行"
                            + operation + "操作");
        }
    }
    /**
     * 校验有效日期；不满足约束时阻止后续处理。
     *
     * @param member 成员，作为 {@code date} 的输入影响后续处理
     * @param effectiveDate 有效日期，后续用于判断有效期或展示该事件的发生时间
     */
    private void validateEffectiveDate(
            EntityRecordData member,
            LocalDate effectiveDate) {
        LocalDate joinDate = date(read(
                data(member), "join_date"));
        if (joinDate != null
                && effectiveDate.isBefore(joinDate)) {
            conflict(
                    "PROJECT_MEMBER_EFFECTIVE_BEFORE_JOIN",
                    "变更生效日期不得早于成员加入日期");
        }
    }
    /**
     * 校验{@code leave}；不满足约束时阻止后续处理。
     *
     * @param requestData 请求数据，作为 {@code text} 的输入影响后续处理
     * @param projectId 项目ID，后续用于校验{@code leave}时定位或关联目标
     * @param member 成员，供本方法校验{@code leave}时使用
     * @param effectiveDate 有效日期，后续用于判断有效期或展示该事件的发生时间
     * @param handoverRequired {@code handover}必填，供本方法校验{@code leave}时使用
     * @param activeRoles 活动角色集合，供本方法校验{@code leave}时使用
     */
    private void validateLeave(
            Map<String, Object> requestData,
            String projectId,
            EntityRecordData member,
            LocalDate effectiveDate,
            boolean handoverRequired,
            List<EntityRecordData> activeRoles) {
        String handoverMemberId = text(read(
                requestData, "handover_member_id"));
        if (handoverRequired
                && !StringUtils.hasText(handoverMemberId)) {
            conflict(
                    "PROJECT_MEMBER_HANDOVER_REQUIRED",
                    "成员存在角色或权限，离场前必须指定交接成员");
        }
        if (StringUtils.hasText(handoverMemberId)) {
            if (Objects.equals(
                    member.getId(), handoverMemberId)) {
                conflict(
                        "PROJECT_MEMBER_HANDOVER_SELF",
                        "交接成员不能选择本人");
            }
            EntityRecordData handover =
                    entityDataService.findById(
                            PROJECT_MEMBER,
                            handoverMemberId);
            if (!Objects.equals(
                    projectId,
                    text(read(
                            data(handover),
                            "project_id")))
                    || !"ACTIVE".equals(
                    handover.getStatus())) {
                conflict(
                        "PROJECT_MEMBER_HANDOVER_INVALID",
                        "交接成员必须是同项目的有效成员");
            }
        }
        if (handoverRequired
                && !StringUtils.hasText(text(read(
                requestData,
                "handover_description")))) {
            conflict(
                    "PROJECT_MEMBER_HANDOVER_DESCRIPTION_REQUIRED",
                    "离场交接说明不能为空");
        }
        LocalDate revokeDeadline = date(read(
                requestData,
                "permission_revoke_deadline"));
        if (handoverRequired
                && revokeDeadline == null) {
            conflict(
                    "PROJECT_MEMBER_REVOKE_DEADLINE_REQUIRED",
                    "离场时必须填写权限回收截止日期");
        }
        if (revokeDeadline != null
                && revokeDeadline.isAfter(
                effectiveDate.plusDays(1))) {
            conflict(
                    "PROJECT_MEMBER_REVOKE_DEADLINE_LATE",
                    "权限最迟须在离场后一个自然日内回收");
        }
        if (activeRoles.stream().anyMatch(
                rules::isPrimaryManagerRole)
                && !StringUtils.hasText(handoverMemberId)) {
            conflict(
                    "PROJECT_MEMBER_PRIMARY_ROLE_BLOCKED",
                    "唯一主负责人离场前必须完成角色交接");
        }
    }
}
