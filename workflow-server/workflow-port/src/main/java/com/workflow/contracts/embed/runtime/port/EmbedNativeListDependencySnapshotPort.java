package com.workflow.contracts.embed.runtime.port;

import com.workflow.contracts.embed.runtime.model.EmbedNativeListDependencyClosure;

/**
 * 在运行时按 Embed Session 固定的 View Release 读取 LIST 依赖闭包。
 */
public interface EmbedNativeListDependencySnapshotPort {

    /**
     * 从不可变 Runtime Snapshot 读取并校验依赖闭包。
     *
     * @param reference 引用，供本方法读取嵌入式原生列表依赖快照时使用
     * @return 读取后的嵌入式原生列表依赖快照结果，供调用方继续处理
     */
    EmbedNativeListDependencyClosure read(Reference reference);

    /**
     * 指向已固定 Embed Runtime Snapshot 中依赖闭包的不可变引用。
     *
     * @param sessionId 会话ID，后续用于处理引用时定位或关联目标
     * @param viewId 视图ID，后续用于处理引用时定位或关联目标
     * @param viewReleaseId 视图发布版本ID，后续用于处理引用时定位或关联目标
     * @param closureVersion 闭包版本，保存在对象中供后续校验、查询或展示
     * @param closureHash 闭包哈希，保存在对象中供后续校验、查询或展示
     */
    record Reference(
            String sessionId,
            String viewId,
            String viewReleaseId,
            int closureVersion,
            String closureHash) {
    }
}
