package com.workflow.contracts.entity.ui.context;

import com.workflow.contracts.entity.ui.model.EntityDescriptor;

/**
 * 从表单绑定调用接口操作时使用的可信上下文。
 *
 * @param common {@code common}，保存在对象中供后续校验、查询或展示
 * @param entity 实体，保存在对象中供后续校验、查询或展示
 * @param formId 表单 ID，后续用于定位已发布表单
 * @param formKey 表单键，后续用于授权校验、关联或幂等去重
 * @param formName 表单名称，后续用于处理表单调用上下文时匹配或展示
 * @param mode 模式标识，决定后续表单调用上下文采用的处理分支
 * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
 * @param fieldCode 字段编码，后续用于处理表单调用上下文时定位或关联目标
 * @param parentRecordId 父级记录ID，后续用于处理表单调用上下文时定位或关联目标
 * @param rowKey 行键，后续用于授权校验、关联或幂等去重
 * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
 * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
 */
public record FormInvocationContext(
        /** 表单、列表和实体调用共享的可信元数据。 */
        CommonInvocationContext common,
        /** 当前表单所属实体的可信描述。 */
        EntityDescriptor entity,
        /** 当前表单 ID。 */
        String formId,
        /** 当前表单稳定编码。 */
        String formKey,
        /** 当前表单名称。 */
        String formName,
        /** 当前表单模式，例如 create、edit、view、approve。 */
        String mode,
        /** 当前业务记录 ID；新增场景可为空。 */
        String recordId,
        /** 当前字段编码；表单级绑定可为空。 */
        String fieldCode,
        /** 子表单或明细行所属的父记录 ID。 */
        String parentRecordId,
        /** 子表单或明细行的稳定行标识。 */
        String rowKey,
        /** 审批按钮经服务端核验的活动任务 ID；其他表单调用为空。 */
        String taskId,
        /** 审批按钮经服务端核验的流程实例 ID；其他表单调用为空。 */
        String processInstanceId) implements UiInvocationContext {

    /**
     * 保留任务主体绑定加入前的公共构造签名，避免既有 Provider 源码扩展升级后
     * 被迫改造；旧调用不会获得任务能力，新增字段安全地保持为空。
     *
     * @param common {@code common}，保存在对象中供后续校验、查询或展示
     * @param entity 实体，保存在对象中供后续校验、查询或展示
     * @param formId 表单 ID，后续用于定位已发布表单
     * @param formKey 表单键，后续用于授权校验、关联或幂等去重
     * @param formName 表单名称，后续用于初始化表单调用上下文时匹配或展示
     * @param mode 模式标识，决定后续表单调用上下文采用的处理分支
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param fieldCode 字段编码，后续用于初始化表单调用上下文时定位或关联目标
     * @param parentRecordId 父级记录ID，后续用于初始化表单调用上下文时定位或关联目标
     * @param rowKey 行键，后续用于授权校验、关联或幂等去重
     */
    public FormInvocationContext(
            CommonInvocationContext common,
            EntityDescriptor entity,
            String formId,
            String formKey,
            String formName,
            String mode,
            String recordId,
            String fieldCode,
            String parentRecordId,
            String rowKey) {
        this(common, entity, formId, formKey, formName, mode,
                recordId, fieldCode, parentRecordId, rowKey,
                null, null);
    }
}
