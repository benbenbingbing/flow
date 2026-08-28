package com.workflow.embed.infrastructure.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.workflow.embed.application.port.EmbedRemoteJwkSetPort;
import com.workflow.embed.domain.EmbedIdentityProviderSnapshot;
import com.workflow.embed.domain.EmbedIdentityProviderPolicy;
import com.workflow.http.HttpTransportRequest;
import com.workflow.http.HttpTransportResult;
import com.workflow.http.PinnedHttpTransport;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 通过 {@link PinnedHttpTransport} 加载远程 JWK Set，并提供有界 TTL 缓存和单飞刷新。
 *
 * <p>地址从已发布 Provider 快照中提取，allowed host 始终是 URL 的精确 host；底层传输负责
 * DNS 固定、逐地址拒私网、禁止重定向/Cookie/Auth。该适配器不记录 URL 或响应正文，异常也
 * 只暴露固定消息，避免 query 或密钥材料进入日志。</p>
 */
@Component
@ConditionalOnProperty(prefix = "workflow.embed", name = "enabled", havingValue = "true")
public final class PinnedHttpEmbedRemoteJwkSetAdapter implements EmbedRemoteJwkSetPort {

    static final int MAX_RESPONSE_BYTES = 256 * 1024;
    static final int REQUEST_TIMEOUT_MILLIS = 3_000;
    static final int MAX_KEYS = 20;
    private static final int DEFAULT_MAX_CACHE_ENTRIES = 256;
    private static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(5);
    private static final Duration DEFAULT_FORCE_REFRESH_COOLDOWN = Duration.ofSeconds(10);
    private static final Set<String> PRIVATE_JWK_PARAMETERS = Set.of(
            "d", "p", "q", "dp", "dq", "qi", "oth", "k", "seed");
    private static final Set<String> RECOGNIZED_PUBLIC_KEY_TYPES = Set.of("RSA", "EC", "OKP");

    private final PinnedHttpTransport transport;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration cacheTtl;
    private final Duration forceRefreshCooldown;
    private final int maxCacheEntries;
    private final Object cacheMonitor = new Object();
    private final LinkedHashMap<CacheKey, CacheEntry> cache =
            new LinkedHashMap<>(16, 0.75f, true);
    private final LinkedHashMap<CacheKey, Instant> refreshRetryAfter =
            new LinkedHashMap<>(16, 0.75f, true);
    private final ConcurrentHashMap<CacheKey, CompletableFuture<CacheEntry>> inFlight =
            new ConcurrentHashMap<>();

    @Autowired
    public PinnedHttpEmbedRemoteJwkSetAdapter(
            PinnedHttpTransport transport,
            ObjectMapper objectMapper) {
        this(transport, objectMapper, Clock.systemUTC(), DEFAULT_CACHE_TTL,
                DEFAULT_FORCE_REFRESH_COOLDOWN, DEFAULT_MAX_CACHE_ENTRIES);
    }

    PinnedHttpEmbedRemoteJwkSetAdapter(
            PinnedHttpTransport transport,
            ObjectMapper objectMapper,
            Clock clock,
            Duration cacheTtl,
            Duration forceRefreshCooldown,
            int maxCacheEntries) {
        if (transport == null || objectMapper == null || clock == null
                || cacheTtl == null || cacheTtl.isZero() || cacheTtl.isNegative()
                || forceRefreshCooldown == null || forceRefreshCooldown.isNegative()
                || forceRefreshCooldown.compareTo(cacheTtl) > 0
                || maxCacheEntries < 1 || maxCacheEntries > 1_024) {
            throw new IllegalArgumentException("Remote JWKS adapter configuration is invalid");
        }
        this.transport = transport;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.cacheTtl = cacheTtl;
        this.forceRefreshCooldown = forceRefreshCooldown;
        this.maxCacheEntries = maxCacheEntries;
    }

