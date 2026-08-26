package com.workflow.process.coordination.application;

import com.workflow.entity.data.application.model.EntityRelationGraph.RecordRef;
import com.workflow.entity.definition.application.model.PublishedRelationPath;

import java.util.List;
import java.util.Set;

/**
 * 跨实体流程协同的不可变影响预览。
 *
 * <p>计划中的目标记录由服务端关系图解析得到，目标流程实例、
 * Flowable 定义 ID 与平台发布历史也在预览时固定。写入执行仍会重新
 * 校验关系、权限和这些版本字段，计划本身不是绕过鉴权的令牌。</p>
 */
public record RelatedProcessCoordinationPlan(
        String planId,
        String graphFingerprint,
        Operation operation,
        Source source,
        PublishedRelationPath publishedRelationPath,
        List<TargetImpact> targets,
        Set<ProcessState> allowedStates,
        String targetActivityId,
        String reason,
        int activeCount,
        int terminalCount,
        int noProcessCount,
        boolean writeOperation) {

    public RelatedProcessCoordinationPlan {
        targets = targets == null ? List.of() : List.copyOf(targets);
        allowedStates = allowedStates == null
                ? Set.of() : Set.copyOf(allowedStates);
    }

    /** 对应已确认的四种通用协同能力。 */
    public enum Operation {
        ASSERT_RELATED_STATE,
        WAIT_RELATED_PROCESSES,
        ROUTE_RELATED_PARENT,
        PROPAGATE_TERMINATION
    }

    /** 相关记录当前的可验证流程状态。 */
    public enum ProcessState {
        NONE,
        STARTING,
        ACTIVE,
        COMPLETED,
        TERMINATED,
        INCONSISTENT
    }

    /**
     * 开发人员注册的流程动作所使用的强类型命令。
     *
     * <p>{@code publishedRelationPath} 必须来自已发布配置快照；命令不包含
     * sourceRecordId、targetRecordId、SQL 或筛选表达式。</p>
     */
    public record Command(
            Operation operation,
            PublishedRelationPath publishedRelationPath,
            Set<ProcessState> allowedStates,
            Integer minimumRelatedRecords,
            String targetActivityId,
            String reason) {

        public Command {
            allowedStates = allowedStates == null
                    ? Set.of() : Set.copyOf(allowedStates);
        }
    }

    /** 本次协同的宿主与操作人快照。 */
    public record Source(
            String actionId,
            String actionExecutionId,
            String processVersionId,
            String processDefinitionId,
            String processInstanceId,
            String entityCode,
            String entityRecordId,
            String operatorId,
            String operatorName) {
    }

    /** 一条关联记录及其精确流程影响。 */
    public record TargetImpact(
            RecordRef record,
            ProcessState state,
            String entityProcessLinkId,
            String processInstanceId,
            String processDefinitionId,
            String processVersionHistoryId,
            Integer processVersion,
            String processKey,
            String entityStatus,
            List<String> activeActivityIds) {

        public TargetImpact {
            activeActivityIds = activeActivityIds == null
                    ? List.of() : List.copyOf(activeActivityIds);
        }
    }
}
