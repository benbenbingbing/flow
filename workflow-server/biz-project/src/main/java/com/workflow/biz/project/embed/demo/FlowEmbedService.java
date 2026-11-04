package com.workflow.biz.project.embed.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 将宿主登录态和业务授权转换为 Flow 的一次性 Launch，不向浏览器暴露机器凭据。 */
@Service
@Profile("partner-embed-demo")
class FlowEmbedService {
    private final EmbedDemoProperties properties;
    private final FlowHttpClient http;
    private final FlowMachineTokenService tokens;
    private final PartnerAssertionService assertions;
    private final ObjectMapper mapper;
    private final Clock clock;

    FlowEmbedService(EmbedDemoProperties properties, FlowHttpClient http, FlowMachineTokenService tokens,
            PartnerAssertionService assertions, ObjectMapper mapper, @Qualifier("partnerEmbedDemoClock") Clock clock) {
        this.properties = properties;
        this.http = http;
        this.tokens = tokens;
        this.assertions = assertions;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * 为当前登录用户创建 Launch。login 必须来自服务端认证上下文；参数/业务授权失败时不调用 Flow。
     * 仅对明确的机器 401 刷新后重试一次；网络超时、429 等不会自动重复签发 Launch。
     */
    Launch createLaunch(String login, HostLaunchRequest request, String traceId) {
        var host = properties.host();
        if (!host.username().equals(login)) {
            throw new EmbedDemoException(403, "HOST_USER_NOT_ALLOWED", traceId, null);
        }
        ObjectNode entry = authorizeEntry(request.entry(), traceId);
        for (int attempt = 0; attempt < 2; attempt++) {
            String token = tokens.getToken(traceId);
            ObjectNode body = mapper.createObjectNode();
            body.put("viewKey", properties.flow().viewKey());
            body.put("parentOrigin", properties.flow().parentOrigin().toString());
            body.put("channelId", request.channelId());
            // 重试也重新签发断言，避免 Flow 的 jti 防重放规则拒绝第二次请求。
            body.putObject("subject").put("type", "SIGNED_JWT")
                    .put("assertion", assertions.sign(host.externalSubject()));
            body.set("entry", entry);
            body.set("context", mapper.valueToTree(host.context()));
            body.putObject("ui").put("locale", "zh-CN").put("theme", "light")
                    .put("formPresentation", request.formPresentation() == null ? "seamless" : request.formPresentation());
            var reply = http.post("/api/open/v1/embed-launches", "Bearer " + token,
                    "application/json", body.toString(), traceId);
            if (reply.status() == 401) {
                tokens.invalidate(token);
                if (attempt == 0) {
                    continue;
                }
            }
            if (reply.status() != 201) {
                throw reply.failure(traceId);
            }
            return validateLaunch(reply.body(), traceId);
        }
        throw new EmbedDemoException(502, "FLOW_AUTHENTICATION_FAILED", traceId, null);
    }

    /** LIST/CREATE 不接受 recordId（含显式 null）；VIEW 同时要求宿主业务授权和 Flow 后续权限校验。 */
    private ObjectNode authorizeEntry(JsonNode entry, String traceId) {
        if (entry == null || !entry.isObject() || !entry.path("mode").isTextual()) {
            throw EmbedDemoException.badRequest();
        }
        String mode = entry.path("mode").asText();
        if (!properties.flow().allowedEntryModes().contains(mode)) {
            throw EmbedDemoException.badRequest();
        }
        Set<String> allowedFields = "VIEW".equals(mode) ? Set.of("mode", "recordId") : Set.of("mode");
        if (entry.properties().stream().anyMatch(field -> !allowedFields.contains(field.getKey()))) {
            throw EmbedDemoException.badRequest();
        }
        ObjectNode authorized = mapper.createObjectNode().put("mode", mode);
        if ("VIEW".equals(mode)) {
            JsonNode recordId = entry.path("recordId");
            if (!recordId.isTextual() || recordId.asText().isBlank() || recordId.asText().length() > 64) {
                throw EmbedDemoException.badRequest();
            }
            // 示例白名单属于当前唯一宿主用户；真实系统应替换为按用户/租户查询的业务授权服务。
            if (!properties.host().allowedRecordIds().contains(recordId.asText())) {
                throw new EmbedDemoException(403, "HOST_RECORD_NOT_ALLOWED", traceId, null);
            }
            authorized.put("recordId", recordId.asText());
        }
        return authorized;
    }

    /** 校验返回目标和专用 Embed Origin，防止错误环境/配置把一次性秘密交给其他站点。 */
    private Launch validateLaunch(JsonNode envelope, String traceId) {
        JsonNode data = envelope.path("data");
        try {
            Launch launch = mapper.treeToValue(data, Launch.class);
            String expectedUrl = properties.flow().embedOrigin() + "/embed/v1/launches/" + launch.launchId();
            if (envelope.path("code").asInt() != 201 || launch.launchId() == null
                    || !launch.launchId().matches("lch_[A-Za-z0-9_-]{16,60}")
                    || launch.launchCode() == null || !launch.launchCode().matches("[A-Za-z0-9_-]{43,128}")
                    || launch.view() == null || !properties.flow().viewKey().equals(launch.view().key())
                    || !properties.flow().surfaceType().equals(launch.view().surfaceType())
                    || !expectedUrl.equals(launch.embedUrl())
                    || !"https".equals(URI.create(launch.embedUrl()).getScheme())
                    || !"flow-embed/1".equals(launch.protocolVersion())
                    || launch.expiresAt() == null || !launch.expiresAt().isAfter(clock.instant())) {
                throw new IllegalArgumentException("Invalid launch");
            }
            return launch;
        } catch (Exception exception) {
            throw new EmbedDemoException(502, "FLOW_LAUNCH_RESPONSE_INVALID", traceId, null);
        }
    }

    /** 一次性 Launch 只返回当前浏览器，不持久化；toString 脱敏防止意外日志泄露。 */
    public record Launch(String launchId, String embedUrl, String launchCode, Instant expiresAt,
            View view, String protocolVersion) {
        @Override
        public String toString() {
            return "Launch[launchId=" + launchId + ", launchCode=<redacted>]";
        }
    }

    public record View(String key, String surfaceType) { }
}
