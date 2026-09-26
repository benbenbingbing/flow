package com.workflow.contracts.entity.port;

/**
 * 流程更新实体记录运行态字段时使用的稳定跨模块写入能力。
 */
public interface EntityRecordPort {

    /**
     * 在终止/撤回产生引擎副作用前确认唯一的业务目标状态。
     * @param entityCode 被取消流程绑定的实体
     * @param category TERMINATED 或 WITHDRAWN，用于选择特殊操作的唯一目标
     * @return 配置中的目标编码；缺失或重复时抛出配置冲突，调用者不得继续取消
     */
    String requireProcessEndStatus(String entityCode, String category);

    /**
     * 更新当前任务；后续读取或执行将使用更新后的状态。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityRecordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param currentTaskId 当前任务ID，写入当前任务信息供后续待办展示和状态同步
     * @param currentTaskName 当前任务名称，写入当前任务信息供后续待办展示和状态同步
     * @param currentTaskAssignee 当前任务办理人，写入当前任务信息供后续待办展示和状态同步
     */
    void updateCurrentTask(
            String entityCode,
            String entityRecordId,
            String currentTaskId,
            String currentTaskName,
            String currentTaskAssignee);

    /**
     * 更新状态；后续读取或执行将使用更新后的状态。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityRecordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param status 目标状态，写入记录后供流程分支或列表查询使用
     */
    void updateStatus(
            String entityCode,
            String entityRecordId,
            String status);

    /**
     * 标记关联流程已结束，并同步实体状态供后续查询。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityRecordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param statusCategory 状态类别，决定后续状态或结果的归类
     * @param fallbackStatus 状态类别无法映射时写入实体的后备状态
     */
    void markProcessEnded(
            String processInstanceId,
            String entityCode,
            String entityRecordId,
            String statusCategory,
            String fallbackStatus);

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
    void recordActivity(
            String entityCode,
            String entityRecordId,
            String action,
            String actionName,
            String processInstanceId,
            String taskId);
}
