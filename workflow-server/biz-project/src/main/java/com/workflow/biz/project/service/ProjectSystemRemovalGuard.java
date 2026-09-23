package com.workflow.biz.project.service;

import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.workflow.biz.project.service.ProjectGovernanceValues.data;
import static com.workflow.biz.project.service.ProjectGovernanceValues.read;
import static com.workflow.biz.project.service.ProjectGovernanceValues.text;

/**
 * 汇总项目系统关系移除前的跨实体业务门禁。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectSystemRemovalGuard {

    private static final String PROJECT_ROLE_ASSIGNMENT =
            "project_role_assignment";
    private static final String REQUIREMENT_PROJECT_LINK =
            "requirement_project_link";
    private static final String REQUIREMENT_SYSTEM_IMPACT =
            "requirement_system_impact";
    private static final String PROJECT_SYSTEM_CHANGE =
            "project_system_change_request";
    private static final Set<String> TERMINAL_CHANGE_STATUSES =
            Set.of("EFFECTIVE", "REJECTED", "CANCELLED", "FAILED");

    private final EntityDataDynamicService entityDataService;

    /**
     * 收集阻断项；结果供调用方的后续步骤使用。
     *
     * @param requestId 请求ID，后续用于收集阻断项时定位或关联目标
     * @param projectId 项目ID，后续用于收集阻断项时定位或关联目标
     * @param systemId 系统ID，后续用于收集阻断项时定位或关联目标
     * @param linkId 链接ID，后续用于收集阻断项时定位或关联目标
     * @return 项目系统{@code removal}保护集合，供调用方遍历或展示
     */
    public List<String> collectBlockers(
            String requestId,
            String projectId,
            String systemId,
            String linkId) {
        List<String> blockers = new ArrayList<>();
        long activeRoles = entityDataService.findByCondition(
                        PROJECT_ROLE_ASSIGNMENT,
                        Map.of(
                                "project_id", projectId,
                                "system_id", systemId))
                .stream()
                .filter(item ->
                        "ACTIVE".equals(item.getStatus()))
                .count();
        if (activeRoles > 0) {
            blockers.add(
                    "存在" + activeRoles
                            + "条有效系统级项目角色");
        }
        addRequirementBlocker(
                blockers,
                projectId,
                systemId);
        addOptionalBlocker(
                blockers,
                "solution_review",
                Map.of(
                        "project_id", projectId,
                        "system_id", systemId),
                Set.of("APPROVED", "REJECTED", "CLOSED"),
                "条未关闭技术方案评审");
        addOptionalBlocker(
                blockers,
                "release_system_link",
                Map.of("project_system_link_id", linkId),
                Set.of(
                        "SUCCESS", "ROLLED_BACK",
                        "FAILED", "CANCELLED"),
                "条未结束发布范围");
        long concurrentChanges =
                entityDataService.findByCondition(
                                PROJECT_SYSTEM_CHANGE,
                                Map.of(
                                        "project_system_link_id",
                                        linkId))
                        .stream()
                        .filter(item -> !Objects.equals(
                                requestId,
                                item.getId()))
                        .filter(item ->
                                !TERMINAL_CHANGE_STATUSES
                                        .contains(item.getStatus()))
                        .count();
        if (concurrentChanges > 0) {
            blockers.add(
                    "存在" + concurrentChanges
                            + "条并行未结束关系变更");
        }
        return blockers;
    }

    /**
     * 添加{@code requirement}{@code blocker}；结果供后续流程传递或持久化。
     *
     * @param blockers 阻断项，供本方法添加{@code requirement}{@code blocker}时使用
     * @param projectId 项目ID，后续用于添加{@code requirement}{@code blocker}时定位或关联目标
     * @param systemId 系统ID，后续用于添加{@code requirement}{@code blocker}时定位或关联目标
     */
    private void addRequirementBlocker(
            List<String> blockers,
            String projectId,
            String systemId) {
        List<EntityDataDTO> projectRequirements =
                entityDataService.findByCondition(
                                REQUIREMENT_PROJECT_LINK,
                                Map.of("project_id", projectId))
                        .stream()
                        .filter(item -> Set.of(
                                        "PROPOSED", "APPROVED",
                                        "DELIVERING", "DELIVERED")
                                .contains(item.getStatus()))
                        .toList();
        long affectedRequirements = 0;
        for (EntityDataDTO projectRequirement
                : projectRequirements) {
            String requirementId = text(read(
                    data(projectRequirement),
                    "requirement_id"));
            if (!StringUtils.hasText(requirementId)) {
                continue;
            }
            affectedRequirements +=
                    entityDataService.findByCondition(
                                    REQUIREMENT_SYSTEM_IMPACT,
                                    Map.of(
                                            "requirement_id",
                                            requirementId,
                                            "system_id",
                                            systemId))
                            .stream()
                            .filter(item ->
                                    !"INVALID".equals(
                                            item.getStatus()))
                            .count();
        }
        if (affectedRequirements > 0) {
            blockers.add(
                    "存在" + affectedRequirements
                            + "条未完成需求仍影响该系统");
        }
    }

    /**
     * 添加可选{@code blocker}；结果供后续流程传递或持久化。
     *
     * @param blockers 阻断项，供本方法添加可选{@code blocker}时使用
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param condition 筛选条件，后续与权限约束合并为查询条件
     * @param terminalStatuses 终态{@code statuses}，供本方法添加可选{@code blocker}时使用
     * @param messageSuffix 消息后缀，作为 {@code blockers.add} 的输入影响后续处理
     */
    private void addOptionalBlocker(
            List<String> blockers,
            String entityCode,
            Map<String, Object> condition,
            Set<String> terminalStatuses,
            String messageSuffix) {
        long count = optionalFind(
                        entityCode,
                        condition)
                .stream()
                .filter(item ->
                        !terminalStatuses.contains(
                                item.getStatus()))
                .count();
        if (count > 0) {
            blockers.add(
                    "存在" + count + messageSuffix);
        }
    }

    /**
     * 整理可选{@code find}数据，供调用方遍历或继续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param condition 筛选条件，后续与权限约束合并为查询条件
     * @return 实体数据集合，供调用方遍历或展示
     */
    private List<EntityDataDTO> optionalFind(
            String entityCode,
            Map<String, Object> condition) {
        try {
            return entityDataService.findByCondition(
                    entityCode,
                    condition);
        } catch (RuntimeException exception) {
            String message = exception.getMessage();
            if (message != null
                    && message.contains("实体不存在")) {
                log.debug(
                        "可选门禁实体尚未发布，按无记录处理: entityCode={}",
                        entityCode);
                return List.of();
            }
            throw exception;
        }
    }
}
