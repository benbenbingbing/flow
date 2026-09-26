package com.workflow.entity.data.infrastructure.adapter;

import com.workflow.contracts.entity.port.EntityRecordPort;
import com.workflow.contracts.entity.mutation.model.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.model.EntityMutationContext;
import com.workflow.contracts.entity.mutation.model.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.port.EntityMutationPort;
import com.workflow.contracts.entity.mutation.model.EntityMutationSourceType;
import com.workflow.entity.data.application.EntityMutationSystemFields;
import com.workflow.contracts.entity.mutation.error.EntityMutationTargetNotFoundException;
import com.workflow.entity.data.application.EntityRecordTeamService;
import com.workflow.entity.mutation.application.EntityMutationIsolationExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 流程运行态实体端口适配器。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EntityRecordMutationAdapter
        implements EntityRecordPort {

    private final EntityMutationPort mutationPort;
    private final EntityMutationIsolationExecutor isolationExecutor;
    private final EntityRecordTeamService teamService;
    private final com.workflow.entity.definition.application.EntityStatusService statusService;

    /** 先验证配置再取消引擎，避免流程结束后才发现目标状态缺失。 */
    @Override
    public String requireProcessEndStatus(String entityCode, String category) {
        return statusService.requireSpecialTarget(entityCode, category);
    }

    /**
     * 更新当前任务；后续读取或执行将使用更新后的状态。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityRecordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param currentTaskId 当前任务ID，写入当前任务信息供后续待办展示和状态同步
     * @param currentTaskName 当前任务名称，写入当前任务信息供后续待办展示和状态同步
     * @param currentTaskAssignee 当前任务办理人，写入当前任务信息供后续待办展示和状态同步
     */
    @Override
    public void updateCurrentTask(
            String entityCode,
            String entityRecordId,
            String currentTaskId,
            String currentTaskName,
            String currentTaskAssignee) {
        // 会签时每人完成自己的任务后，剩余活跃任务的第一个 ID 经常不变。
        // 幂等键若只含该任务 ID，后办的人会带着不同操作者撞上首次同步回执。
        // 键按「当前任务快照」区分，操作者固定为系统，相同快照视为重放。
        String key = String.join(
                ":",
                "task-runtime",
                entityCode,
                entityRecordId,
                blankToNone(currentTaskId),
                blankToNone(currentTaskAssignee));
        Map<String, Object> payload =
                new LinkedHashMap<>();
        payload.put(
                EntityMutationSystemFields.MODE_KEY,
                EntityMutationSystemFields.CURRENT_TASK);
        payload.put("currentTaskId", currentTaskId);
        payload.put("currentTaskName", currentTaskName);
        payload.put(
                "currentTaskAssignee",
                currentTaskAssignee);
        mutationPort.execute(new EntityMutationCommand(
                key,
                entityCode,
                entityRecordId,
                EntityMutationOperationType.STATUS_CHANGE,
                payload,
                EntityMutationContext.builder(
                                EntityMutationSourceType.PROCESS_RUNTIME,
                                "TASK_RUNTIME_SYNC",
                                "当前任务同步")
                        .sourceId(currentTaskId)
                        .sourceRecord(
                                entityCode,
                                entityRecordId)
                        .operator("system", "流程引擎")
                        .trace(key, key)
                        .build()));
    }

    /**
     * 标记关联流程已结束，并同步实体状态供后续查询。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityRecordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param statusCategory 状态类别，决定后续状态或结果的归类
     * @param fallbackStatus 状态类别无法映射时写入实体的后备状态
     */
    @Override
    public void markProcessEnded(
            String processInstanceId,
            String entityCode,
            String entityRecordId,
            String statusCategory,
            String fallbackStatus) {
        boolean completed =
                "COMPLETED".equals(statusCategory)
                        && fallbackStatus != null && !fallbackStatus.isBlank();
        String key = String.join(
                ":",
                "process-end",
                processInstanceId,
                statusCategory == null
                        ? "UNKNOWN" : statusCategory);
        try {
            isolationExecutor.execute(new EntityMutationCommand(
                    key,
                    entityCode,
                    entityRecordId,
                    EntityMutationOperationType.STATUS_CHANGE,
                    Map.of(
                            EntityMutationSystemFields.MODE_KEY,
                            EntityMutationSystemFields.PROCESS_END,
                            "statusCategory",
                            statusCategory == null
                                    ? "" : statusCategory,
                            "fallbackStatus",
                            fallbackStatus == null
                                    ? "" : fallbackStatus),
                    EntityMutationContext.builder(
                                    EntityMutationSourceType.PROCESS_RUNTIME,
                                    completed
                                            ? "INITIAL_EFFECTIVE"
                                            : "PROCESS_END_SYNC",
                                    completed
                                            ? "初始审批生效"
                                            : "流程结束同步")
                            .sourceId(statusCategory)
                            .sourceRecord(
                                    entityCode,
                                    entityRecordId)
                            .process(
                                    null,
                                    processInstanceId,
                                    null)
                            .operator(
                                    "system",
                                    "流程引擎")
                            .trace(key, key)
                            .build()));
        } catch (com.workflow.entity.data.domain.policy.StaleProcessEventException exception) {
            log.info("忽略已被新实例替代的结束事件: {}", processInstanceId);
        } catch (EntityMutationTargetNotFoundException exception) {
            log.info(
                    "流程结束状态同步跳过已删除实体: processInstanceId={}, entityCode={}, entityRecordId={}",
                    processInstanceId,
                    entityCode,
                    entityRecordId);
        }
    }

    /**
     * 更新状态；后续读取或执行将使用更新后的状态。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityRecordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param status 目标状态，写入记录后供流程分支或列表查询使用
     */
    @Override
    public void updateStatus(
            String entityCode,
            String entityRecordId,
            String status) {
        String key = String.join(
                ":",
                "status-sync",
                entityCode,
                entityRecordId,
                status == null ? "none" : status);
        mutationPort.execute(new EntityMutationCommand(
                key,
                entityCode,
                entityRecordId,
                EntityMutationOperationType.STATUS_CHANGE,
                Map.of("status", status == null ? "" : status),
                EntityMutationContext.builder(
                                EntityMutationSourceType.PROCESS_RUNTIME,
                                "ENTITY_STATUS_SYNC",
                                "实体状态同步")
                        .sourceId(status)
                        .sourceRecord(entityCode, entityRecordId)
                        .trace(key, key)
                        .build()));
    }

    /**
     * 生成空白截止{@code none}文本，供后续匹配或展示。
     *
     * @param value 待处理空白截止{@code none}的原始输入，结果供调用方继续使用
     * @return 处理后的空白截止{@code none}文本，供调用方比较或展示
     */
    private String blankToNone(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }

    /**
     * 记录流程或实体活动，供后续历史展示与审计追溯。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityRecordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param action 动作，写入活动历史供后续审计或展示
     * @param actionName 动作名称，写入活动历史供后续审计或展示
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     */
    @Override
    public void recordActivity(
            String entityCode,
            String entityRecordId,
            String action,
            String actionName,
            String processInstanceId,
            String taskId) {
        teamService.record(
                entityCode,
                entityRecordId,
                action,
                actionName,
                processInstanceId,
                taskId);
    }
}
