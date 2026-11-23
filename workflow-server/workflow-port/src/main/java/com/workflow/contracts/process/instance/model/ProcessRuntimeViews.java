package com.workflow.contracts.process.instance.model;

import com.workflow.contracts.process.query.model.ActionCapability;
import com.workflow.contracts.process.query.model.ProcessActivityView;

import java.time.LocalDateTime;
import java.util.List;

/** 流程运行配套查询使用的独立投影。 */
public final class ProcessRuntimeViews {
    private ProcessRuntimeViews() { }

    /** 操作提示以查询时状态为准；接收任务坐标供调用者明确指定等待分支。 */
    public record InstanceCapabilities(ActionCapability withdraw, ActionCapability terminate, ActionCapability receive,
            List<ProcessActivityView> receiveTargets) {
        public InstanceCapabilities { receiveTargets = List.copyOf(receiveTargets); }
    }

    /** 图上各节点及连线的运行状态，与当前查询的实例和发布版本绑定。 */
    public record Progress(String processInstanceId, String processDefinitionId, Integer processVersion,
            String status, String endType, String endReason, List<String> activeNodes, List<String> completedNodes,
            List<String> cancelledNodes, List<String> terminatedNodes, List<String> executedSequenceFlows) {
        public Progress {
            activeNodes = copy(activeNodes);
            completedNodes = copy(completedNodes);
            cancelledNodes = copy(cancelledNodes);
            terminatedNodes = copy(terminatedNodes);
            executedSequenceFlows = copy(executedSequenceFlows);
        }
    }

    /** 知会收件摘要；id 用于已读操作，record 关联通过流程查询取得。 */
    public record CcItem(String id, String processInstanceId, String processName, String nodeName,
            String businessName, String comment, String readStatus, LocalDateTime createdAt, LocalDateTime readAt) { }

    /** 已发布 SLA 的计时状态；为空表示任务没有启用 SLA。 */
    public record TaskSla(String taskId, String policyCode, Integer policyVersion, String timezoneId,
            String overallStatus, String responseStatus, String completionStatus, LocalDateTime responseDueAt,
            LocalDateTime completionDueAt, LocalDateTime respondedAt, LocalDateTime pauseStartedAt) { }

    private static List<String> copy(List<String> items) { return items == null ? List.of() : List.copyOf(items); }
}
