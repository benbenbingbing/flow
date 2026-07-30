package com.workflow.project.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.action.FlowActionContext;
import com.workflow.core.error.BusinessConflictException;
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
import java.util.stream.Stream;

import static com.workflow.project.service.ProjectGovernanceValues.*;

/**
 * Project-specific cross-entity rules that cannot be expressed by form and BPMN configuration.
 */
@Service
@RequiredArgsConstructor
public class ProjectGovernanceService {

    private static final String PROJECT = "project";
    private static final String REQUIREMENT = "requirement";
    private static final String REQUIREMENT_PROJECT_LINK = "requirement_project_link";
    private static final String SYSTEM_ASSET = "system_asset";
    private static final String PROJECT_SYSTEM_LINK = "project_system_link";
    private static final String PROJECT_MEMBER = "project_member";
    private static final String PROJECT_ROLE_CATALOG = "project_role_catalog";
    private static final String PROJECT_ROLE_ASSIGNMENT = "project_role_assignment";
    private static final String PROJECT_SYSTEM_CHANGE = "project_system_change_request";

    private static final Set<String> ALLOWED_REQUIREMENT_STATUSES =
            Set.of("BACKLOG", "PLANNED");
    private static final Set<String> ALLOWED_PROJECT_STATUSES =
            Set.of("APPROVED", "ACTIVE", "PAUSED", "ACCEPTING");
    private static final Set<String> TERMINAL_LINK_STATUSES =
            Set.of("INVALID", "REJECTED", "CANCELLED");
    private final EntityDataDynamicService entityDataService;
    private final ProjectEntityMutationExecutor mutationExecutor;
    private final ProjectSystemRemovalGuard removalGuard;
    private final ObjectMapper objectMapper;

