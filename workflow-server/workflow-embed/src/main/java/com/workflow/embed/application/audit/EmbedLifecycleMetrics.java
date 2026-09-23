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

    /**
     * 初始化嵌入式生命周期指标集合，保存构造参数供后续方法使用。
     *
     * @param registryProvider {@code registry}提供者，保存在对象中供后续校验、查询或展示
     */
    public EmbedLifecycleMetrics(ObjectProvider<MeterRegistry> registryProvider) {
        this.registry = registryProvider.getIfAvailable();
    }

    /**
     * 标签值只接受封闭枚举，禁止把任何业务 ID、Origin 或外部输入变成指标标签。
     *
     * @param surface 界面，作为 {@code registry.counter} 的输入影响后续处理
     * @param outcome 结果，供本方法记录嵌入式生命周期指标集合时使用
     * @param reason 原因，供本方法记录嵌入式生命周期指标集合时使用
     */
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

    /**
     * 定义界面的可选值；调用方据此选择对应的处理分支。
     */
    public enum Surface {
        LAUNCH("launch"),
        EXCHANGE("exchange"),
        AUTH("auth"),
        RUNTIME("runtime"),
        LOGOUT("logout"),
        EXPIRY("expiry"),
        ADMIN_REVOKE("admin_revoke");

        private final String tag;

        /**
         * 初始化界面，保存构造参数供后续方法使用。
         *
         * @param tag 标签依赖，保存到当前对象供后续业务方法调用
         */
        Surface(String tag) {
            this.tag = tag;
        }

        /**
         * 生成标签文本，供后续匹配或展示。
         *
         * @return 处理后的标签文本，供调用方比较或展示
         */
        public String tag() {
            return tag;
        }
    }

    /**
     * 定义结果的可选值；调用方据此选择对应的处理分支。
     */
    public enum Outcome {
        SUCCESS("success"),
        REJECTED("rejected"),
        IDEMPOTENT("idempotent");

        private final String tag;

        /**
         * 初始化结果，保存构造参数供后续方法使用。
         *
         * @param tag 标签依赖，保存到当前对象供后续业务方法调用
         */
        Outcome(String tag) {
            this.tag = tag;
        }

        /**
         * 生成标签文本，供后续匹配或展示。
         *
         * @return 处理后的标签文本，供调用方比较或展示
         */
        public String tag() {
            return tag;
        }
    }

    /**
     * 定义原因的可选值；调用方据此选择对应的处理分支。
     */
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

        /**
         * 初始化原因，保存构造参数供后续方法使用。
         *
         * @param tag 标签依赖，保存到当前对象供后续业务方法调用
         */
        Reason(String tag) {
            this.tag = tag;
        }

        /**
         * 生成标签文本，供后续匹配或展示。
         *
         * @return 处理后的标签文本，供调用方比较或展示
         */
        public String tag() {
            return tag;
        }

        /**
         * 将公开错误码压缩到固定的低基数原因集合。
         *
         * @param errorCode 错误编码，后续用于处理起始时定位或关联目标
         * @return 处理后的起始结果，供调用方继续处理
         */
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
