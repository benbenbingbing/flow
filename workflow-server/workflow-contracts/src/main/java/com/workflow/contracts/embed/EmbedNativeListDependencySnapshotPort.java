package com.workflow.contracts.embed;

/**
 * 在运行时按 Embed Session 固定的 View Release 读取 LIST 依赖闭包。
 *
 * <p>实现必须从不可变 Runtime Snapshot 读取，并同时校验 session/view/release 归属、
 * closure 版本与 canonical hash。该端口让 {@code elr1} 只携带短引用，不把完整闭包
 * 放进 URL。</p>
 */
public interface EmbedNativeListDependencySnapshotPort {

    EmbedNativeListDependencyClosure read(Reference reference);

    record Reference(
            String sessionId,
            String viewId,
            String viewReleaseId,
            int closureVersion,
            String closureHash) {
    }
}
