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
 *
 * @param planId 方案ID，后续用于处理关联流程协同方案时定位或关联目标
 * @param graphFingerprint 图指纹，保存在对象中供后续校验、查询或展示
 * @param operation 操作标识，决定后续关联流程协同方案采用的处理分支
 * @param source 待处理关联流程协同方案的原始输入，结果供调用方继续使用
 * @param publishedRelationPath 已发布关系路径，保存在对象中供后续校验、查询或展示
 * @param targets 目标集合，保存在对象中供后续校验、查询或展示
 * @param allowedStates 允许{@code states}，保存在对象中供后续校验、查询或展示
 * @param targetActivityId 目标活动ID，后续用于处理关联流程协同方案时定位或关联目标
 * @param reason 原因，保存在对象中供后续校验、查询或展示
 * @param activeCount 活动数量，保存在对象中供后续校验、查询或展示
 * @param terminalCount 终态数量，保存在对象中供后续校验、查询或展示
 * @param noProcessCount 无流程数量，保存在对象中供后续校验、查询或展示
 * @param writeOperation 写入操作，保存在对象中供后续校验、查询或展示
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

    /**
     * 初始化关联流程协同方案，保存构造参数供后续方法使用。
     *
     * @param planId 方案ID，后续用于初始化关联流程协同方案时定位或关联目标
     * @param graphFingerprint 图指纹，保存在对象中供后续校验、查询或展示
     * @param operation 操作标识，决定后续关联流程协同方案采用的处理分支
     * @param source 待初始化关联流程协同方案的原始输入，结果供调用方继续使用
     * @param publishedRelationPath 已发布关系路径，保存在对象中供后续校验、查询或展示
     * @param targets 目标集合，保存在对象中供后续校验、查询或展示
     * @param allowedStates 允许{@code states}，保存在对象中供后续校验、查询或展示
     * @param targetActivityId 目标活动ID，后续用于初始化关联流程协同方案时定位或关联目标
     * @param reason 原因，保存在对象中供后续校验、查询或展示
     * @param activeCount 活动数量，保存在对象中供后续校验、查询或展示
     * @param terminalCount 终态数量，保存在对象中供后续校验、查询或展示
     * @param noProcessCount 无流程数量，保存在对象中供后续校验、查询或展示
     * @param writeOperation 写入操作，保存在对象中供后续校验、查询或展示
     */
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
     *
     * @param operation 操作标识，决定后续命令采用的处理分支
     * @param publishedRelationPath 已发布关系路径，保存在对象中供后续校验、查询或展示
     * @param allowedStates 允许{@code states}，保存在对象中供后续校验、查询或展示
     * @param minimumRelatedRecords {@code minimum}关联记录集合，保存在对象中供后续校验、查询或展示
     * @param targetActivityId 目标活动ID，后续用于处理命令时定位或关联目标
     * @param reason 原因，保存在对象中供后续校验、查询或展示
     */
    public record Command(
            Operation operation,
            PublishedRelationPath publishedRelationPath,
            Set<ProcessState> allowedStates,
            Integer minimumRelatedRecords,
            String targetActivityId,
            String reason) {

        /**
         * 初始化命令，保存构造参数供后续方法使用。
         *
         * @param operation 操作标识，决定后续命令采用的处理分支
         * @param publishedRelationPath 已发布关系路径，保存在对象中供后续校验、查询或展示
         * @param allowedStates 允许{@code states}，保存在对象中供后续校验、查询或展示
         * @param minimumRelatedRecords {@code minimum}关联记录集合，保存在对象中供后续校验、查询或展示
         * @param targetActivityId 目标活动ID，后续用于初始化命令时定位或关联目标
         * @param reason 原因，保存在对象中供后续校验、查询或展示
         */
        public Command {
            allowedStates = allowedStates == null
                    ? Set.of() : Set.copyOf(allowedStates);
        }
    }

    /**
     * 本次协同的宿主与操作人快照。
     *
     * @param actionId 动作ID，后续用于处理来源时定位或关联目标
     * @param actionExecutionId 动作执行ID，后续用于处理来源时定位或关联目标
     * @param processVersionId 流程版本ID，后续用于处理来源时定位或关联目标
     * @param processDefinitionId 流程定义 ID，用于读取对应的已发布流程配置
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityRecordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param operatorId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param operatorName 用户名称，后续用于身份匹配或操作展示
     */
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

    /**
     * 一条关联记录及其精确流程影响。
     *
     * @param record 记录，保存在对象中供后续校验、查询或展示
     * @param state 状态标识，决定后续目标影响采用的处理分支
     * @param entityProcessLinkId 实体流程链接ID，后续用于处理目标影响时定位或关联目标
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param processDefinitionId 流程定义 ID，用于读取对应的已发布流程配置
     * @param processVersionHistoryId 流程版本历史ID，后续用于处理目标影响时定位或关联目标
     * @param processVersion 流程版本，保存在对象中供后续校验、查询或展示
     * @param processKey 流程键，后续用于授权校验、关联或幂等去重
     * @param entityStatus 实体状态标识，决定后续目标影响采用的处理分支
     * @param activeActivityIds 活动活动ID 集合，保存在对象中供后续校验、查询或展示
     */
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

        /**
         * 初始化目标影响，保存构造参数供后续方法使用。
         *
         * @param record 记录，保存在对象中供后续校验、查询或展示
         * @param state 状态标识，决定后续目标影响采用的处理分支
         * @param entityProcessLinkId 实体流程链接ID，后续用于初始化目标影响时定位或关联目标
         * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
         * @param processDefinitionId 流程定义 ID，用于读取对应的已发布流程配置
         * @param processVersionHistoryId 流程版本历史ID，后续用于初始化目标影响时定位或关联目标
         * @param processVersion 流程版本，保存在对象中供后续校验、查询或展示
         * @param processKey 流程键，后续用于授权校验、关联或幂等去重
         * @param entityStatus 实体状态标识，决定后续目标影响采用的处理分支
         * @param activeActivityIds 活动活动ID 集合，保存在对象中供后续校验、查询或展示
         */
        public TargetImpact {
            activeActivityIds = activeActivityIds == null
                    ? List.of() : List.copyOf(activeActivityIds);
        }
    }
}
