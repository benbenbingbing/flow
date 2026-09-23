package com.workflow.contracts.audit.model;

/**
 * 指向产生统一审计事件的权威业务记录。
 *
 * <p>该指针只保存稳定标识，不复制来源表的业务载荷。查询方如需查看来源详情，
 * 仍必须通过来源模块自己的鉴权接口读取。</p>
 *
 * @param sourceSystem 来源系统，保存在对象中供后续校验、查询或展示
 * @param sourceType 来源类型标识，决定后续审计来源指针采用的处理分支
 * @param sourceId 来源ID，后续用于处理审计来源指针时定位或关联目标
 * @param sourceEventId 来源事件ID，后续用于处理审计来源指针时定位或关联目标
 */
public record AuditSourcePointer(
        String sourceSystem,
        String sourceType,
        String sourceId,
        String sourceEventId) {

    /**
     * 初始化审计来源指针，保存构造参数供后续方法使用。
     *
     * @param sourceSystem 来源系统，保存在对象中供后续校验、查询或展示
     * @param sourceType 来源类型标识，决定后续审计来源指针采用的处理分支
     * @param sourceId 来源ID，后续用于初始化审计来源指针时定位或关联目标
     * @param sourceEventId 来源事件ID，后续用于初始化审计来源指针时定位或关联目标
     */
    public AuditSourcePointer {
        sourceSystem = text(sourceSystem);
        sourceType = text(sourceType);
        sourceId = text(sourceId);
        sourceEventId = text(sourceEventId);
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
