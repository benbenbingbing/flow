package com.workflow.port.process.model.instance;

import com.workflow.port.process.model.ProcessValues;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** 流程管理操作的业务命令与最小查询投影。 */
public final class ProcessInterventionModels {
    private ProcessInterventionModels() { }

    public enum IncidentAction { RETRY_RESOLVER, ASSIGN_USER, FALLBACK_GROUP, TERMINATE_INSTANCE }

    /** requestId 绑定处置意图；reason 进入运行审计。 */
    public record HandleAssigneeIncident(String incidentId, String requestId, IncidentAction action,
            String userId, String groupCode, String reason) {
        public HandleAssigneeIncident {
            incidentId = ProcessValues.required(incidentId, "incidentId");
            requestId = ProcessValues.required(requestId, "requestId");
            Objects.requireNonNull(action, "action");
            reason = ProcessValues.required(reason, "reason");
            if (requestId.length() > 128 || reason.length() < 5 || reason.length() > 500) {
                throw new IllegalArgumentException("处置请求 ID 最长 128 字符，原因须为 5 至 500 字符");
            }
            if (action == IncidentAction.ASSIGN_USER) userId = ProcessValues.required(userId, "userId");
            if (action == IncidentAction.FALLBACK_GROUP) groupCode = ProcessValues.required(groupCode, "groupCode");
        }
    }

    /** 动作执行状态与人工处置状态可以并存；id 用于重试或登记处理结果。 */
    public record ActionExecution(String id, String actionName, String taskId, String processVersionId,
            String status, String resolutionStatus, Integer attempts, String terminationReason,
            String errorMessage, LocalDateTime createdAt) { }

    /** 待办交接摘要；assignmentType 区分签收、共享候选及本地加签。 */
    public record HandoverTask(String taskId, String taskName, String processInstanceId, String entityCode,
            String recordId, String assignmentType, String status, LocalDateTime createdAt) { }

    /** 交接仅影响当前待办；all 为 true 时 taskIds 必须为空。 */
    public record HandoverCommand(String sourceUserId, String targetUserId, List<String> taskIds, boolean all, String reason) {
        public HandoverCommand {
            sourceUserId = ProcessValues.required(sourceUserId, "sourceUserId");
            targetUserId = ProcessValues.required(targetUserId, "targetUserId");
            taskIds = taskIds == null ? List.of() : List.copyOf(taskIds);
            reason = ProcessValues.required(reason, "reason");
            if (sourceUserId.length() > 64 || targetUserId.length() > 64 || reason.length() > 500
                    || taskIds.stream().anyMatch(id -> id.isBlank() || id.length() > 64) || all != taskIds.isEmpty()) {
                throw new IllegalArgumentException("待办交接人员、任务范围或原因无效");
            }
        }
    }
}
