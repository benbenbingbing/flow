package com.workflow.entity.data.infrastructure.adapter;

import com.workflow.contracts.entity.EntityRecordPort;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationContext;
import com.workflow.contracts.entity.mutation.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.EntityMutationPort;
import com.workflow.contracts.entity.mutation.EntityMutationSourceType;
import com.workflow.contracts.entity.mutation.EntityMutationSystemFields;
import com.workflow.contracts.entity.mutation.EntityMutationTargetNotFoundException;
import com.workflow.entity.data.application.EntityRecordTeamService;
import com.workflow.entity.version.application.EntityMutationIsolationExecutor;
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

    @Override
    public void markProcessEnded(
            String processInstanceId,
            String entityCode,
            String entityRecordId,
            String statusCategory,
            String fallbackStatus) {
        boolean completed =
                "COMPLETED".equals(statusCategory);
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
        } catch (EntityMutationTargetNotFoundException exception) {
            log.info(
                    "流程结束状态同步跳过已删除实体: processInstanceId={}, entityCode={}, entityRecordId={}",
                    processInstanceId,
                    entityCode,
                    entityRecordId);
        }
    }

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

    private String blankToNone(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }

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
