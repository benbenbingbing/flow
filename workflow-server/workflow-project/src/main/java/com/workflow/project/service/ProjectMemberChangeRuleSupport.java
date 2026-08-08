package com.workflow.project.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import static com.workflow.project.service.ProjectGovernanceValues.bool;
import static com.workflow.project.service.ProjectGovernanceValues.conflict;
import static com.workflow.project.service.ProjectGovernanceValues.data;
import static com.workflow.project.service.ProjectGovernanceValues.decimal;
import static com.workflow.project.service.ProjectGovernanceValues.read;
import static com.workflow.project.service.ProjectGovernanceValues.text;
import static com.workflow.project.service.ProjectGovernanceValues.upper;

/** Shared member, role and allocation invariants for the change service. */
@Component
final class ProjectMemberChangeRuleSupport {

    private static final String PROJECT_MEMBER = "project_member";
    private static final String PROJECT_ROLE_ASSIGNMENT = "project_role_assignment";
    private static final Set<String> ACTIVE_MEMBER_STATUSES = Set.of(
            "PENDING_JOIN", "ACTIVE", "SUSPENDED", "PENDING_LEAVE");

    private final EntityDataDynamicService entityDataService;
    private final ObjectMapper objectMapper;

    ProjectMemberChangeRuleSupport(
            EntityDataDynamicService entityDataService,
            ObjectMapper objectMapper) {
        this.entityDataService = entityDataService;
        this.objectMapper = objectMapper;
    }

    void ensureMemberDoesNotExist(String projectId, String userId) {
        boolean exists = entityDataService.findByCondition(
                        PROJECT_MEMBER, Map.of("project_id", projectId, "user_id", userId))
                .stream().anyMatch(item -> ACTIVE_MEMBER_STATUSES.contains(item.getStatus()));
        if (exists) conflict("PROJECT_MEMBER_DUPLICATE", "该人员已在项目成员范围内");
    }

    void ensureAllocationAvailable(
            String userId, String excludedMemberId, BigDecimal requestedAllocation) {
        BigDecimal current = entityDataService.findByCondition(
                        PROJECT_MEMBER, Map.of("user_id", userId)).stream()
                .filter(item -> ACTIVE_MEMBER_STATUSES.contains(item.getStatus()))
                .filter(item -> !Objects.equals(excludedMemberId, item.getId()))
                .map(item -> decimal(read(data(item), "allocation_percentage")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (current.add(requestedAllocation).compareTo(new BigDecimal("100")) > 0) {
            conflict("PROJECT_MEMBER_ALLOCATION_EXCEEDED", "人员跨项目有效投入比例超过100%");
        }
    }

    BigDecimal allocation(Object value) {
        BigDecimal result = decimal(value);
        if (result.compareTo(new BigDecimal("0.01")) < 0
                || result.compareTo(new BigDecimal("100")) > 0) {
            conflict("PROJECT_MEMBER_ALLOCATION_INVALID", "投入比例必须在0.01%至100%之间");
        }
        return result;
    }

    EntityDataDTO requireTargetMember(Map<String, Object> requestData, String projectId) {
        String memberId = ProjectGovernanceValues.requireText(
                requestData, "project_member_id", "目标项目成员不能为空");
        EntityDataDTO member = entityDataService.findById(PROJECT_MEMBER, memberId);
        if (!Objects.equals(projectId, text(read(data(member), "project_id")))) {
            conflict("PROJECT_MEMBER_PROJECT_MISMATCH", "目标成员不属于所选项目");
        }
        return member;
    }

    List<EntityDataDTO> activeRoles(String projectId, String memberId) {
        return entityDataService.findByCondition(
                        PROJECT_ROLE_ASSIGNMENT,
                        Map.of("project_id", projectId, "member_id", memberId)).stream()
                .filter(item -> "ACTIVE".equals(item.getStatus())).toList();
    }

    List<EntityDataDTO> rolesForMember(String memberId) {
        return entityDataService.findByCondition(
                PROJECT_ROLE_ASSIGNMENT, Map.of("member_id", memberId));
    }

    boolean isPrimaryManagerRole(EntityDataDTO assignment) {
        Map<String, Object> values = data(assignment);
        return "PROJECT_MANAGER".equals(upper(read(values, "role_code")))
                && bool(read(values, "primary_flag"));
    }

    boolean requiresSecurityReview(Map<String, Object> values) {
        if (bool(read(values, "sensitive_access_flag"))) return true;
        Object scope = read(values, "environment_scope");
        if (scope instanceof List<?> list) {
            return list.stream().map(String::valueOf).anyMatch("PROD_OPERATE"::equals);
        }
        return scope != null && String.valueOf(scope).contains("PROD_OPERATE");
    }

    boolean hasValues(Object value) {
        if (value instanceof List<?> list) return !list.isEmpty();
        return StringUtils.hasText(text(value));
    }

    Map<String, Object> proposedMemberData(
            String operation, Map<String, Object> requestData, EntityDataDTO member) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (member != null) result.putAll(data(member));
        result.put("operation_type", operation);
        result.put("effective_date", read(requestData, "effective_date"));
        for (String field : List.of(
                "target_user_id", "source_dept_id", "employment_type", "planned_leave_date",
                "new_allocation_percentage", "account_required_flag",
                "environment_access_required_flag", "environment_scope", "sensitive_access_flag",
                "handover_member_id", "handover_description", "permission_revoke_deadline")) {
            Object value = read(requestData, field);
            if (value != null) result.put(field, value);
        }
        return result;
    }

    String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("项目成员变更快照序列化失败", exception);
        }
    }
}
