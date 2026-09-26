package com.workflow.admin.audit.api.response;

import java.time.LocalDateTime;

/**
 * 已收敛敏感字段的统一审计只读投影。
 *
 * <p>不返回 before/after、请求地址、IP、User-Agent、错误堆栈等内容；来源详情
 * 必须回到来源模块并再次鉴权读取。</p>
 *
 * @param id 对象标识，供后续引用、更新或关联
 * @param eventId 事件ID，后续用于处理统一审计事件视图时定位或关联目标
 * @param operationId 操作ID，后续用于处理统一审计事件视图时定位或关联目标
 * @param parentOperationId 父级操作ID，后续用于处理统一审计事件视图时定位或关联目标
 * @param traceId 追踪ID，后续用于处理统一审计事件视图时定位或关联目标
 * @param module {@code module}，保存在对象中供后续校验、查询或展示
 * @param operationCode 操作编码，后续用于处理统一审计事件视图时定位或关联目标
 * @param operationName 操作名称，后续用于处理统一审计事件视图时匹配或展示
 * @param result 结果，保存在对象中供后续校验、查询或展示
 * @param riskLevel 风险层级，保存在对象中供后续校验、查询或展示
 * @param operatorId 用户身份 ID，后续用于权限判断、目标分配或操作记录
 * @param operatorName 用户名称，后续用于身份匹配或操作展示
 * @param targetType 目标类型标识，决定后续统一审计事件视图采用的处理分支
 * @param targetId 目标ID，后续用于处理统一审计事件视图时定位或关联目标
 * @param targetName 目标名称，后续用于处理统一审计事件视图时匹配或展示
 * @param summary 摘要，保存在对象中供后续校验、查询或展示
 * @param errorCode 错误编码，后续用于处理统一审计事件视图时定位或关联目标
 * @param durationMs 时长{@code ms}，保存在对象中供后续校验、查询或展示
 * @param source 待处理统一审计事件视图的原始输入，结果供调用方继续使用
 * @param payloadDetailsOmitted 载荷详情{@code omitted}，保存在对象中供后续校验、查询或展示
 * @param createdAt 已创建时间，后续用于判断有效期或展示该事件的发生时间
 */
public record UnifiedAuditEventView(
        String id,
        String eventId,
        String operationId,
        String parentOperationId,
        String traceId,
        String module,
        String operationCode,
        String operationName,
        String result,
        String riskLevel,
        String operatorId,
        String operatorName,
        String targetType,
        String targetId,
        String targetName,
        String summary,
        String errorCode,
        Long durationMs,
        SourcePointer source,
        boolean payloadDetailsOmitted,
        LocalDateTime createdAt) {

    /**
     * 封装来源指针的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param system 系统，保存在对象中供后续校验、查询或展示
     * @param type 类型标识，决定后续来源指针采用的处理分支
     * @param id 对象标识，供后续引用、更新或关联
     * @param eventId 事件ID，后续用于处理来源指针时定位或关联目标
     */
    public record SourcePointer(
            String system,
            String type,
            String id,
            String eventId) {
    }
}
