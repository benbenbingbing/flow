package com.workflow.embed.application.launch;

import com.workflow.embed.application.port.EmbedLaunchEntryLookupPort;
import com.workflow.embed.application.validation.EmbedOriginNormalizer;
import com.workflow.embed.domain.EmbedLaunchEntrySnapshot;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** 在返回可嵌入 HTML 前重新校验 Launch 及全部快速撤销版本。 */
@Service
@ConditionalOnProperty(prefix = "workflow.embed", name = "enabled", havingValue = "true")
public class EmbedLaunchEntryService {

    private final EmbedLaunchEntryLookupPort lookupPort;
    private final Clock clock;

    public EmbedLaunchEntryService(
            EmbedLaunchEntryLookupPort lookupPort,
            @Qualifier("embedClock") Clock clock) {
        this.lookupPort = lookupPort;
        this.clock = clock;
    }

    /**
     * 只为仍可兑换的 Launch 输出父 Origin 与 channel；任一撤销或过期条件都折叠为 empty，
     * 防止 Entry 页面成为资源存在性与账户状态的探针。
     */
    public Optional<EntryMetadata> resolve(String launchId) {
        if (launchId == null
                || !launchId.matches("^lch_[A-Za-z0-9_-]{16,60}$")) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return lookupPort.find(launchId)
                .filter(value -> active(value, now))
                .flatMap(value -> normalized(value).map(origin ->
                        new EntryMetadata(
                                value.launchId(),
                                origin,
                                value.channelId(),
                                "flow-embed/1")));
    }

    private static boolean active(
            EmbedLaunchEntrySnapshot value,
            Instant now) {
        return "ISSUED".equals(value.launchStatus())
                && after(value.launchExpiresAt(), now)
                && "ACTIVE".equals(value.applicationStatus())
                && windowOpen(value.applicationExpiresAt(), now)
                && value.applicationVersion() == value.currentApplicationVersion()
                && "ACTIVE".equals(value.viewStatus())
                && value.viewSecurityVersion() == value.currentViewSecurityVersion()
                && "ACTIVE".equals(value.grantStatus())
                && windowOpen(value.grantExpiresAt(), now)
                && value.grantSecurityVersion() == value.currentGrantSecurityVersion()
                && "ACTIVE".equals(value.providerStatus())
                && value.providerSecurityVersion() == value.currentProviderSecurityVersion()
                && "ACTIVE".equals(value.bindingStatus())
                && value.bindingEffectiveAt() != null
                && !value.bindingEffectiveAt().isAfter(now)
                && windowOpen(value.bindingExpiresAt(), now)
                && value.bindingVersion() == value.currentBindingVersion()
                && "0".equals(value.flowUserStatus())
                && !value.flowUserDeleted()
                && value.channelId() != null
                // 必须与 Launch API 的稳定 channel 契约完全一致，避免已签发 Launch 在 Entry
                // 边界因规则漂移被误判为不存在。
                && value.channelId().matches("^[A-Za-z0-9._:-]{16,128}$");
    }

    private static boolean after(Instant value, Instant now) {
        return value != null && value.isAfter(now);
    }

    private static boolean windowOpen(Instant expiresAt, Instant now) {
        return expiresAt == null || expiresAt.isAfter(now);
    }

    private static Optional<String> normalized(
            EmbedLaunchEntrySnapshot value) {
        try {
            return Optional.of(EmbedOriginNormalizer.normalize(
                    value.parentOrigin()));
        } catch (RuntimeException ignored) {
            // 持久化数据损坏也按通用不可嵌入处理，不把具体原因暴露给匿名 Entry 请求。
            return Optional.empty();
        }
    }

    public record EntryMetadata(
            String launchId,
            String expectedParentOrigin,
            String channelId,
            String protocolVersion) {
    }
}
