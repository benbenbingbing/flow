package com.workflow.contracts.audit.context;

import com.workflow.contracts.audit.model.AuditSourcePointer;
import java.util.Objects;

/**
 * 跨模块传播的一次业务操作上下文。
 *
 * <p>{@code operationId} 串联同一次业务操作产生的多个审计事件，
 * {@code traceId} 只负责请求/消息链路追踪，两者不得互相冒充。
 * 异步边界必须显式复制本对象，不能依赖线程本地状态自动传播。</p>
 *
 * @param operationId 操作ID，后续用于处理操作上下文时定位或关联目标
 * @param traceId 追踪ID，后续用于处理操作上下文时定位或关联目标
 * @param parentOperationId 父级操作ID，后续用于处理操作上下文时定位或关联目标
 * @param sourcePointer 来源指针，保存在对象中供后续校验、查询或展示
 */
public record OperationContext(
        String operationId,
        String traceId,
        String parentOperationId,
        AuditSourcePointer sourcePointer) {

    /**
     * 初始化操作上下文，保存构造参数供后续方法使用。
     *
     * @param operationId 操作ID，后续用于初始化操作上下文时定位或关联目标
     * @param traceId 追踪ID，后续用于初始化操作上下文时定位或关联目标
     * @param parentOperationId 父级操作ID，后续用于初始化操作上下文时定位或关联目标
     * @param sourcePointer 来源指针，保存在对象中供后续校验、查询或展示
     */
    public OperationContext {
        operationId = required(operationId, "operationId");
        traceId = text(traceId);
        parentOperationId = text(parentOperationId);
    }

    /**
     * 处理根，并将结果传给后续步骤。
     *
     * @param operationId 操作ID，后续用于处理根时定位或关联目标
     * @param traceId 追踪ID，后续用于处理根时定位或关联目标
     * @return 处理后的根结果，供调用方继续处理
     */
    public static OperationContext root(
            String operationId,
            String traceId) {
        return new OperationContext(
                operationId,
                traceId,
                null,
                null);
    }

    /**
     * 为受当前操作触发的子操作建立上下文，同时保留同一链路 Trace。
     *
     * @param childOperationId 子级操作ID，后续用于处理子级时定位或关联目标
     * @param childSource 子级来源，作为 {@code OperationContext} 的输入影响后续处理
     * @return 处理后的子级结果，供调用方继续处理
     */
    public OperationContext child(
            String childOperationId,
            AuditSourcePointer childSource) {
        return new OperationContext(
                childOperationId,
                traceId,
                operationId,
                childSource);
    }

    /**
     * 生成必填文本，供后续匹配或展示。
     *
     * @param value 待处理必填的原始输入，结果供调用方继续使用
     * @param name 名称，后续用于处理必填时匹配或展示
     * @return 处理后的必填文本，供调用方比较或展示
     */
    private static String required(
            String value,
            String name) {
        String normalized = text(value);
        return Objects.requireNonNull(
                normalized,
                name + " 不能为空");
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private static String text(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }
}
