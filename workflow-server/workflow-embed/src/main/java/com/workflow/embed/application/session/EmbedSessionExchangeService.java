package com.workflow.embed.application.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.embed.application.audit.EmbedAuditCorrelation;
import com.workflow.embed.application.audit.EmbedLifecycleAudit;
import com.workflow.embed.application.port.EmbedContextProtectionPort;
import com.workflow.embed.application.port.EmbedDigestPort;
import com.workflow.embed.application.port.EmbedIdGeneratorPort;
import com.workflow.embed.application.port.EmbedLaunchExchangeLookupPort;
import com.workflow.embed.application.port.EmbedSecretGeneratorPort;
import com.workflow.embed.application.port.EmbedSessionExchangeTransactionPort;
import com.workflow.embed.application.port.EmbedTrafficControlPort;
import com.workflow.embed.application.validation.EmbedOriginNormalizer;
import com.workflow.embed.config.EmbedProperties;
import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.domain.EmbedLaunchExchangeCandidate;
import com.workflow.embed.domain.EmbedSessionExchangePlan;
import com.workflow.embed.domain.EmbedSessionIssued;
import com.workflow.embed.domain.ProtectedContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 将一个有效且未消费的 Launch code 兑换成权限受限的 opaque Embed Session token。 */
@Service
@ConditionalOnProperty(prefix = "workflow.embed", name = "enabled", havingValue = "true")
public class EmbedSessionExchangeService {

    private static final String PROTOCOL_VERSION = "flow-embed/1";
    private static final Pattern SAFE_CHANNEL = Pattern.compile("[A-Za-z0-9._:-]{16,128}");
    private static final Pattern BASE64_URL = Pattern.compile("[A-Za-z0-9_-]{43,128}");
    private static final TypeReference<Set<String>> STRING_SET = new TypeReference<>() { };
    private static final Set<String> V1_ENTRY_MODES = Set.of(
            "LIST", "CREATE", "VIEW");

    private final EmbedLaunchExchangeLookupPort lookupPort;
    private final EmbedSessionExchangeTransactionPort transactionPort;
    private final EmbedContextProtectionPort contextProtectionPort;
    private final EmbedSecretGeneratorPort secretGenerator;
    private final EmbedDigestPort digestPort;
    private final EmbedIdGeneratorPort idGenerator;
    private final EmbedProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final EmbedLifecycleAudit lifecycleAudit;
    private final EmbedTrafficControlPort trafficControlPort;

    public EmbedSessionExchangeService(
            EmbedLaunchExchangeLookupPort lookupPort,
            EmbedSessionExchangeTransactionPort transactionPort,
            EmbedContextProtectionPort contextProtectionPort,
            EmbedSecretGeneratorPort secretGenerator,
            EmbedDigestPort digestPort,
            EmbedIdGeneratorPort idGenerator,
            EmbedProperties properties,
            ObjectMapper objectMapper,
            @Qualifier("embedClock") Clock clock,
            EmbedLifecycleAudit lifecycleAudit,
            EmbedTrafficControlPort trafficControlPort) {
        this.lookupPort = lookupPort;
        this.transactionPort = transactionPort;
        this.contextProtectionPort = contextProtectionPort;
        this.secretGenerator = secretGenerator;
        this.digestPort = digestPort;
        this.idGenerator = idGenerator;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.lifecycleAudit = lifecycleAudit;
        this.trafficControlPort = trafficControlPort;
    }

    /**
     * 在事务外只准备不可变的加密材料；真正消费 Launch 前，由事务端口重新校验所有可变安全状态。
     *
     * <p>这样既缩短了持锁时间，也避免首次查询与加锁之间的配置变更造成 TOCTOU 越权。
     */
    @Transactional(rollbackFor = Exception.class)
    public EmbedSessionIssued exchange(
            EmbedSessionExchangeCommand command,
            EmbedAuditCorrelation correlation) {
        EmbedAuditCorrelation safeCorrelation = correlation == null
                ? EmbedAuditCorrelation.none() : correlation;
        try {
            EmbedSessionIssued issued = exchangeInternal(command);
            lifecycleAudit.exchangeSucceeded(issued.sessionId(), safeCorrelation);
            return issued;
        } catch (EmbedException error) {
            lifecycleAudit.exchangeRejected(error.getErrorCode(), safeCorrelation);
            throw error;
        } catch (RuntimeException error) {
            lifecycleAudit.exchangeRejected(null, safeCorrelation);
            throw error;
        }
    }

