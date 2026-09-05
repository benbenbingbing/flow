package com.workflow.contracts.embed.runtime.port;

import com.workflow.contracts.embed.EmbedNativeListDependencyClosure;

/**
 * 在运行时按 Embed Session 固定的 View Release 读取 LIST 依赖闭包。
 */
public interface EmbedNativeListDependencySnapshotPort {

    /** 从不可变 Runtime Snapshot 读取并校验依赖闭包。 */
    EmbedNativeListDependencyClosure read(Reference reference);

    /** 指向已固定 Embed Runtime Snapshot 中依赖闭包的不可变引用。 */
    record Reference(
            String sessionId,
            String viewId,
            String viewReleaseId,
            int closureVersion,
            String closureHash) {
    }
}
