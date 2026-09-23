package com.workflow.process.status.application;

/**
 * 封装流程状态同步载荷的不可变数据；各分量供后续校验、传递或结果展示使用。
 *
 * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
 * @param eventType 事件类型标识，决定后续流程状态同步载荷采用的处理分支
 * @param eventSequence 事件序列，保存在对象中供后续校验、查询或展示
 * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
 * @param entityRecordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
 * @param targetStatus 目标状态标识，决定后续流程状态同步载荷采用的处理分支
 * @param statusCategory 状态类别，决定后续状态或结果的归类
 * @param fallbackStatus 状态类别无法映射时写入实体的后备状态
 */
public record ProcessStatusSyncPayload(
        String processInstanceId,
        String eventType,
        String eventSequence,
        String entityCode,
        String entityRecordId,
        String targetStatus,
        String statusCategory,
        String fallbackStatus) {

    /**
     * 初始化流程状态同步载荷，保存构造参数供后续方法使用。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param eventType 事件类型标识，决定后续流程状态同步载荷采用的处理分支
     * @param eventSequence 事件序列，保存在对象中供后续校验、查询或展示
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityRecordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param targetStatus 目标状态标识，决定后续流程状态同步载荷采用的处理分支
     * @param statusCategory 状态类别，决定后续状态或结果的归类
     * @param fallbackStatus 状态类别无法映射时写入实体的后备状态
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    public ProcessStatusSyncPayload {
        require(processInstanceId, "processInstanceId");
        require(eventType, "eventType");
        require(eventSequence, "eventSequence");
        require(entityCode, "entityCode");
        require(entityRecordId, "entityRecordId");
        if ("TASK_COMPLETED".equals(eventType)) {
            require(targetStatus, "targetStatus");
        } else if ("PROCESS_END".equals(eventType)) {
            require(statusCategory, "statusCategory");
            // 新发布版本不携带业务状态兜底，结束仅更新生命周期。
        } else {
            throw new IllegalArgumentException(
                    "未知状态同步事件: " + eventType);
        }
    }

    /**
     * 校验并获取流程状态同步载荷；不满足约束时阻止后续处理。
     *
     * @param value 待校验并获取流程状态同步载荷的原始输入，结果供调用方继续使用
     * @param field 字段，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "状态同步事件缺少 " + field);
        }
    }
}