    private EmbedSessionIssued exchangeInternal(EmbedSessionExchangeCommand command) {
        validate(command);
        // 必须在 code 摘要索引查询之前扣减，否则随机 code 可放大数据库读压力。
        trafficControlPort.consumeExchange(command.launchId(), command.peerAddress());
        Instant now = clock.instant();
        String codeDigest = digestPort.sha256(command.launchCode());
        EmbedLaunchExchangeCandidate candidate = lookupPort.findByCodeDigest(codeDigest)
                .orElseThrow(EmbedSessionExchangeService::launchInvalid);
        if (!candidate.launch().id().equals(command.launchId())
                || !constantTimeEquals(candidate.launch().launchCodeDigest(), codeDigest)
                || !candidate.launch().channelId().equals(command.channelId())) {
            throw launchInvalid();
        }
        String normalizedOrigin = EmbedOriginNormalizer.normalize(command.parentOrigin());
        if (!candidate.launch().parentOrigin().equals(normalizedOrigin)) {
            throw launchInvalid();
        }
        if (!"ISSUED".equals(candidate.status())) {
            throw launchInvalid();
        }
        if (!candidate.launch().expiresAt().isAfter(now)) {
            // 只有匹配高熵 code 后才返回“已过期”，避免攻击者通过 launchId 探测 Launch 是否存在。
            throw new EmbedException(
                    410,
                    EmbedErrorCode.EMBED_LAUNCH_EXPIRED,
                    "Embed launch has expired");
        }
        if (!V1_ENTRY_MODES.contains(candidate.launch().entryMode())) {
            throw new EmbedException(
                    403,
                    EmbedErrorCode.EMBED_OPERATION_NOT_ALLOWED,
                    "Embed entry mode is not supported");
        }

        Map<String, Object> context = contextProtectionPort.unprotectLaunch(
                candidate.launch().applicationId(),
                candidate.launch().id(),
                candidate.launch().context());
        String sessionId = idGenerator.nextSessionId();
        String accessToken = secretGenerator.generate(properties.getSecretBytes());
        ProtectedContext sessionContext = contextProtectionPort.protectSession(
                candidate.launch().applicationId(), sessionId, context);
        String capabilitiesJson = intersectCapabilities(candidate);
        int absoluteSeconds = Math.min(
                properties.getSessionAbsoluteSeconds(),
                candidate.maxSessionSeconds());
        Instant absoluteExpiry = now.plusSeconds(absoluteSeconds);
        Instant idleExpiry = min(
                now.plusSeconds(properties.getSessionIdleSeconds()),
                absoluteExpiry);

        EmbedSessionExchangePlan plan = new EmbedSessionExchangePlan(
                candidate,
                sessionId,
                digestPort.sha256(accessToken),
                digestNonce(command.parentNonce()),
                digestNonce(command.childNonce()),
                sessionContext,
                capabilitiesJson,
                now,
                idleExpiry,
                absoluteExpiry);
        transactionPort.exchange(plan);
        return new EmbedSessionIssued(
                sessionId,
                accessToken,
                absoluteExpiry,
                idleExpiry,
                60,
                "/api/embed/v1/runtime/bootstrap",
                PROTOCOL_VERSION);
    }

    private void validate(EmbedSessionExchangeCommand command) {
        if (command == null
                || command.launchId() == null || command.launchId().isBlank()
                || command.launchId().length() > 64
                || command.launchCode() == null
                || !BASE64_URL.matcher(command.launchCode()).matches()
                || command.channelId() == null
                || !SAFE_CHANNEL.matcher(command.channelId()).matches()
                || command.peerAddress() == null
                || command.peerAddress().isBlank()
                || command.peerAddress().length() > 128
                || command.sdkVersion() != null && command.sdkVersion().length() > 64) {
            throw new EmbedException(400, EmbedErrorCode.INVALID_REQUEST,
                    "Embed exchange request is invalid");
        }
        validateNonce(command.parentNonce());
        validateNonce(command.childNonce());
    }

    private void validateNonce(String nonce) {
        if (nonce == null || !BASE64_URL.matcher(nonce).matches()) {
            throw new EmbedException(400, EmbedErrorCode.INVALID_REQUEST,
                    "Handshake nonce is invalid");
        }
        try {
            if (Base64.getUrlDecoder().decode(nonce).length < 32) {
                throw new EmbedException(400, EmbedErrorCode.INVALID_REQUEST,
                        "Handshake nonce is invalid");
            }
        } catch (IllegalArgumentException error) {
            throw new EmbedException(400, EmbedErrorCode.INVALID_REQUEST,
                    "Handshake nonce is invalid");
        }
    }

    private String digestNonce(String nonce) {
        return digestPort.sha256(nonce);
    }

    private String intersectCapabilities(EmbedLaunchExchangeCandidate candidate) {
        Set<String> release = parseSet(candidate.releaseCapabilitiesJson());
        Set<String> ceiling = parseSet(candidate.grantCapabilitiesJson());
        Set<String> intersection = new TreeSet<>(release);
        intersection.retainAll(ceiling);
        // 即使发布配置异常，也在兑换边界强制剔除 V1 明令禁止的高风险能力。
        intersection.removeAll(Set.of(
                "RECORD_UPDATE", "ACTION_EXECUTE", "PROCESS_START",
                "RECORD_DELETE", "BATCH_DELETE", "EXPORT",
                "FILE_UPLOAD", "FILE_DOWNLOAD"));
        try {
            return objectMapper.writeValueAsString(intersection);
        } catch (JsonProcessingException error) {
            throw unavailable(error);
        }
    }

    private Set<String> parseSet(String json) {
        try {
            Set<String> values = objectMapper.readValue(json, STRING_SET);
            return values == null ? Set.of() : new LinkedHashSet<>(values);
        } catch (JsonProcessingException error) {
            throw unavailable(error);
        }
    }

    private static Instant min(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return expected != null && actual != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII));
    }

    private static EmbedException launchInvalid() {
        return new EmbedException(
                401,
                EmbedErrorCode.EMBED_LAUNCH_INVALID,
                "Embed launch is invalid");
    }

    private static EmbedException unavailable(Throwable error) {
        return new EmbedException(
                503,
                EmbedErrorCode.EMBED_RUNTIME_UNAVAILABLE,
                "Published Embed capabilities are unavailable",
                null,
                error);
    }
}