    /**
     * 使用 TTL 缓存；未知 kid 的强制刷新会绕过普通缓存，但同 key 并发请求只允许一个出站调用。
     */
    @Override
    public String load(
            EmbedIdentityProviderSnapshot provider,
            boolean forceRefresh) {
        RemoteEndpoint endpoint = requireEndpoint(provider);
        CacheKey key = new CacheKey(
                provider.id(), provider.securityVersion(), provider.keyVersion(), endpoint.url());
        Instant now = clock.instant();
        CacheEntry reusable = reusableWithoutFetch(key, forceRefresh, now);
        if (reusable != null) {
            return reusable.jwkSetJson();
        }

        // putIfAbsent 形成每个 Provider 安全快照的 single-flight。等待者复用同一个结果，
        // 无论是冷启动还是 unknown kid 强刷，都不会把并发峰值放大成外部请求风暴。
        CompletableFuture<CacheEntry> ownFlight = new CompletableFuture<>();
        CompletableFuture<CacheEntry> activeFlight = inFlight.putIfAbsent(key, ownFlight);
        if (activeFlight != null) {
            return await(activeFlight).jwkSetJson();
        }

        try {
            // 首次缓存检查与注册 single-flight 之间可能发生线程切换。成为 owner 后必须重查，
            // 否则前一轮刚完成并移除 flight 时，排队线程会立刻发起重复请求。
            now = clock.instant();
            reusable = reusableWithoutFetch(key, forceRefresh, now);
            if (reusable != null) {
                ownFlight.complete(reusable);
                return reusable.jwkSetJson();
            }
            String json = fetchAndValidate(endpoint, forceRefresh);
            CacheEntry loaded = new CacheEntry(
                    json,
                    now.plus(cacheTtl),
                    forceRefresh ? now : null);
            store(key, loaded, now);
            clearRefreshFailure(key);
            ownFlight.complete(loaded);
            return loaded.jwkSetJson();
        } catch (RuntimeException exception) {
            // 失败也进入短冷却。否则攻击者可用连续 unknown kid 把串行请求放大成持续的
            // 3 秒出站调用；保留旧公钥缓存，让既有 kid 在远端故障时仍可继续验证。
            suppressRefreshRetry(key, clock.instant());
            ownFlight.completeExceptionally(exception);
            throw exception;
        } catch (Error error) {
            ownFlight.completeExceptionally(error);
            throw error;
        } finally {
            inFlight.remove(key, ownFlight);
        }
    }

    private String fetchAndValidate(RemoteEndpoint endpoint, boolean forceRefresh) {
        Map<String, String> headers = forceRefresh
                ? Map.of(
                        "Accept", "application/json",
                        "User-Agent", "Flow-Embed-JWKS/1",
                        "Cache-Control", "no-cache")
                : Map.of(
                        "Accept", "application/json",
                        "User-Agent", "Flow-Embed-JWKS/1");
        HttpTransportRequest request = new HttpTransportRequest(
                "GET",
                endpoint.uri(),
                headers,
                null,
                REQUEST_TIMEOUT_MILLIS,
                Set.of(endpoint.host()),
                MAX_RESPONSE_BYTES,
                false);
        HttpTransportResult result;
        try {
            // 始终调用严格入口，不允许继承 workflow.http.allow-private-addresses 的宽松配置。
            result = transport.execute(request);
        } catch (IOException | IllegalArgumentException exception) {
            throw unavailable();
        }
        if (result == null || result.statusCode() != 200 || result.responseTruncated()) {
            throw unavailable();
        }
        return validateJwkSet(result.body());
    }

