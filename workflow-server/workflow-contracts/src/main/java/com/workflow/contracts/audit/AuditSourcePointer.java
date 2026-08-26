package com.workflow.contracts.audit;

/**
 * 指向产生统一审计事件的权威业务记录。
 *
 * <p>该指针只保存稳定标识，不复制来源表的业务载荷。查询方如需查看来源详情，
 * 仍必须通过来源模块自己的鉴权接口读取。</p>
 */
public record AuditSourcePointer(
        String sourceSystem,
        String sourceType,
        String sourceId,
        String sourceEventId) {

    public AuditSourcePointer {
        sourceSystem = text(sourceSystem);
        sourceType = text(sourceType);
        sourceId = text(sourceId);
        sourceEventId = text(sourceEventId);
    }

    private static String text(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }
}
