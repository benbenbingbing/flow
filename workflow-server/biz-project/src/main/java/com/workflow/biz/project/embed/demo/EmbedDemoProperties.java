package com.workflow.biz.project.embed.demo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;

/** 所有授权坐标由宿主后端固定配置；浏览器不能覆盖目标、身份、Origin 或 Context。 */
@Validated
@ConfigurationProperties("partner.embed-demo")
@Profile("partner-embed-demo")
public record EmbedDemoProperties(@NotNull @Valid Flow flow, @NotNull @Valid Host host) {
    /**
     * 由后端控制的接入坐标。
     *
     * @param apiBaseUrl 机器接口根地址，用于获取 Token 和签发 Launch，不由浏览器传入
     * @param clientId Flow Application 的机器身份，不用于人员身份映射
     * @param clientSecret 机器身份凭据，仅用于后端 OAuth Basic 认证
     * @param embedOrigin 固定的 iframe 运行域，用于严格校验 Flow 返回的 embedUrl
     * @param parentOrigin 实际宿主 Origin，须与 Flow Grant 和浏览器宿主页一致
     * @param viewKey 已授权的稳定目标键，替代浏览器直接提交实体、表单或列表坐标
     * @param surfaceType 预期返回的 LIST/FORM，用于防止误接其他目标
     * @param allowedEntryModes 宿主允许的直接入口，仍须受 Flow View/Grant 和用户权限约束
     * @param assertionIssuer 与 Flow Provider 精确匹配的宿主断言签发者
     * @param assertionKeyId 标识当前签名公钥，供 Flow 在 JWKS 中选取验证密钥
     * @param privateKey 宿主 PKCS#8 RSA 私钥路径，不能将内容交给 Flow 或浏览器
     */
    public record Flow(
            @NotNull URI apiBaseUrl,
            @NotBlank String clientId,
            @NotBlank String clientSecret,
            @NotNull URI embedOrigin,
            @NotNull URI parentOrigin,
            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9._-]{0,99}") String viewKey,
            @NotBlank @Pattern(regexp = "LIST|FORM") String surfaceType,
            @NotEmpty Set<@Pattern(regexp = "LIST|CREATE|VIEW") String> allowedEntryModes,
            @NotBlank @Size(max = 255) String assertionIssuer,
            @NotBlank @Size(max = 128) String assertionKeyId,
            @NotNull Path privateKey) {

        public Flow {
            requireOrigin(apiBaseUrl, true);
            requireOrigin(embedOrigin, false);
            requireOrigin(parentOrigin, false);
            if (embedOrigin.equals(parentOrigin)) {
                throw new IllegalArgumentException("Embed Origin must differ from parent Origin");
            }
        }

        @Override
        public String toString() {
            return "Flow[viewKey=" + viewKey + ", credentials=<redacted>]";
        }
    }

    /** externalSubject 是不可回收的人员标识，后续由 Flow Provider + Binding 精确映射到用户。 */
    public record Host(
            @NotBlank String username,
            @NotBlank String password,
            @NotBlank @Size(max = 128) String externalSubject,
            Set<@NotBlank @Size(max = 64) String> allowedRecordIds,
            @Size(max = 32) Map<String, String> context) {
        public Host {
            allowedRecordIds = allowedRecordIds == null ? Set.of() : Set.copyOf(allowedRecordIds);
            context = context == null ? Map.of() : Map.copyOf(context);
        }

        @Override
        public String toString() {
            return "Host[identity=<redacted>]";
        }
    }

    /** 固定 API 根地址及两个 Origin，禁止路径、用户信息和重定向引入另一套信任边界。 */
    private static void requireOrigin(URI uri, boolean allowLoopbackHttp) {
        boolean loopback = uri != null && Set.of("localhost", "127.0.0.1", "[::1]")
                .contains(uri.getHost() == null ? "" : uri.getHost());
        if (uri == null || uri.getHost() == null || uri.getRawUserInfo() != null
                || !uri.getRawPath().isEmpty() || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !("https".equals(uri.getScheme())
                    || (allowLoopbackHttp && loopback && "http".equals(uri.getScheme())))) {
            throw new IllegalArgumentException("Use an exact HTTPS Origin without path; API permits loopback HTTP");
        }
    }
}
