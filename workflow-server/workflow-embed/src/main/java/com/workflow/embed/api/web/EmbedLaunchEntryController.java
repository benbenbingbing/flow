package com.workflow.embed.api.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.embed.application.launch.EmbedLaunchEntryService;
import com.workflow.embed.config.EmbedProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 返回带精确 frame-ancestors 和非秘密启动 meta 的动态 iframe Entry。 */
@RestController
@ConditionalOnProperty(prefix = "workflow.embed", name = "enabled", havingValue = "true")
public class EmbedLaunchEntryController {

    private static final String COMMON_CSP =
            "default-src 'none'; script-src 'self'; script-src-elem 'self'; "
                    + "script-src-attr 'none'; style-src 'self'; "
                    + "style-src-elem 'self'; style-src-attr 'unsafe-inline'; "
                    + "img-src 'self' data: blob: https:; "
                    + "font-src 'self' data:; connect-src 'self'; "
                    + "media-src 'self' blob:; worker-src 'self' blob:; "
                    + "object-src 'none'; base-uri 'none'; form-action 'none'; ";

    private final EmbedLaunchEntryService service;
    private final EmbedProperties properties;
    private final ObjectMapper objectMapper;

    public EmbedLaunchEntryController(
            EmbedLaunchEntryService service,
            EmbedProperties properties,
            ObjectMapper objectMapper) {
        this.service = service;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @GetMapping(
            value = "/embed/v1/launches/{launchId}",
            produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> entry(@PathVariable String launchId) {
        return service.resolve(launchId)
                .map(this::success)
                .orElseGet(this::notFound);
    }

    private ResponseEntity<String> success(
            EmbedLaunchEntryService.EntryMetadata metadata) {
        String encoded = encode(Map.of(
                "launchId", metadata.launchId(),
                "expectedParentOrigin", metadata.expectedParentOrigin(),
                "channelId", metadata.channelId(),
                "protocolVersion", metadata.protocolVersion()));
        String assetPath = HtmlUtils.htmlEscape(
                properties.getEntryAssetPath(), StandardCharsets.UTF_8.name());
        String stylePath = HtmlUtils.htmlEscape(
                properties.getEntryStylePath(), StandardCharsets.UTF_8.name());
        String html = """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width,initial-scale=1">
                  <meta name="flow-embed-entry" content="%s">
                  <title>Flow Embed</title>
                  <link rel="stylesheet" href="%s">
                  <script type="module" src="%s"></script>
                </head>
                <body><div id="app"></div></body>
                </html>
                """.formatted(encoded, stylePath, assetPath);
        return headers(ResponseEntity.ok(),
                COMMON_CSP + "frame-ancestors "
                        + metadata.expectedParentOrigin())
                .body(html);
    }

    private ResponseEntity<String> notFound() {
        String html = """
                <!doctype html><html lang="zh-CN"><head><meta charset="UTF-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Flow Embed unavailable</title></head>
                <body><main>Embed 页面不可用，请从宿主系统重新打开。</main></body></html>
                """;
        return headers(ResponseEntity.status(404),
                COMMON_CSP + "frame-ancestors 'none'")
                .body(html);
    }

    private ResponseEntity.BodyBuilder headers(
            ResponseEntity.BodyBuilder builder,
            String csp) {
        return builder
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noStore())
                .header("Content-Security-Policy", csp)
                .header("Referrer-Policy", "no-referrer")
                .header("X-Content-Type-Options", "nosniff")
                .header("Permissions-Policy", "camera=(), microphone=(), geolocation=()")
                .header(HttpHeaders.PRAGMA, "no-cache");
    }

    private String encode(Object value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    objectMapper.writeValueAsBytes(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize Embed entry metadata", exception);
        }
    }
}