    /**
     * Validates project initiation against requirement, system, allocation and date data.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> validateProjectInitiation(EntityDataDTO project) {
        return validateProjectInitiationInternal(project);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> validateProjectInitiation(
            EntityDataDTO project,
            FlowActionContext context) {
        return mutationExecutor.inSession(
                context,
                "PROJECT_INITIATION_VALIDATION",
                "项目立项校验",
                () -> validateProjectInitiationInternal(project));
    }

    private Map<String, Object> validateProjectInitiationInternal(
            EntityDataDTO project) {
        requireEntity(project, PROJECT);
        Map<String, Object> data = data(project);

        LocalDate plannedStart = date(read(data, "planned_start_date"));
        LocalDate plannedEnd = date(read(data, "planned_end_date"));
        if (plannedStart == null || plannedEnd == null || plannedEnd.isBefore(plannedStart)) {
            conflict("PROJECT_DATE_RANGE_INVALID", "项目计划结束日期不得早于开始日期");
        }

        requireText(data, "project_manager_id", "项目经理不能为空");
        requireText(data, "product_owner_id", "产品负责人不能为空");
        requireText(data, "business_owner_id", "业务负责人不能为空");
        requireText(data, "project_sponsor_id", "项目发起人不能为空");

        List<Map<String, Object>> requirementLinks =
                rows(read(data, "initial_requirement_links"));
        if (requirementLinks.isEmpty()) {
            conflict("PROJECT_REQUIREMENT_REQUIRED", "项目至少关联一条已入池需求");
        }
        validateRequirementLinks(requirementLinks, plannedStart, plannedEnd);

        String projectType = text(read(data, "project_type"));
        List<Map<String, Object>> systemLinks = rows(read(data, "initial_system_links"));
        if (!"RESEARCH".equals(projectType) && systemLinks.isEmpty()) {
            conflict("PROJECT_SYSTEM_REQUIRED", "非研究咨询项目至少关联一个有效系统");
        }
        validateSystemLinks(systemLinks, plannedStart, plannedEnd);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requirementCount", requirementLinks.size());
        result.put("systemCount", systemLinks.size());
        result.put("projectType", projectType);
        result.put("checkedAt", LocalDateTime.now());
        return result;
    }

    /**
     * Activates initial links and creates the three governed members and role assignments.
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> applyProjectInitiation(EntityDataDTO project) {
        return mutationExecutor.inSession(
                null,
                "INITIAL_EFFECTIVE",
                "项目立项审批生效",
                () -> applyProjectInitiationInternal(project));
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> applyProjectInitiation(
            EntityDataDTO project,
            FlowActionContext context) {
        return mutationExecutor.inSession(
                context,
                "INITIAL_EFFECTIVE",
                "项目立项审批生效",
                () -> applyProjectInitiationInternal(project));
    }

    private Map<String, Object> applyProjectInitiationInternal(
            EntityDataDTO project) {
        requireEntity(project, PROJECT);
        Map<String, Object> data = data(project);
        if (bool(read(data, "initialization_completed_flag"))) {
            return Map.of(
                    "projectId", project.getId(),
                    "reused", true,
                    "summary", text(read(data, "initialization_summary")));
        }

        String projectId = project.getId();
        String sourceDeptId = firstNonBlank(
                text(read(data, "sponsor_dept_id")),
                text(read(data, "applicant_dept_id")),
                project.getDeptId());
        LocalDate joinDate = firstNonNull(
                date(read(data, "planned_start_date")),
                LocalDate.now());
        LocalDate plannedLeaveDate = date(read(data, "planned_end_date"));

        EntityDataDTO managerMember = ensureMember(
                project,
                text(read(data, "project_manager_id")),
                sourceDeptId,
                joinDate,
                plannedLeaveDate,
                "项目经理");
        EntityDataDTO businessMember = ensureMember(
                project,
                text(read(data, "business_owner_id")),
                sourceDeptId,
                joinDate,
                plannedLeaveDate,
                "业务负责人");
        EntityDataDTO productMember = ensureMember(
                project,
                text(read(data, "product_owner_id")),
                sourceDeptId,
                joinDate,
                plannedLeaveDate,
                "产品负责人");

        ensureRoleAssignment(
                project,
                managerMember,
                "PROJECT_MANAGER",
                "项目经理",
                true,
                joinDate,
                plannedLeaveDate);
        ensureRoleAssignment(
                project,
                businessMember,
                "BUSINESS_OWNER",
                "业务负责人",
                true,
                joinDate,
                plannedLeaveDate);
        ensureRoleAssignment(
                project,
                productMember,
                "PRODUCT_OWNER",
                "产品负责人",
                true,
                joinDate,
                plannedLeaveDate);

        List<EntityDataDTO> requirementLinks =
                entityDataService.findByCondition(
                        REQUIREMENT_PROJECT_LINK,
                        Map.of("project_id", projectId));
        for (EntityDataDTO link : requirementLinks) {
            Map<String, Object> custom = new LinkedHashMap<>();
            if (!StringUtils.hasText(text(read(data(link), "responsible_member_id")))) {
                custom.put("responsible_member_id", managerMember.getId());
            }
            mutationExecutor.update(
                    REQUIREMENT_PROJECT_LINK,
                    link.getId(),
                    update("APPROVED", custom));
        }

        LocalDateTime effectiveAt = LocalDateTime.now();
        List<EntityDataDTO> systemLinks =
                entityDataService.findByCondition(
                        PROJECT_SYSTEM_LINK,
                        Map.of("project_id", projectId));
        for (EntityDataDTO link : systemLinks) {
            Map<String, Object> custom = new LinkedHashMap<>();
            if (!StringUtils.hasText(text(read(data(link), "project_system_lead_id")))) {
                custom.put("project_system_lead_id", managerMember.getId());
            }
            if (!StringUtils.hasText(text(read(data(link), "technical_lead_id")))) {
                custom.put("technical_lead_id", managerMember.getId());
            }
            custom.put("effective_at", effectiveAt);
            custom.put("source_process", "F03");
            mutationExecutor.update(
                    PROJECT_SYSTEM_LINK,
                    link.getId(),
                    update("ACTIVE", custom));
        }

        List<String> memberIds = Stream.of(
                        managerMember.getId(),
                        businessMember.getId(),
                        productMember.getId())
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        String summary = "初始化需求关系%d条、系统关系%d条、关键成员%d名、关键角色3条"
                .formatted(requirementLinks.size(), systemLinks.size(), memberIds.size());
        Map<String, Object> projectUpdate = new LinkedHashMap<>();
        projectUpdate.put("initialization_completed_flag", true);
        projectUpdate.put("initialization_summary", summary);
        mutationExecutor.update(
                PROJECT,
                projectId,
                update("APPROVED", projectUpdate));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("requirementLinkCount", requirementLinks.size());
        result.put("systemLinkCount", systemLinks.size());
        result.put("memberIds", memberIds);
        result.put("summary", summary);
        result.put("reused", false);
        return result;
    }

    /**
     * Validates ADD, UPDATE and REMOVE requests. Removal performs real cross-entity blocker checks.
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> validateProjectSystemChange(EntityDataDTO request) {
        return mutationExecutor.inSession(
                null,
                "CHANGE_PRECHECK",
                "项目系统变更前置校验",
                () -> validateProjectSystemChangeInternal(request));
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> validateProjectSystemChange(
            EntityDataDTO request,
            FlowActionContext context) {
        return mutationExecutor.inSession(
                context,
                "CHANGE_PRECHECK",
                "项目系统变更前置校验",
                () -> validateProjectSystemChangeInternal(request));
    }

    private Map<String, Object> validateProjectSystemChangeInternal(
            EntityDataDTO request) {
        requireEntity(request, PROJECT_SYSTEM_CHANGE);
        Map<String, Object> data = data(request);
        String operation = upper(read(data, "operation_type"));
        if (!Set.of("ADD", "UPDATE", "REMOVE").contains(operation)) {
            conflict("PROJECT_SYSTEM_OPERATION_INVALID", "项目系统关系操作类型不合法");
        }

        String projectId = requireText(data, "project_id", "项目不能为空");
        String systemId = requireText(data, "system_id", "系统不能为空");
        EntityDataDTO project = entityDataService.findById(PROJECT, projectId);
        if (!ALLOWED_PROJECT_STATUSES.contains(project.getStatus())) {
            conflict(
                    "PROJECT_STATUS_NOT_ALLOWED",
                    "项目必须处于已批准、进行中、暂停或验收中状态");
        }
        EntityDataDTO system = entityDataService.findById(SYSTEM_ASSET, systemId);
        if ("RETIRED".equals(system.getStatus())) {
            conflict("SYSTEM_RETIRED", "已退役系统不能加入或调整项目范围");
        }

        String linkId = text(read(data, "project_system_link_id"));
        EntityDataDTO sourceLink = null;
        if ("ADD".equals(operation)) {
            List<EntityDataDTO> existing = entityDataService.findByCondition(
                    PROJECT_SYSTEM_LINK,
                    Map.of("project_id", projectId, "system_id", systemId));
            if (existing.stream().anyMatch(item ->
                    !TERMINAL_LINK_STATUSES.contains(item.getStatus()))) {
                conflict(
                        "PROJECT_SYSTEM_LINK_DUPLICATE",
                        "同一项目与系统已存在当前有效或待审批关系");
            }
        } else {
            if (!StringUtils.hasText(linkId)) {
                conflict(
                        "PROJECT_SYSTEM_LINK_REQUIRED",
                        "修改或移除时必须选择原项目系统关系");
            }
            sourceLink = entityDataService.findById(PROJECT_SYSTEM_LINK, linkId);
            if (!Objects.equals(projectId, text(read(data(sourceLink), "project_id")))
                    || !Objects.equals(systemId, text(read(data(sourceLink), "system_id")))) {
                conflict(
                        "PROJECT_SYSTEM_LINK_MISMATCH",
                        "原项目系统关系与申请中的项目、系统不一致");
            }
            if (!"ACTIVE".equals(sourceLink.getStatus())) {
                conflict(
                        "PROJECT_SYSTEM_LINK_NOT_ACTIVE",
                        "只有当前有效的项目系统关系可以修改或移除");
            }
        }

        if (!"REMOVE".equals(operation)) {
            requireText(data, "construction_mode", "新增或修改时建设方式不能为空");
            String systemLeadId = requireText(
                    data,
                    "new_project_system_lead_id",
                    "新增或修改时项目内系统负责人不能为空");
            String technicalLeadId = requireText(
                    data,
                    "new_technical_lead_id",
                    "新增或修改时项目内技术负责人不能为空");
            validateMember(projectId, systemLeadId, "项目内系统负责人");
            validateMember(projectId, technicalLeadId, "项目内技术负责人");
            validateDateRange(
                    read(data, "planned_start_date"),
                    read(data, "planned_end_date"),
                    "项目系统关系计划日期");
        }

        String riskLevel = upper(read(data, "risk_level"));
        if (Set.of("HIGH", "CRITICAL").contains(riskLevel)
                && !StringUtils.hasText(text(read(data, "rollback_plan")))) {
            conflict("ROLLBACK_PLAN_REQUIRED", "高风险或极高风险变更必须填写回滚方案");
        }

        List<String> blockers = "REMOVE".equals(operation)
                ? removalGuard.collectBlockers(
                        request.getId(),
                        projectId,
                        systemId,
                        linkId)
                : List.of();
        if (!blockers.isEmpty()) {
            conflict(
                    "PROJECT_SYSTEM_REMOVAL_BLOCKED",
                    "项目系统关系不能移除：" + String.join("；", blockers));
        }

        Map<String, Object> checkResult = new LinkedHashMap<>();
        checkResult.put("allowed", true);
        checkResult.put("operation", operation);
        checkResult.put("checkedAt", LocalDateTime.now());
        checkResult.put("blockers", blockers);
        checkResult.put("projectStatus", project.getStatus());
        checkResult.put("systemStatus", system.getStatus());

        Map<String, Object> snapshotUpdate = new LinkedHashMap<>();
        snapshotUpdate.put("before_snapshot", json(sourceLink == null
                ? Map.of()
                : sourceLink));
        snapshotUpdate.put("after_snapshot", json(buildProposedLinkData(data)));
        if ("REMOVE".equals(operation)) {
            snapshotUpdate.put("remove_dependency_result", json(checkResult));
        }
        mutationExecutor.update(
                PROJECT_SYSTEM_CHANGE,
                request.getId(),
                Map.of("data", snapshotUpdate));
        return checkResult;
    }

    /**
     * Applies the approved project-system relationship change idempotently.
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> applyProjectSystemChange(EntityDataDTO request) {
        return mutationExecutor.inSession(
                null,
                "CHANGE_EFFECTIVE",
                "变更审批生效",
                () -> applyProjectSystemChangeInternal(request));
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> applyProjectSystemChange(
            EntityDataDTO request,
            FlowActionContext context) {
        return mutationExecutor.inSession(
                context,
                "CHANGE_EFFECTIVE",
                "变更审批生效",
                () -> applyProjectSystemChangeInternal(request));
    }

    private Map<String, Object> applyProjectSystemChangeInternal(
            EntityDataDTO request) {
        requireEntity(request, PROJECT_SYSTEM_CHANGE);
        Map<String, Object> data = data(request);
        String existingEffectiveLinkId = text(read(data, "effective_link_id"));
        if ("EFFECTIVE".equals(request.getStatus())
                && StringUtils.hasText(existingEffectiveLinkId)) {
            return Map.of(
                    "requestId", request.getId(),
                    "projectSystemLinkId", existingEffectiveLinkId,
                    "reused", true);
        }

        String operation = upper(read(data, "operation_type"));
        String projectId = requireText(data, "project_id", "项目不能为空");
        String systemId = requireText(data, "system_id", "系统不能为空");
        LocalDateTime effectiveAt = LocalDateTime.now();
        EntityDataDTO effectiveLink;

        switch (operation) {
            case "ADD" -> {
                EntityDataDTO link = new EntityDataDTO();
                link.setEntityCode(PROJECT_SYSTEM_LINK);
                link.setName("项目系统关系-" + projectId + "-" + systemId);
                link.setSubmitterId(request.getSubmitterId());
                link.setSubmitterName(request.getSubmitterName());
                Map<String, Object> linkData = buildProposedLinkData(data);
                linkData.put("project_id", projectId);
                linkData.put("system_id", systemId);
                linkData.put("effective_at", effectiveAt);
                linkData.put("source_process", "F06");
                link.setData(linkData);
                effectiveLink = mutationExecutor.save(link);
                mutationExecutor.update(
                        PROJECT_SYSTEM_LINK,
                        effectiveLink.getId(),
                        update("ACTIVE", Map.of(
                                "effective_at", effectiveAt,
                                "source_process", "F06")));
            }
            case "UPDATE" -> {
                String linkId = requireText(
                        data,
                        "project_system_link_id",
                        "修改时原项目系统关系不能为空");
                Map<String, Object> linkData = buildProposedLinkData(data);
                linkData.put("effective_at", effectiveAt);
                linkData.put("source_process", "F06");
                mutationExecutor.update(
                        PROJECT_SYSTEM_LINK,
                        linkId,
                        update("ACTIVE", linkData));
                effectiveLink = entityDataService.findById(PROJECT_SYSTEM_LINK, linkId);
            }
            case "REMOVE" -> {
                String linkId = requireText(
                        data,
                        "project_system_link_id",
                        "移除时原项目系统关系不能为空");
                List<String> blockers =
                        removalGuard.collectBlockers(
                                request.getId(),
                                projectId,
                                systemId,
                                linkId);
                if (!blockers.isEmpty()) {
                    conflict(
                            "PROJECT_SYSTEM_REMOVAL_BLOCKED",
                            "批准后生效前检查发现新阻断：" + String.join("；", blockers));
                }
                Map<String, Object> invalidData = new LinkedHashMap<>();
                invalidData.put("invalid_at", effectiveAt);
                invalidData.put("invalid_reason", text(read(data, "change_reason")));
                invalidData.put("source_process", "F06");
                mutationExecutor.update(
                        PROJECT_SYSTEM_LINK,
                        linkId,
                        update("INVALID", invalidData));
                effectiveLink = entityDataService.findById(PROJECT_SYSTEM_LINK, linkId);
            }
            default -> throw new BusinessConflictException(
                    "PROJECT_SYSTEM_OPERATION_INVALID",
                    "项目系统关系操作类型不合法");
        }

        String resultText = switch (operation) {
            case "ADD" -> "已创建并激活项目系统关系";
            case "UPDATE" -> "已更新项目系统关系";
            case "REMOVE" -> "已失效项目系统关系";
            default -> "已处理项目系统关系";
        };
        Map<String, Object> requestData = new LinkedHashMap<>();
        requestData.put("effective_link_id", effectiveLink.getId());
        requestData.put("implementation_result", resultText);
        requestData.put("effective_at", effectiveAt);
        mutationExecutor.update(
                PROJECT_SYSTEM_CHANGE,
                request.getId(),
                update("EFFECTIVE", requestData));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", request.getId());
        result.put("operation", operation);
        result.put("projectSystemLinkId", effectiveLink.getId());
        result.put("effectiveAt", effectiveAt);
        result.put("reused", false);
        return result;
    }

    private void validateRequirementLinks(
            List<Map<String, Object>> links,
            LocalDate projectStart,
            LocalDate projectEnd) {
        for (Map<String, Object> link : links) {
            String requirementId = requireText(link, "requirement_id", "需求范围中存在空需求");
            EntityDataDTO requirement = entityDataService.findById(REQUIREMENT, requirementId);
            if (!ALLOWED_REQUIREMENT_STATUSES.contains(requirement.getStatus())) {
                conflict(
                        "REQUIREMENT_STATUS_NOT_ALLOWED",
                        "需求 " + requirement.getCode() + " 必须处于 BACKLOG 或 PLANNED 状态");
            }
            validateDateWithinProject(
                    link,
                    projectStart,
                    projectEnd,
                    "需求 " + requirement.getCode());

            BigDecimal total = entityDataService.findByCondition(
                            REQUIREMENT_PROJECT_LINK,
                            Map.of("requirement_id", requirementId))
                    .stream()
                    .filter(item -> !"CANCELLED".equals(item.getStatus()))
                    .map(item -> decimal(read(data(item), "allocation_percentage")))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.compareTo(new BigDecimal("100")) > 0) {
                conflict(
                        "REQUIREMENT_ALLOCATION_EXCEEDED",
                        "需求 " + requirement.getCode() + " 的有效项目分配比例超过100%");
            }
        }
    }

    private void validateSystemLinks(
            List<Map<String, Object>> links,
            LocalDate projectStart,
            LocalDate projectEnd) {
        for (Map<String, Object> link : links) {
            String systemId = requireText(link, "system_id", "系统范围中存在空系统");
            EntityDataDTO system = entityDataService.findById(SYSTEM_ASSET, systemId);
            if ("RETIRED".equals(system.getStatus())) {
                conflict("SYSTEM_RETIRED", "已退役系统不能纳入项目范围：" + system.getCode());
            }
            validateDateWithinProject(
                    link,
                    projectStart,
                    projectEnd,
                    "系统 " + system.getCode());
        }
    }

    private void validateDateWithinProject(
            Map<String, Object> link,
            LocalDate projectStart,
            LocalDate projectEnd,
            String label) {
        LocalDate linkStart = date(read(link, "planned_start_date"));
        LocalDate linkEnd = date(read(link, "planned_end_date"));
        if (linkStart == null || linkEnd == null || linkEnd.isBefore(linkStart)) {
            conflict("LINK_DATE_RANGE_INVALID", label + " 的计划日期不合法");
        }
        if (linkStart.isBefore(projectStart) || linkEnd.isAfter(projectEnd)) {
            conflict("LINK_DATE_OUTSIDE_PROJECT", label + " 的计划日期必须位于项目周期内");
        }
    }

    private EntityDataDTO ensureMember(
            EntityDataDTO project,
            String userId,
            String sourceDeptId,
            LocalDate joinDate,
            LocalDate plannedLeaveDate,
            String responsibility) {
        if (!StringUtils.hasText(userId)) {
            conflict("PROJECT_KEY_MEMBER_REQUIRED", responsibility + "不能为空");
        }
        List<EntityDataDTO> existing = entityDataService.findByCondition(
                PROJECT_MEMBER,
                Map.of("project_id", project.getId(), "user_id", userId));
        EntityDataDTO member = existing.stream()
                .filter(item -> Set.of("ACTIVE", "PENDING_JOIN", "SUSPENDED")
                        .contains(item.getStatus()))
                .findFirst()
                .orElse(null);
        if (member == null) {
            Map<String, Object> memberData = new LinkedHashMap<>();
            memberData.put("project_id", project.getId());
            memberData.put("user_id", userId);
            memberData.put("source_dept_id", sourceDeptId);
            memberData.put("employment_type", "INTERNAL");
            memberData.put("join_date", joinDate);
            memberData.put("planned_leave_date", plannedLeaveDate);
            memberData.put("allocation_percentage", new BigDecimal("100"));
            memberData.put("join_reason", "F03立项批准后自动初始化" + responsibility);
            memberData.put("account_required_flag", true);
            memberData.put("environment_access_required_flag", false);
            memberData.put("access_revoked_flag", false);
            memberData.put("handover_completed_flag", false);
            memberData.put("source_process", "F03");
            member = saveStandalone(
                    PROJECT_MEMBER,
                    project.getName() + "-" + responsibility,
                    project,
                    memberData);
        }
        mutationExecutor.update(
                PROJECT_MEMBER,
                member.getId(),
                update("ACTIVE", Map.of("source_process", "F03")));
        return member;
    }

    private void ensureRoleAssignment(
            EntityDataDTO project,
            EntityDataDTO member,
            String roleCode,
            String roleName,
            boolean primary,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {
        EntityDataDTO catalog = ensureRoleCatalog(project, roleCode, roleName, primary);
        List<EntityDataDTO> existing = entityDataService.findByCondition(
                PROJECT_ROLE_ASSIGNMENT,
                Map.of("project_id", project.getId(), "role_code", roleCode));
        if (existing.stream().anyMatch(item ->
                Set.of("ACTIVE", "PROPOSED").contains(item.getStatus()))) {
            return;
        }

        Map<String, Object> assignmentData = new LinkedHashMap<>();
        assignmentData.put("project_id", project.getId());
        assignmentData.put("member_id", member.getId());
        assignmentData.put("user_id", text(read(data(member), "user_id")));
        assignmentData.put("role_catalog_id", catalog.getId());
        assignmentData.put("role_code", roleCode);
        assignmentData.put("role_scope", "PROJECT");
        assignmentData.put("primary_flag", primary);
        assignmentData.put("responsibility_description", "F03立项批准后自动初始化" + roleName);
        assignmentData.put("effective_from", effectiveFrom);
        assignmentData.put("effective_to", effectiveTo);
        assignmentData.put("handover_required_flag", false);
        assignmentData.put("handover_completed_flag", false);
        assignmentData.put("source_process", "F03");
        EntityDataDTO assignment = saveStandalone(
                PROJECT_ROLE_ASSIGNMENT,
                project.getName() + "-" + roleName,
                project,
                assignmentData);
        mutationExecutor.update(
                PROJECT_ROLE_ASSIGNMENT,
                assignment.getId(),
                update("ACTIVE", Map.of("source_process", "F03")));
    }

    private EntityDataDTO ensureRoleCatalog(
            EntityDataDTO project,
            String roleCode,
            String roleName,
            boolean primaryUnique) {
        List<EntityDataDTO> existing = entityDataService.findByCondition(
                PROJECT_ROLE_CATALOG,
                Map.of("role_code", roleCode));
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        Map<String, Object> catalogData = new LinkedHashMap<>();
        catalogData.put("role_code", roleCode);
        catalogData.put("allowed_scope", List.of("PROJECT"));
        catalogData.put("primary_unique_flag", primaryUnique);
        catalogData.put("approval_required_flag", true);
        catalogData.put("enabled_flag", true);
        catalogData.put("description", "F03立项初始化角色");
        return saveStandalone(
                PROJECT_ROLE_CATALOG,
                roleName,
                project,
                catalogData);
    }

    private void validateMember(String projectId, String memberId, String label) {
        EntityDataDTO member = entityDataService.findById(PROJECT_MEMBER, memberId);
        if (!Objects.equals(projectId, text(read(data(member), "project_id")))
                || !"ACTIVE".equals(member.getStatus())) {
            conflict("PROJECT_MEMBER_INVALID", label + "必须为该项目的有效成员");
        }
    }

    private Map<String, Object> buildProposedLinkData(Map<String, Object> source) {
        Map<String, Object> target = new LinkedHashMap<>();
        copy(source, target, "construction_mode", "construction_mode");
        copy(source, target, "relation_reason", "relation_reason");
        copy(source, target, "affected_modules", "affected_modules");
        copy(source, target, "interface_impact", "interface_impact");
        copy(source, target, "data_impact", "data_impact");
        copy(source, target, "deployment_impact", "deployment_impact");
        copy(source, target, "target_system_version", "target_system_version");
        copy(source, target, "new_project_system_lead_id", "project_system_lead_id");
        copy(source, target, "new_technical_lead_id", "technical_lead_id");
        copy(source, target, "risk_level", "risk_level");
        copy(source, target, "planned_start_date", "planned_start_date");
        copy(source, target, "planned_end_date", "planned_end_date");
        return target;
    }

    private EntityDataDTO saveStandalone(
            String entityCode,
            String name,
            EntityDataDTO source,
            Map<String, Object> customData) {
        EntityDataDTO dto = new EntityDataDTO();
        dto.setEntityCode(entityCode);
        dto.setName(name);
        dto.setSubmitterId(source.getSubmitterId());
        dto.setSubmitterName(source.getSubmitterName());
        dto.setData(customData);
        return mutationExecutor.save(dto);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("项目治理快照序列化失败", exception);
        }
    }

}
