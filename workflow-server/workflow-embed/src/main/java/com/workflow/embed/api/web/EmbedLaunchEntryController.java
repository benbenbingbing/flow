package com.workflow.embed.api.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.embed.application.launch.EmbedLaunchEntryService;
import com.workflow.embed.infrastructure.config.EmbedProperties;
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

    /**
     * 初始化嵌入式启动记录入口控制器，保存构造参数供后续方法使用。
     *
     * @param service 服务依赖，保存到当前对象供后续业务方法调用
     * @param properties 属性集合依赖，保存到当前对象供后续业务方法调用
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     */
    public EmbedLaunchEntryController(
            EmbedLaunchEntryService service,
            EmbedProperties properties,
            ObjectMapper objectMapper) {
        this.service = service;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 处理入口，并将结果传给后续步骤。
     *
     * @param launchId 启动记录ID，后续用于处理入口时定位或关联目标
     * @return 处理后的入口结果，供调用方继续处理
     */
    @GetMapping(
            value = "/embed/v1/launches/{launchId}",
            produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> entry(@PathVariable String launchId) {
        return service.resolve(launchId)
                .map(this::success)
                .orElseGet(this::notFound);
    }

    /**
     * 处理成功，并将结果传给后续步骤。
     *
     * @param metadata 元数据，作为 {@code encode} 的输入影响后续处理
     * @return 处理后的成功结果，供调用方继续处理
     */
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

    /**
     * 构造目标不存在异常，供调用方终止后续处理。
     *
     * @return 处理后的非已找到结果，供调用方继续处理
     */
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

    /**
     * 处理{@code headers}，并将结果传给后续步骤。
     *
     * @param builder 构建器，供本方法处理{@code headers}时使用
     * @param csp {@code csp}，供本方法处理{@code headers}时使用
     * @return 处理后的{@code headers}结果，供调用方继续处理
     */
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

    /**
     * 编码嵌入式启动记录入口；输出作为后续校验或处理的输入。
     *
     * @param value 待编码嵌入式启动记录入口的原始输入，结果供调用方继续使用
     * @return 编码后的嵌入式启动记录入口文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private String encode(Object value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    objectMapper.writeValueAsBytes(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize Embed entry metadata", exception);
        }
    }
}
