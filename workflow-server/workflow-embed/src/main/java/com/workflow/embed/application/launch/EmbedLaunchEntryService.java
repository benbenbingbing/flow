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

    /**
     * 初始化嵌入式启动记录入口服务，保存构造参数供后续方法使用。
     *
     * @param lookupPort 查找端口依赖，保存到当前对象供后续业务方法调用
     * @param clock 时钟依赖，保存到当前对象供后续业务方法调用
     */
    public EmbedLaunchEntryService(
            EmbedLaunchEntryLookupPort lookupPort,
            @Qualifier("embedClock") Clock clock) {
        this.lookupPort = lookupPort;
        this.clock = clock;
    }

    /**
     * 只为仍可兑换的 Launch 输出父 Origin 与 channel；任一撤销或过期条件都折叠为 empty，
     * 防止 Entry 页面成为资源存在性与账户状态的探针。
     *
     * @param launchId 启动记录ID，后续用于解析嵌入式启动记录入口时定位或关联目标
     * @return 匹配的嵌入式启动记录入口；未找到时为空
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

    /**
     * 判断活动条件是否成立，供调用方选择后续分支。
     *
     * @param value 待处理活动的原始输入，结果供调用方继续使用
     * @param now 当前时间，作为 {@code isAfter} 的输入影响后续处理
     * @return 活动条件成立时为 true，否则为 false
     */
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

    /**
     * 判断之后条件是否成立，供调用方选择后续分支。
     *
     * @param value 待处理之后的原始输入，结果供调用方继续使用
     * @param now 当前时间，作为 {@code value.isAfter} 的输入影响后续处理
     * @return 之后条件成立时为 true，否则为 false
     */
    private static boolean after(Instant value, Instant now) {
        return value != null && value.isAfter(now);
    }

    /**
     * 判断{@code window}打开条件是否成立，供调用方选择后续分支。
     *
     * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
     * @param now 当前时间，作为 {@code expiresAt.isAfter} 的输入影响后续处理
     * @return {@code window}打开条件成立时为 true，否则为 false
     */
    private static boolean windowOpen(Instant expiresAt, Instant now) {
        return expiresAt == null || expiresAt.isAfter(now);
    }

    /**
     * 处理规范化，并将结果传给后续步骤。
     *
     * @param value 待处理规范化的原始输入，结果供调用方继续使用
     * @return 匹配的规范化；未找到时为空
     */
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

    /**
     * 封装入口元数据的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param launchId 启动记录ID，后续用于处理入口元数据时定位或关联目标
     * @param expectedParentOrigin 预期父级来源，保存在对象中供后续校验、查询或展示
     * @param channelId 通道ID，后续用于处理入口元数据时定位或关联目标
     * @param protocolVersion {@code protocol}版本，保存在对象中供后续校验、查询或展示
     */
    public record EntryMetadata(
            String launchId,
            String expectedParentOrigin,
            String channelId,
            String protocolVersion) {
    }
}
