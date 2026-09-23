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

    /**
     * 初始化MyBatis嵌入式启动记录入口适配器，保存构造参数供后续方法使用。
     *
     * @param mapper 映射器依赖，保存到当前对象供后续业务方法调用
     */
    public MyBatisEmbedLaunchEntryAdapter(EmbedLaunchEntryMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 查询嵌入式启动记录入口快照；结果供调用方展示或继续处理。
     *
     * @param launchId 启动记录ID，后续用于查询MyBatis嵌入式启动记录入口时定位或关联目标
     * @return 匹配的MyBatis嵌入式启动记录入口；未找到时为空
     */
    @Override
    public Optional<EmbedLaunchEntrySnapshot> find(String launchId) {
        return Optional.ofNullable(mapper.find(launchId)).map(this::snapshot);
    }

    /**
     * 处理快照，并将结果传给后续步骤。
     *
     * @param row 行，作为 {@code EmbedLaunchEntrySnapshot} 的输入影响后续处理
     * @return 处理后的快照结果，供调用方继续处理
     */
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

    /**
     * 处理绝对时间，并将结果传给后续步骤。
     *
     * @param value 待处理绝对时间的原始输入，结果供调用方继续使用
     * @return 处理后的绝对时间结果，供调用方继续处理
     */
    private static java.time.Instant instant(java.time.LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
