package com.workflow.process.task.application.operation;

/**
 * 用户任务的简化操作权限。
 *
 * <p>配置冻结在 BPMN 的 {@code assigneeConfig} 中；字段缺省时保持历史行为，默认开放。</p>
 *
 * @param allowTransfer 允许转办，保存在对象中供后续校验、查询或展示
 * @param allowAddSign 允许添加签名，保存在对象中供后续校验、查询或展示
 * @param allowTerminate 允许终止，保存在对象中供后续校验、查询或展示
 */
public record NodeOperationConfig(
        boolean allowTransfer,
        boolean allowAddSign,
        boolean allowTerminate) {

    /**
     * 处理允许全部，并将结果传给后续步骤。
     *
     * @return 处理后的允许全部结果，供调用方继续处理
     */
    public static NodeOperationConfig allowAll() {
        return new NodeOperationConfig(true, true, true);
    }

    /**
     * 返回受简化配置管理的操作是否开放。
     *
     * @param operation 操作标识，决定后续{@code allows}采用的处理分支
     * @return {@code allows}条件成立时为 true，否则为 false
     */
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
