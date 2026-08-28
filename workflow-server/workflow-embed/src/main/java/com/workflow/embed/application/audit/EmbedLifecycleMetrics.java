package com.workflow.embed.application.audit;

import com.workflow.embed.domain.EmbedErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 可选的低基数 Embed 生命周期指标；没有 MeterRegistry 时为 no-op。 */
@Component
@ConditionalOnProperty(prefix = "workflow.embed", name = "enabled", havingValue = "true")
public class EmbedLifecycleMetrics {

    private final MeterRegistry registry;

    public EmbedLifecycleMetrics(ObjectProvider<MeterRegistry> registryProvider) {
        this.registry = registryProvider.getIfAvailable();
    }

    /** 标签值只接受封闭枚举，禁止把任何业务 ID、Origin 或外部输入变成指标标签。 */
    public void record(Surface surface, Outcome outcome, Reason reason) {
        if (registry == null) {
            return;
        }
        registry.counter(
                "workflow.embed.lifecycle",
                "surface", surface.tag(),
                "outcome", outcome.tag(),
                "reason", reason.tag()).increment();
    }

    public enum Surface {
        LAUNCH("launch"),
        EXCHANGE("exchange"),
        AUTH("auth"),
        RUNTIME("runtime"),
        LOGOUT("logout"),
        EXPIRY("expiry"),
        ADMIN_REVOKE("admin_revoke");

        private final String tag;

        Surface(String tag) {
            this.tag = tag;
        }

        public String tag() {
            return tag;
        }
    }

    public enum Outcome {
        SUCCESS("success"),
        REJECTED("rejected"),
        IDEMPOTENT("idempotent");

        private final String tag;

        Outcome(String tag) {
            this.tag = tag;
        }

        public String tag() {
            return tag;
        }
    }

    public enum Reason {
        NONE("none"),
        INVALID_REQUEST("invalid_request"),
        INVALID_CREDENTIAL("invalid_credential"),
        EXPIRED("expired"),
        REVOKED("revoked"),
        RATE_LIMIT("rate_limit"),
        SESSION_LIMIT("session_limit"),
        SECURITY_POLICY("security_policy"),
        FORBIDDEN("forbidden"),
        CONFLICT("conflict"),
        UNAVAILABLE("unavailable"),
        INTERNAL_ERROR("internal_error"),
        ALREADY_TERMINAL("already_terminal");

        private final String tag;

        Reason(String tag) {
            this.tag = tag;
        }

        public String tag() {
            return tag;
        }

        /** 将公开错误码压缩到固定的低基数原因集合。 */
        public static Reason from(EmbedErrorCode errorCode) {
            if (errorCode == null) {
                return INTERNAL_ERROR;
            }
            return switch (errorCode) {
                case INVALID_REQUEST, EMBED_CONTEXT_INVALID -> INVALID_REQUEST;
                case EMBED_LAUNCH_INVALID, EMBED_SESSION_INVALID,
                        EMBED_IDENTITY_ASSERTION_INVALID,
                        EMBED_IDENTITY_ASSERTION_REPLAYED -> INVALID_CREDENTIAL;
                case EMBED_LAUNCH_EXPIRED, EMBED_SESSION_EXPIRED -> EXPIRED;
                case EMBED_SESSION_REVOKED -> REVOKED;
                case RATE_LIMIT_EXCEEDED -> RATE_LIMIT;
                case EMBED_SESSION_LIMIT_EXCEEDED -> SESSION_LIMIT;
                case EMBED_VIEW_DISABLED, FLOW_USER_DISABLED -> SECURITY_POLICY;
                case EMBED_VIEW_NOT_GRANTED, EMBED_ORIGIN_NOT_ALLOWED,
                        EXTERNAL_IDENTITY_NOT_MAPPED, EMBED_OPERATION_NOT_ALLOWED,
                        EMBED_RESOURCE_NOT_FOUND -> FORBIDDEN;
                case EMBED_IDEMPOTENCY_KEY_REUSED, EMBED_REQUEST_IN_PROGRESS,
                        EMBED_RECORD_CONFLICT -> CONFLICT;
                case EMBED_RUNTIME_UNAVAILABLE -> UNAVAILABLE;
                default -> INTERNAL_ERROR;
            };
        }
    }
}