    private String validateJwkSet(String body) {
        if (body == null
                || body.getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
            throw unavailable();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode keys = root == null ? null : root.get("keys");
            if (root == null || !root.isObject() || keys == null || !keys.isArray()
                    || keys.isEmpty()) {
                throw unavailable();
            }

            LinkedHashSet<String> keyIds = new LinkedHashSet<>();
            List<JWK> parsedKeys = new ArrayList<>(Math.min(keys.size(), MAX_KEYS));
            for (JsonNode keyNode : keys) {
                boolean supported = validatePublicKeyShape(keyNode, keyIds);
                if (!supported) {
                    // 常见 IdP 会在同一远程集合中发布 EdDSA/OKP 与 RSA/EC。V1 对 OKP
                    // 做公开性校验后丢弃，不让额外 key 使受支持的轮换集合整体不可用。
                    continue;
                }
                JWK key = JWK.parse(keyNode.toString());
                if (key.isPrivate()) {
                    throw unavailable();
                }
                parsedKeys.add(key);
                if (parsedKeys.size() > MAX_KEYS) {
                    throw unavailable();
                }
            }
            if (parsedKeys.isEmpty()) {
                throw unavailable();
            }
            // 丢弃远程文档的未知顶层成员，并再次由 Nimbus 只序列化公开部分。
            String publicJwkSet = new JWKSet(parsedKeys).toString(false);
            if (publicJwkSet.getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
                throw unavailable();
            }
            return publicJwkSet;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    private static boolean validatePublicKeyShape(
            JsonNode key,
            Set<String> keyIds) {
        if (key == null || !key.isObject()) {
            throw unavailable();
        }
        String kid = text(key, "kid");
        String keyType = text(key, "kty");
        if (!StringUtils.hasText(kid) || kid.length() > 128 || !keyIds.add(kid)
                || !RECOGNIZED_PUBLIC_KEY_TYPES.contains(keyType)) {
            throw unavailable();
        }
        Iterator<String> names = key.fieldNames();
        while (names.hasNext()) {
            if (PRIVATE_JWK_PARAMETERS.contains(names.next())) {
                throw unavailable();
            }
        }
        boolean publicMaterial = switch (keyType) {
            case "RSA" -> boundedText(key, "n") && boundedText(key, "e");
            case "EC" -> boundedText(key, "crv") && boundedText(key, "x")
                    && boundedText(key, "y");
            case "OKP" -> boundedText(key, "crv") && boundedText(key, "x");
            default -> false;
        };
        if (!publicMaterial
                || (key.hasNonNull("use") && !"sig".equals(key.path("use").asText()))
                || (key.hasNonNull("key_ops")
                    && (!key.path("key_ops").isArray()
                        || !containsText(key.path("key_ops"), "verify")))) {
            throw unavailable();
        }
        return EmbedIdentityProviderPolicy.supportsPublicJwkType(keyType);
    }

    private static RemoteEndpoint requireEndpoint(EmbedIdentityProviderSnapshot provider) {
        if (provider == null || !provider.isActive()
                || !"SIGNED_JWT".equals(provider.type())
                || !"REMOTE_JWKS".equals(provider.jwksMode())
                || StringUtils.hasText(provider.jwksJson())
                || !StringUtils.hasText(provider.id()) || provider.id().length() > 128
                || provider.securityVersion() < 1 || provider.keyVersion() < 1
                || !StringUtils.hasText(provider.jwksUrl())
                || provider.jwksUrl().length() > 2_048) {
            throw new IllegalArgumentException("Remote JWKS provider is invalid");
        }
        try {
            URI uri = new URI(provider.jwksUrl().trim()).normalize();
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !StringUtils.hasText(uri.getHost())
                    || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null
                    || uri.getPort() == 0 || uri.getPort() > 65_535) {
                throw new IllegalArgumentException("Remote JWKS provider is invalid");
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            return new RemoteEndpoint(uri, host, uri.toASCIIString());
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Remote JWKS provider is invalid");
        }
    }

    private CacheEntry cached(CacheKey key, Instant now) {
        synchronized (cacheMonitor) {
            CacheEntry value = cache.get(key);
            if (value != null && !now.isBefore(value.expiresAt())) {
                cache.remove(key);
                return null;
            }
            return value;
        }
    }

    private CacheEntry reusableWithoutFetch(
            CacheKey key,
            boolean forceRefresh,
            Instant now) {
        CacheEntry cached = cached(key, now);
        if (cached != null
                && (!forceRefresh || recentlyForceRefreshed(cached, now))) {
            return cached;
        }
        if (!refreshRetrySuppressed(key, now)) {
            return null;
        }
        if (cached != null) {
            return cached;
        }
        throw unavailable();
    }

    private void store(CacheKey key, CacheEntry value, Instant now) {
        synchronized (cacheMonitor) {
            cache.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
            cache.put(key, value);
            while (cache.size() > maxCacheEntries) {
                Iterator<CacheKey> keys = cache.keySet().iterator();
                keys.next();
                keys.remove();
            }
        }
    }

    private boolean refreshRetrySuppressed(CacheKey key, Instant now) {
        synchronized (cacheMonitor) {
            Instant retryAfter = refreshRetryAfter.get(key);
            if (retryAfter != null && !now.isBefore(retryAfter)) {
                refreshRetryAfter.remove(key);
                return false;
            }
            return retryAfter != null;
        }
    }

    private void suppressRefreshRetry(CacheKey key, Instant now) {
        synchronized (cacheMonitor) {
            refreshRetryAfter.entrySet().removeIf(
                    entry -> !now.isBefore(entry.getValue()));
            refreshRetryAfter.put(key, now.plus(forceRefreshCooldown));
            while (refreshRetryAfter.size() > maxCacheEntries) {
                Iterator<CacheKey> keys = refreshRetryAfter.keySet().iterator();
                keys.next();
                keys.remove();
            }
        }
    }

    private void clearRefreshFailure(CacheKey key) {
        synchronized (cacheMonitor) {
            refreshRetryAfter.remove(key);
        }
    }

    private boolean recentlyForceRefreshed(CacheEntry entry, Instant now) {
        return entry.forceRefreshedAt() != null
                && now.isBefore(entry.forceRefreshedAt().plus(forceRefreshCooldown));
    }

    private static CacheEntry await(CompletableFuture<CacheEntry> future) {
        try {
            return future.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw unavailable();
        }
    }

    private static boolean boundedText(JsonNode node, String field) {
        String value = text(node, field);
        return StringUtils.hasText(value) && value.length() <= 16_384;
    }

    private static boolean containsText(JsonNode values, String expected) {
        for (JsonNode value : values) {
            if (value.isTextual() && expected.equals(value.textValue())) {
                return true;
            }
        }
        return false;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Remote JWK Set is unavailable");
    }

    private record RemoteEndpoint(URI uri, String host, String url) {
    }

    private record CacheKey(
            String providerId,
            long securityVersion,
            long keyVersion,
            String url) {
    }

    private record CacheEntry(
            String jwkSetJson,
            Instant expiresAt,
            Instant forceRefreshedAt) {
    }
}
