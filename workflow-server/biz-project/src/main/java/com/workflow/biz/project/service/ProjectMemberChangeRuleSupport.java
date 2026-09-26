package com.workflow.biz.project.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.model.EntityRecordData;
import com.workflow.contracts.entity.port.EntityRecordQueryPort;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import static com.workflow.biz.project.service.ProjectGovernanceValues.bool;
import static com.workflow.biz.project.service.ProjectGovernanceValues.conflict;
import static com.workflow.biz.project.service.ProjectGovernanceValues.data;
import static com.workflow.biz.project.service.ProjectGovernanceValues.decimal;
import static com.workflow.biz.project.service.ProjectGovernanceValues.read;
import static com.workflow.biz.project.service.ProjectGovernanceValues.text;
import static com.workflow.biz.project.service.ProjectGovernanceValues.upper;

/** Shared member, role and allocation invariants for the change service. */
@Component
final class ProjectMemberChangeRuleSupport {

    private static final String PROJECT_MEMBER = "project_member";
    private static final String PROJECT_ROLE_ASSIGNMENT = "project_role_assignment";
    private static final Set<String> ACTIVE_MEMBER_STATUSES = Set.of(
            "PENDING_JOIN", "ACTIVE", "SUSPENDED", "PENDING_LEAVE");

    private final EntityRecordQueryPort entityDataService;
    private final ObjectMapper objectMapper;

    /**
     * 初始化项目成员变更规则支持，保存构造参数供后续方法使用。
     *
     * @param entityDataService 实体数据服务依赖，保存到当前对象供后续业务方法调用
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     */
    ProjectMemberChangeRuleSupport(
            EntityRecordQueryPort entityDataService,
            ObjectMapper objectMapper) {
        this.entityDataService = entityDataService;
        this.objectMapper = objectMapper;
    }

    /**
     * 确保成员{@code does}非{@code exist}；不满足约束时阻止后续处理。
     *
     * @param projectId 项目ID，后续用于确保成员{@code does}非{@code exist}时定位或关联目标
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     */
    void ensureMemberDoesNotExist(String projectId, String userId) {
        boolean exists = entityDataService.findByCondition(
                        PROJECT_MEMBER, Map.of("project_id", projectId, "user_id", userId))
                .stream().anyMatch(item -> ACTIVE_MEMBER_STATUSES.contains(item.getStatus()));
        if (exists) conflict("PROJECT_MEMBER_DUPLICATE", "该人员已在项目成员范围内");
    }

    /**
     * 确保{@code allocation}可用；不满足约束时阻止后续处理。
     *
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param excludedMemberId {@code excluded}成员ID，后续用于确保{@code allocation}可用时定位或关联目标
     * @param requestedAllocation 请求{@code allocation}，供本方法确保{@code allocation}可用时使用
     */
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

    /**
     * 处理{@code allocation}，并将结果传给后续步骤。
     *
     * @param value 待处理{@code allocation}的原始输入，结果供调用方继续使用
     * @return 处理后的{@code allocation}结果，供调用方继续处理
     */
    BigDecimal allocation(Object value) {
        BigDecimal result = decimal(value);
        if (result.compareTo(new BigDecimal("0.01")) < 0
                || result.compareTo(new BigDecimal("100")) > 0) {
            conflict("PROJECT_MEMBER_ALLOCATION_INVALID", "投入比例必须在0.01%至100%之间");
        }
        return result;
    }

    /**
     * 校验并获取目标成员；不满足约束时阻止后续处理。
     *
     * @param requestData 请求数据，作为 {@code ProjectGovernanceValues.requireText} 的输入影响后续处理
     * @param projectId 项目ID，后续用于校验并获取目标成员时定位或关联目标
     * @return 校验并获取后的目标成员结果，供调用方继续处理
     */
    EntityRecordData requireTargetMember(Map<String, Object> requestData, String projectId) {
        String memberId = ProjectGovernanceValues.requireText(
                requestData, "project_member_id", "目标项目成员不能为空");
        EntityRecordData member = entityDataService.findById(PROJECT_MEMBER, memberId);
        if (!Objects.equals(projectId, text(read(data(member), "project_id")))) {
            conflict("PROJECT_MEMBER_PROJECT_MISMATCH", "目标成员不属于所选项目");
        }
        return member;
    }

    /**
     * 整理活动角色集合数据，供调用方遍历或继续处理。
     *
     * @param projectId 项目ID，后续用于处理活动角色集合时定位或关联目标
     * @param memberId 成员ID，后续用于处理活动角色集合时定位或关联目标
     * @return 实体数据集合，供调用方遍历或展示
     */
    List<EntityRecordData> activeRoles(String projectId, String memberId) {
        return entityDataService.findByCondition(
                        PROJECT_ROLE_ASSIGNMENT,
                        Map.of("project_id", projectId, "member_id", memberId)).stream()
                .filter(item -> "ACTIVE".equals(item.getStatus())).toList();
    }

    /**
     * 整理角色集合成员数据，供调用方遍历或继续处理。
     *
     * @param memberId 成员ID，后续用于处理角色集合成员时定位或关联目标
     * @return 实体数据集合，供调用方遍历或展示
     */
    List<EntityRecordData> rolesForMember(String memberId) {
        return entityDataService.findByCondition(
                PROJECT_ROLE_ASSIGNMENT, Map.of("member_id", memberId));
    }

    /**
     * 判断是否主要{@code manager}角色；判断结果决定调用方的后续分支。
     *
     * @param assignment 分配，作为 {@code data} 的输入影响后续处理
     * @return 主要{@code manager}角色条件成立时为 true，否则为 false
     */
    boolean isPrimaryManagerRole(EntityRecordData assignment) {
        Map<String, Object> values = data(assignment);
        return "PROJECT_MANAGER".equals(upper(read(values, "role_code")))
                && bool(read(values, "primary_flag"));
    }

    /**
     * 判断需要安全{@code review}条件是否成立，供调用方选择后续分支。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 需要安全{@code review}条件成立时为 true，否则为 false
     */
    boolean requiresSecurityReview(Map<String, Object> values) {
        if (bool(read(values, "sensitive_access_flag"))) return true;
        Object scope = read(values, "environment_scope");
        if (scope instanceof List<?> list) {
            return list.stream().map(String::valueOf).anyMatch("PROD_OPERATE"::equals);
        }
        return scope != null && String.valueOf(scope).contains("PROD_OPERATE");
    }

    /**
     * 判断是否具有值集合；判断结果决定调用方的后续分支。
     *
     * @param value 待判断是否具有值集合的原始输入，结果供调用方继续使用
     * @return 值集合条件成立时为 true，否则为 false
     */
    boolean hasValues(Object value) {
        if (value instanceof List<?> list) return !list.isEmpty();
        return StringUtils.hasText(text(value));
    }

    /**
     * 整理{@code proposed}成员数据数据，供调用方遍历或继续处理。
     *
     * @param operation 操作标识，决定后续{@code proposed}成员数据采用的处理分支
     * @param requestData 请求数据，作为 {@code result.put} 的输入影响后续处理
     * @param member 成员，供本方法处理{@code proposed}成员数据时使用
     * @return {@code proposed}成员数据键值结果，供调用方继续处理
     */
    Map<String, Object> proposedMemberData(
            String operation, Map<String, Object> requestData, EntityRecordData member) {
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

    /**
     * 生成JSON文本，供后续匹配或展示。
     *
     * @param value 待处理JSON的原始输入，结果供调用方继续使用
     * @return 处理后的JSON文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("项目成员变更快照序列化失败", exception);
        }
    }
}
