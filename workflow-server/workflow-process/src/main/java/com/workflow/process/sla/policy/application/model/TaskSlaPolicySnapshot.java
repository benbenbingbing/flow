package com.workflow.process.sla.policy.application.model;

import java.util.List;

/**
 * 随流程发布固定的 SLA 策略内容，任务创建时复制到任务记录供计时和升级重排使用。
 *
 * @param policyCode 策略编码，追踪配置来源
 * @param policyName 发布时名称，供详情解释
 * @param version 发布版本，标识冻结策略
 * @param responseTargetMinutes 可选响应时限；为空表示不生成响应截止事件
 * @param completionTargetMinutes 必填办结时限，用于 Flowable dueDate 和待办摘要
 * @param responseTimeBasis 响应指标的自然时间或工作时间口径
 * @param completionTimeBasis 办结指标的自然时间或工作时间口径
 * @param allowManualPause 人工暂停入口是否可用
 * @param pauseOnProcessSuspend 流程挂起时是否暂停任务计时
 * @param maxPauseMinutes 暂停上限；超过后从上限时刻继续累计剩余时间
 * @param escalationSteps 有序升级规则，后续由任务 SLA 运行时展开成独立事件
 */
public record TaskSlaPolicySnapshot(
        String policyCode,
        String policyName,
        int version,
        Integer responseTargetMinutes,
        int completionTargetMinutes,
        String responseTimeBasis,
        String completionTimeBasis,
        boolean allowManualPause,
        boolean pauseOnProcessSuspend,
        Integer maxPauseMinutes,
        List<EscalationStep> escalationSteps) {

    /**
     * 一条升级步骤的发布配置。触发类型、偏移和重复次数决定事件时间，
     * 动作及收件人配置会随每条事件保存，重试时不读取已变化的策略草稿。
     *
     * @param id 步骤 ID，进入事件幂等键
     * @param stepName 步骤名称，随动作快照保存
     * @param metricType 指标类型，决定参照哪个截止点
     * @param triggerType 截止前、时或后触发
     * @param offsetMinutes 相对截止点的分钟偏移
     * @param repeatIntervalMinutes 重复动作间隔分钟数
     * @param maxExecutions 重复展开次数
     * @param actionType 事件处理器执行的动作
     * @param templateCode 通知模板，供事件处理使用
     * @param recipientConfigJson 冻结的收件人配置，重试时复用
     * @param targetConfigJson 冻结的目标配置，重试时复用
     * @param sortOrder 发布时的步骤顺序，供回显
     */
    public record EscalationStep(
            String id,
            String stepName,
            String metricType,
            String triggerType,
            int offsetMinutes,
            Integer repeatIntervalMinutes,
            int maxExecutions,
            String actionType,
            String templateCode,
            String recipientConfigJson,
            String targetConfigJson,
            int sortOrder) {
    }
}
