package com.workflow.embed.infrastructure.persistence.adapter;

import com.workflow.embed.application.port.EmbedLaunchEntryLookupPort;
import com.workflow.embed.domain.EmbedLaunchEntrySnapshot;
import com.workflow.embed.infrastructure.persistence.mapper.EmbedLaunchEntryMapper;
import com.workflow.embed.infrastructure.persistence.record.EmbedLaunchEntryRow;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for non-secret dynamic Entry metadata. */
@Repository
@ConditionalOnProperty(prefix = "workflow.embed", name = "enabled", havingValue = "true")
public class MyBatisEmbedLaunchEntryAdapter
        implements EmbedLaunchEntryLookupPort {

    private final EmbedLaunchEntryMapper mapper;

    public MyBatisEmbedLaunchEntryAdapter(EmbedLaunchEntryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<EmbedLaunchEntrySnapshot> find(String launchId) {
        return Optional.ofNullable(mapper.find(launchId)).map(this::snapshot);
    }

    private EmbedLaunchEntrySnapshot snapshot(EmbedLaunchEntryRow row) {
        return new EmbedLaunchEntrySnapshot(
                row.launchId(), row.parentOrigin(), row.channelId(), row.launchStatus(),
                instant(row.launchExpiresAt()), row.applicationVersion(),
                row.currentApplicationVersion(), row.applicationStatus(),
                instant(row.applicationExpiresAt()), row.grantSecurityVersion(),
                row.currentGrantSecurityVersion(), row.grantStatus(),
                instant(row.grantExpiresAt()), row.viewSecurityVersion(),
                row.currentViewSecurityVersion(), row.viewStatus(),
                row.providerSecurityVersion(), row.currentProviderSecurityVersion(),
                row.providerStatus(), row.bindingVersion(), row.currentBindingVersion(),
                row.bindingStatus(), instant(row.bindingEffectiveAt()),
                instant(row.bindingExpiresAt()), row.flowUserStatus(),
                row.flowUserDeleted());
    }

    private static java.time.Instant instant(java.time.LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
