package com.workflow.entity.data.infrastructure.adapter;

import com.workflow.contracts.entity.EntityRecordPort;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationContext;
import com.workflow.contracts.entity.mutation.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.EntityMutationPort;
import com.workflow.contracts.entity.mutation.EntityMutationSourceType;
import com.workflow.contracts.entity.mutation.EntityMutationSystemFields;
import com.workflow.entity.data.application.EntityRecordTeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 流程运行态实体端口适配器。
 */
@Component
@RequiredArgsConstructor
public class EntityRecordMutationAdapter
        implements EntityRecordPort {

    private final EntityMutationPort mutationPort;
    private final EntityRecordTeamService teamService;

    @Override
    public void updateCurrentTask(
            String entityCode,
            String entityRecordId,
            String currentTaskId,
            String currentTaskName,
            String currentTaskAssignee) {
        String key = String.join(
                ":",
                "task-runtime",
                entityCode,
                entityRecordId,
                currentTaskId == null
                        ? "none" : currentTaskId);
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
                        .trace(key, key)
                        .build()));
    }

    @Override
    public void markProcessEnded(
            String entityCode,
            String entityRecordId,
            String statusCategory,
            String fallbackStatus) {
        boolean completed =
                "COMPLETED".equals(statusCategory);
        String key = String.join(
                ":",
                "process-end",
                entityCode,
                entityRecordId,
                statusCategory == null
                        ? "UNKNOWN" : statusCategory);
        mutationPort.execute(new EntityMutationCommand(
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
                        .trace(key, key)
                        .build()));
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
