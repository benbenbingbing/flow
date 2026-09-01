package com.workflow.embed.application.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.embed.EmbedNativeListDependencyClosure;
import com.workflow.contracts.embed.EmbedNativeListDependencySnapshotPort;
import com.workflow.embed.application.port.EmbedRuntimeReleasePort;
import com.workflow.embed.domain.EmbedRuntimeReleaseSnapshot;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 从 Session 固定的 immutable View Release 恢复 LIST 依赖闭包。 */
@Component
public class EmbedNativeListDependencySnapshotAdapter
        implements EmbedNativeListDependencySnapshotPort {

    private final EmbedRuntimeReleasePort releasePort;
    private final ObjectMapper objectMapper;

    public EmbedNativeListDependencySnapshotAdapter(
            EmbedRuntimeReleasePort releasePort,
            ObjectMapper objectMapper) {
        this.releasePort = releasePort;
        this.objectMapper = objectMapper;
    }

    /**
     * 同时校验 sessionId、viewId、viewReleaseId、闭包版本和摘要；任一不符即拒绝。
     */
    @Override
    public EmbedNativeListDependencyClosure read(Reference reference) {
        if (reference == null
                || !StringUtils.hasText(reference.sessionId())
                || !StringUtils.hasText(reference.viewId())
                || !StringUtils.hasText(reference.viewReleaseId())
                || reference.closureVersion()
                != EmbedNativeListDependencyClosure.CURRENT_VERSION
                || !StringUtils.hasText(reference.closureHash())) {
            throw new IllegalArgumentException("Embed LIST 依赖闭包引用不完整");
        }
        EmbedRuntimeReleaseSnapshot release = releasePort.find(
                reference.sessionId(),
                reference.viewId(),
                reference.viewReleaseId());
        if (release == null
                || !"LIST".equals(release.surfaceType())
                || !Objects.equals(reference.viewId(), release.viewId())
                || !Objects.equals(
                reference.viewReleaseId(), release.releaseId())) {
            throw new IllegalArgumentException("Embed LIST 依赖快照归属不一致");
        }
        EmbedNativeListDependencyClosureCodec.Decoded decoded =
                EmbedNativeListDependencyClosureCodec.decode(
                        objectMapper,
                        release.configJson());
        if (decoded.closure().version() != reference.closureVersion()
                || !Objects.equals(
                decoded.hash(), reference.closureHash())) {
            throw new IllegalArgumentException("Embed LIST 依赖闭包版本或摘要不一致");
        }
        return decoded.closure();
    }
}
