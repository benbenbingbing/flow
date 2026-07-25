package com.workflow.contracts.ui.runtime;

/**
 * 服务端可信的 UI 发布快照解析上下文。
 *
 * @param purpose                 解析目的
 * @param processVersionHistoryId 流程发布历史ID
 * @param nodeId                  流程节点ID
 */
public record UiRuntimeResolutionContext(
        UiRuntimePurpose purpose,
        String processVersionHistoryId,
        String nodeId) {

    public static UiRuntimeResolutionContext standalone() {
        return new UiRuntimeResolutionContext(
                UiRuntimePurpose.STANDALONE,
                null,
                null);
    }

    public static UiRuntimeResolutionContext historical(
            String processVersionHistoryId,
            String nodeId) {
        return new UiRuntimeResolutionContext(
                UiRuntimePurpose.HISTORICAL,
                processVersionHistoryId,
                nodeId);
    }
}
