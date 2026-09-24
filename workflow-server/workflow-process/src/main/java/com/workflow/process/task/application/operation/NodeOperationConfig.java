package com.workflow.process.task.application.operation;

/**
 * 用户任务的简化操作权限。
 *
 * <p>配置冻结在 BPMN 的 {@code assigneeConfig} 中；新节点显式保存默认值，旧部署的缺省兼容由 Reader 处理。</p>
 *
 * @param allowTransfer 允许转办，保存在对象中供后续校验、查询或展示
 * @param allowAddSign 允许添加签名，保存在对象中供后续校验、查询或展示
 * @param allowTerminate 控制终止流程，不影响显式配置的撤回能力
 * @param allowWithdraw 控制发起人撤回，供列表、移动端和写接口共用
 */
public record NodeOperationConfig(
        boolean allowTransfer,
        boolean allowAddSign,
        boolean allowTerminate,
        boolean allowWithdraw) {

    /**
     * 仅用于没有简化开关的历史节点，仍需叠加旧矩阵；新建节点不得使用此默认。
     *
     * @return 处理后的允许全部结果，供调用方继续处理
     */
    public static NodeOperationConfig allowAll() {
        return new NodeOperationConfig(true, true, true, true);
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
        if (operation == NodeOperationPolicy.Operation.WITHDRAW) {
            return allowWithdraw;
        }
        return true;
    }
}
