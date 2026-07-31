package com.workflow.project.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.action.FlowActionContext;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
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

import static com.workflow.project.service.ProjectGovernanceValues.bool;
import static com.workflow.project.service.ProjectGovernanceValues.conflict;
import static com.workflow.project.service.ProjectGovernanceValues.data;
import static com.workflow.project.service.ProjectGovernanceValues.date;
import static com.workflow.project.service.ProjectGovernanceValues.decimal;
import static com.workflow.project.service.ProjectGovernanceValues.read;
import static com.workflow.project.service.ProjectGovernanceValues.requireEntity;
import static com.workflow.project.service.ProjectGovernanceValues.requireText;
import static com.workflow.project.service.ProjectGovernanceValues.text;
import static com.workflow.project.service.ProjectGovernanceValues.update;
import static com.workflow.project.service.ProjectGovernanceValues.upper;

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
    private static final Set<String> ACTIVE_MEMBER_STATUSES = Set.of(
            "PENDING_JOIN", "ACTIVE", "SUSPENDED",
            "PENDING_LEAVE");
    private static final Set<String> ALLOWED_PROJECT_STATUSES = Set.of(
            "APPROVED", "ACTIVE", "PAUSED", "ACCEPTING");

    private final EntityDataDynamicService entityDataService;
    private final ProjectEntityMutationExecutor mutationExecutor;
    private final ObjectMapper objectMapper;

    /**
     * 启动流程前执行完整跨实体校验并固化快照和路由变量。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> validateChange(
            EntityDataDTO request,
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

    Map<String, Object> validateChange(EntityDataDTO request) {
        return mutationExecutor.inSession(
                null,
                "MEMBER_CHANGE_PRECHECK",
                "项目成员变更前置校验",
                () -> validateChangeInternal(request));
    }

    private Map<String, Object> validateChangeInternal(
            EntityDataDTO request) {
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
        EntityDataDTO project =
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

        EntityDataDTO member = null;
        String targetUserId;
        BigDecimal requestedAllocation = BigDecimal.ZERO;
        List<EntityDataDTO> activeRoles = List.of();
        boolean accessReviewRequired;
        boolean securityReviewRequired;
        boolean handoverRequired = false;

        if ("JOIN".equals(operation)) {
            targetUserId = requireText(
                    requestData, "target_user_id", "加入人员不能为空");
            requireText(
                    requestData, "source_dept_id", "来源部门不能为空");
            String employmentType = requireText(
                    requestData, "employment_type", "人员类型不能为空");
            requestedAllocation = allocation(
                    read(requestData, "new_allocation_percentage"));
            ensureMemberDoesNotExist(projectId, targetUserId);
            ensureAllocationAvailable(
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
                    requiresSecurityReview(requestData);
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
                requestedAllocation = allocation(read(
                        requestData,
                        "new_allocation_percentage"));
                ensureAllocationAvailable(
                        targetUserId,
                        member.getId(),
                        requestedAllocation);
            }

            activeRoles = activeRoles(projectId, member.getId());
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
                    requiresSecurityReview(requestData)
                            || bool(read(
                            data(member),
                            "environment_access_required_flag"));
        }

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

        Map<String, Object> snapshotUpdate =
                new LinkedHashMap<>();
        snapshotUpdate.put(
                "target_user_id", targetUserId);
        snapshotUpdate.put(
                "before_snapshot",
                json(member == null ? Map.of() : member));
        snapshotUpdate.put(
                "after_snapshot",
                json(proposedMemberData(
                        operation,
                        requestData,
                        member)));
        snapshotUpdate.put(
                "conflict_check_result", json(result));
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
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> captureManagerReview(
            EntityDataDTO request,
            FlowActionContext context) {
        return mutationExecutor.inSession(
                context,
                "MEMBER_MANAGER_REVIEWED",
                "项目经理完成成员变更复核",
                () -> {
                    requireEntity(request, REQUEST);
                    LocalDateTime reviewedAt =
                            LocalDateTime.now();
                    Map<String, Object> values =
                            new LinkedHashMap<>();
                    values.put(
                            "manager_reviewed_at",
                            reviewedAt);
                    values.put(
                            "manager_review_operator_id",
                            context.getOperatorId());
                    mutationExecutor.update(
                            REQUEST,
                            request.getId(),
                            Map.of("data", values));
                    return Map.of(
                            "requestId", request.getId(),
                            "reviewedAt", reviewedAt,
                            "operatorId",
                            String.valueOf(
                                    context.getOperatorId()));
                });
    }

    /**
     * 最终审批连线被选中时记录决策轨迹。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> recordDecision(
            EntityDataDTO request,
            FlowActionContext context,
            String decision) {
        return mutationExecutor.inSession(
                context,
                "MEMBER_DECISION_RECORDED",
                "记录项目成员变更决策",
                () -> {
                    requireEntity(request, REQUEST);
                    Map<String, Object> trace =
                            new LinkedHashMap<>();
                    trace.put(
                            "decision",
                            StringUtils.hasText(decision)
                                    ? decision : "UNKNOWN");
                    trace.put(
                            "sequenceFlowId",
                            context.getSequenceFlowId());
                    trace.put(
                            "sourceNodeId",
                            context.getSourceNodeId());
                    trace.put(
                            "targetNodeId",
                            context.getTargetNodeId());
                    trace.put(
                            "operatorId",
                            context.getOperatorId());
                    trace.put(
                            "recordedAt",
                            LocalDateTime.now());
                    mutationExecutor.update(
                            REQUEST,
                            request.getId(),
                            Map.of(
                                    "data",
                                    Map.of(
                                            "decision_trace",
                                            json(trace))));
                    return trace;
                });
    }

    /**
     * 流程批准后幂等生效成员和角色变化。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> applyChange(
            EntityDataDTO request,
            FlowActionContext context) {
        return mutationExecutor.inSession(
                context,
                "CHANGE_EFFECTIVE",
                "项目成员变更审批生效",
                () -> applyChangeInternal(request));
    }

    Map<String, Object> applyChange(EntityDataDTO request) {
        return mutationExecutor.inSession(
                null,
                "CHANGE_EFFECTIVE",
                "项目成员变更审批生效",
                () -> applyChangeInternal(request));
    }

    private Map<String, Object> applyChangeInternal(
            EntityDataDTO request) {
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
        EntityDataDTO member;
        int transferredRoleCount = 0;

        switch (operation) {
            case "JOIN" -> member =
                    createMember(
                            request,
                            projectId,
                            effectiveDate);
            case "LEAVE" -> {
                member = requireTargetMember(
                        requestData, projectId);
                transferredRoleCount =
                        applyLeave(
                                request,
                                member,
                                effectiveDate);
            }
            case "SUSPEND" -> {
                member = requireTargetMember(
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
                member = requireTargetMember(
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
                member = requireTargetMember(
                        requestData, projectId);
                mutationExecutor.update(
                        PROJECT_MEMBER,
                        member.getId(),
                        update(
                                member.getStatus(),
                                Map.of(
                                        "allocation_percentage",
                                        allocation(read(
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

    private EntityDataDTO createMember(
            EntityDataDTO request,
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
                allocation(read(
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

        EntityDataDTO dto = new EntityDataDTO();
        dto.setEntityCode(PROJECT_MEMBER);
        dto.setName(
                "项目成员-"
                        + text(read(source, "target_user_id")));
        dto.setSubmitterId(request.getSubmitterId());
        dto.setSubmitterName(
                request.getSubmitterName());
        dto.setData(memberData);
        EntityDataDTO created =
                mutationExecutor.save(dto);
        mutationExecutor.update(
                PROJECT_MEMBER,
                created.getId(),
                update(
                        "ACTIVE",
                        Map.of("source_process", "F07")));
        return created;
    }

    private int applyLeave(
            EntityDataDTO request,
            EntityDataDTO member,
            LocalDate effectiveDate) {
        Map<String, Object> requestData = data(request);
        String projectId = text(read(
                requestData, "project_id"));
        List<EntityDataDTO> activeRoles =
                activeRoles(projectId, member.getId());
        String handoverMemberId = text(read(
                requestData, "handover_member_id"));
        EntityDataDTO handoverMember =
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
        for (EntityDataDTO assignment : activeRoles) {
            transferRole(
                    request,
                    assignment,
                    handoverMember,
                    effectiveDate);
        }
        return activeRoles.size();
    }

    private void transferRole(
            EntityDataDTO request,
            EntityDataDTO assignment,
            EntityDataDTO handoverMember,
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

        EntityDataDTO replacement =
                new EntityDataDTO();
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
        EntityDataDTO created =
                mutationExecutor.save(replacement);
        mutationExecutor.update(
                PROJECT_ROLE_ASSIGNMENT,
                created.getId(),
                update(
                        "ACTIVE",
                        Map.of("source_process", "F07")));
    }

    private void updateMemberAndRoles(
            EntityDataDTO member,
            String memberStatus,
            String roleStatus,
            Map<String, Object> customData) {
        mutationExecutor.update(
                PROJECT_MEMBER,
                member.getId(),
                update(memberStatus, customData));
        for (EntityDataDTO role :
                rolesForMember(member.getId())) {
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

    private void validateProjectDate(
            EntityDataDTO project,
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

    private void validateAccessScope(
            Map<String, Object> requestData) {
        if (bool(read(
                requestData,
                "environment_access_required_flag"))
                && !hasValues(read(
                requestData,
                "environment_scope"))) {
            conflict(
                    "PROJECT_MEMBER_ENVIRONMENT_SCOPE_REQUIRED",
                    "申请环境权限时必须选择环境范围");
        }
    }

    private void validateOperationStatus(
            String operation,
            EntityDataDTO member) {
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

    private void validateEffectiveDate(
            EntityDataDTO member,
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

    private void validateLeave(
            Map<String, Object> requestData,
            String projectId,
            EntityDataDTO member,
            LocalDate effectiveDate,
            boolean handoverRequired,
            List<EntityDataDTO> activeRoles) {
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
            EntityDataDTO handover =
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
                this::isPrimaryManagerRole)
                && !StringUtils.hasText(handoverMemberId)) {
            conflict(
                    "PROJECT_MEMBER_PRIMARY_ROLE_BLOCKED",
                    "唯一主负责人离场前必须完成角色交接");
        }
    }

    private void ensureMemberDoesNotExist(
            String projectId,
            String userId) {
        boolean exists = entityDataService.findByCondition(
                        PROJECT_MEMBER,
                        Map.of(
                                "project_id", projectId,
                                "user_id", userId))
                .stream()
                .anyMatch(item ->
                        ACTIVE_MEMBER_STATUSES.contains(
                                item.getStatus()));
        if (exists) {
            conflict(
                    "PROJECT_MEMBER_DUPLICATE",
                    "该人员已在项目成员范围内");
        }
    }

    private void ensureAllocationAvailable(
            String userId,
            String excludedMemberId,
            BigDecimal requestedAllocation) {
        BigDecimal current = entityDataService.findByCondition(
                        PROJECT_MEMBER,
                        Map.of("user_id", userId))
                .stream()
                .filter(item -> ACTIVE_MEMBER_STATUSES
                        .contains(item.getStatus()))
                .filter(item -> !Objects.equals(
                        excludedMemberId,
                        item.getId()))
                .map(item -> decimal(read(
                        data(item),
                        "allocation_percentage")))
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add);
        if (current.add(requestedAllocation)
                .compareTo(new BigDecimal("100")) > 0) {
            conflict(
                    "PROJECT_MEMBER_ALLOCATION_EXCEEDED",
                    "人员跨项目有效投入比例超过100%");
        }
    }

    private BigDecimal allocation(Object value) {
        BigDecimal result = decimal(value);
        if (result.compareTo(
                new BigDecimal("0.01")) < 0
                || result.compareTo(
                new BigDecimal("100")) > 0) {
            conflict(
                    "PROJECT_MEMBER_ALLOCATION_INVALID",
                    "投入比例必须在0.01%至100%之间");
        }
        return result;
    }

    private EntityDataDTO requireTargetMember(
            Map<String, Object> requestData,
            String projectId) {
        String memberId = requireText(
                requestData,
                "project_member_id",
                "目标项目成员不能为空");
        EntityDataDTO member =
                entityDataService.findById(
                        PROJECT_MEMBER, memberId);
        if (!Objects.equals(
                projectId,
                text(read(
                        data(member), "project_id")))) {
            conflict(
                    "PROJECT_MEMBER_PROJECT_MISMATCH",
                    "目标成员不属于所选项目");
        }
        return member;
    }

    private List<EntityDataDTO> activeRoles(
            String projectId,
            String memberId) {
        return entityDataService.findByCondition(
                        PROJECT_ROLE_ASSIGNMENT,
                        Map.of(
                                "project_id", projectId,
                                "member_id", memberId))
                .stream()
                .filter(item ->
                        "ACTIVE".equals(item.getStatus()))
                .toList();
    }

    private List<EntityDataDTO> rolesForMember(
            String memberId) {
        return entityDataService.findByCondition(
                PROJECT_ROLE_ASSIGNMENT,
                Map.of("member_id", memberId));
    }

    private boolean isPrimaryManagerRole(
            EntityDataDTO assignment) {
        Map<String, Object> values = data(assignment);
        return "PROJECT_MANAGER".equals(
                upper(read(values, "role_code")))
                && bool(read(values, "primary_flag"));
    }

    private boolean requiresSecurityReview(
            Map<String, Object> values) {
        if (bool(read(
                values,
                "sensitive_access_flag"))) {
            return true;
        }
        Object scope = read(values, "environment_scope");
        if (scope instanceof List<?> list) {
            return list.stream().map(String::valueOf)
                    .anyMatch("PROD_OPERATE"::equals);
        }
        return scope != null
                && String.valueOf(scope)
                .contains("PROD_OPERATE");
    }

    private boolean hasValues(Object value) {
        if (value instanceof List<?> list) {
            return !list.isEmpty();
        }
        return StringUtils.hasText(text(value));
    }

    private Map<String, Object> proposedMemberData(
            String operation,
            Map<String, Object> requestData,
            EntityDataDTO member) {
        Map<String, Object> result =
                new LinkedHashMap<>();
        if (member != null) {
            result.putAll(data(member));
        }
        result.put("operation_type", operation);
        result.put(
                "effective_date",
                read(requestData, "effective_date"));
        for (String field : List.of(
                "target_user_id",
                "source_dept_id",
                "employment_type",
                "planned_leave_date",
                "new_allocation_percentage",
                "account_required_flag",
                "environment_access_required_flag",
                "environment_scope",
                "sensitive_access_flag",
                "handover_member_id",
                "handover_description",
                "permission_revoke_deadline")) {
            Object value = read(requestData, field);
            if (value != null) {
                result.put(field, value);
            }
        }
        return result;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "项目成员变更快照序列化失败",
                    exception);
        }
    }
}
