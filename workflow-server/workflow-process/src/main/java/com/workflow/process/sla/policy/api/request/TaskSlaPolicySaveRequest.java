package com.workflow.process.sla.policy.api.request;

import java.util.List;

/**
 * SLA 策略草稿请求。响应时限可选，办结时限必填；两种 timeBasis 分别决定
 * 自然时间或工作时间累计，暂停开关和上限会在任务恢复时使用。
 *
 * @param policyCode 策略编码，供发布引用
 * @param policyName 管理端名称
 * @param description 策略说明
 * @param responseTargetMinutes 响应时限，缺失时不生成响应事件
 * @param completionTargetMinutes 办结时限，用于计算截止点
 * @param responseTimeBasis 响应计时口径
 * @param completionTimeBasis 办结计时口径
 * @param allowManualPause 控制人工暂停入口
 * @param pauseOnProcessSuspend 控制流程挂起时暂停
 * @param maxPauseMinutes 暂停上限，超出后继续计时
 * @param escalationSteps 发布后展开成定时事件
 */
public record TaskSlaPolicySaveRequest(
        String policyCode,
        String policyName,
        String description,
        Integer responseTargetMinutes,
        Integer completionTargetMinutes,
        String responseTimeBasis,
        String completionTimeBasis,
        Boolean allowManualPause,
        Boolean pauseOnProcessSuspend,
        Integer maxPauseMinutes,
        List<EscalationStepRequest> escalationSteps) {

    /**
     * 升级动作配置。metricType/triggerType/offsetMinutes 决定触发时刻，
     * 重复间隔与次数展开事件，收件人和目标 JSON 随事件快照保存供重试使用。
     *
     * @param stepName 步骤名称，供事件排障
     * @param metricType 响应或办结指标，确定截止点
     * @param triggerType 截止前、时或后的触发规则
     * @param offsetMinutes 相对截止点的分钟偏移
     * @param repeatIntervalMinutes 重复动作间隔分钟数
     * @param maxExecutions 重复展开次数
     * @param actionType 通知、转办或加签动作
     * @param templateCode 事件通知模板
     * @param recipientConfigJson 冻结到事件供重试的收件人配置
     * @param targetConfigJson 冻结到事件供重试的动作目标
     */
    public record EscalationStepRequest(
            String stepName,
            String metricType,
            String triggerType,
            Integer offsetMinutes,
            Integer repeatIntervalMinutes,
            Integer maxExecutions,
            String actionType,
            String templateCode,
            String recipientConfigJson,
            String targetConfigJson) {
    }
}
