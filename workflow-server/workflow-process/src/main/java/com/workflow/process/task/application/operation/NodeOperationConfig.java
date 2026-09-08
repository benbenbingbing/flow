package com.workflow.process.task.application.operation;

/**
 * 用户任务的简化操作权限。
 *
 * <p>配置冻结在 BPMN 的 {@code assigneeConfig} 中；字段缺省时保持历史行为，默认开放。</p>
 */
public record NodeOperationConfig(
        boolean allowTransfer,
        boolean allowAddSign,
        boolean allowTerminate) {

    public static NodeOperationConfig allowAll() {
        return new NodeOperationConfig(true, true, true);
    }

    /** 返回受简化配置管理的操作是否开放。 */
    public boolean allows(NodeOperationPolicy.Operation operation) {
        if (operation == NodeOperationPolicy.Operation.TRANSFER) {
            return allowTransfer;
        }
        if (operation != null && operation.isAddSign()) {
            return allowAddSign;
        }
        if (operation == NodeOperationPolicy.Operation.TERMINATE) {
            return allowTerminate;
        }
        return true;
    }
}
