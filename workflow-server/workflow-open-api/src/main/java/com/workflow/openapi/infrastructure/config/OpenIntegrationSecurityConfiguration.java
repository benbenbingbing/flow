package com.workflow.openapi.infrastructure.config;

import com.workflow.openapi.application.security.IntegrationCredentialUsageService;
import com.workflow.openapi.application.security.IntegrationRateLimitService;
import com.workflow.openapi.application.security.OpenApiConcurrencyLeaseService;
import com.workflow.openapi.infrastructure.security.OpenApiApplicationPolicyFilter;
import com.workflow.openapi.infrastructure.security.OpenApiSecurityResponseWriter;
import com.workflow.openapi.infrastructure.security.OpenIntegrationClientAddressResolver;
import com.workflow.openapi.infrastructure.security.OpenIntegrationKeyMaterial;
import com.workflow.openapi.infrastructure.security.StatelessClientCredentialsAuthorizationService;
import com.workflow.openapi.infrastructure.security.TokenEndpointRateLimitFilter;
import com.workflow.openapi.security.IntegrationClientNetworkPolicy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.audit.port.SystemAuditPort;
import com.workflow.openapi.application.IntegrationSecretHasher;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationApplicationMapper;
import com.workflow.openapi.infrastructure.web.OpenApiRequestGuardFilter;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.ClientSecretAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * 封装打开集成安全配置相关能力和状态；供同一业务流程的后续处理使用。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OpenIntegrationProperties.class)
public class OpenIntegrationSecurityConfiguration {

    /**
     * 处理已有应用安全，并将结果传给后续步骤。
     *
     * @param http HTTP，供本方法处理已有应用安全时使用
     * @return 处理后的已有应用安全结果，供调用方继续处理
     * @throws Exception 下游操作失败时向调用方传递
     */
    @Bean
    @Order(1000)
    @ConditionalOnWebApplication(
            type = ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain existingApplicationSecurity(HttpSecurity http)
            throws Exception {
        http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().permitAll());
        return http.build();
    }

    /**
     * 封装启用打开集成安全相关能力和状态；供同一业务流程的后续处理使用。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(
            type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(
            name = "workflow.open-api.enabled",
            havingValue = "true")
    static class EnabledOpenIntegrationSecurity {

        /**
         * 处理打开集成键材料，并将结果传给后续步骤。
         *
         * @param properties 属性集合，作为 {@code OpenIntegrationKeyMaterial} 的输入影响后续处理
         * @param resourceLoader 资源{@code loader}，作为 {@code OpenIntegrationKeyMaterial} 的输入影响后续处理
         * @return 处理后的打开集成键材料结果，供调用方继续处理
         */
        @Bean
        OpenIntegrationKeyMaterial openIntegrationKeyMaterial(
                OpenIntegrationProperties properties,
                ResourceLoader resourceLoader) {
            return new OpenIntegrationKeyMaterial(
                    properties,
                    resourceLoader);
        }

        /**
         * 处理打开集成{@code jwk}来源，并将结果传给后续步骤。
         *
         * @param properties 属性集合，供本方法处理打开集成{@code jwk}来源时使用
         * @param keys 键集合，作为 {@code RSAKey.Builder} 的输入影响后续处理
         * @return 处理后的打开集成{@code jwk}来源结果，供调用方继续处理
         */
        @Bean
        JWKSource<SecurityContext> openIntegrationJwkSource(
                OpenIntegrationProperties properties,
                OpenIntegrationKeyMaterial keys) {
            RSAKey rsaKey = new RSAKey.Builder(keys.publicKey())
                    .privateKey(keys.privateKey())
                    .keyID(properties.getKeyId())
                    .build();
            List<JWK> jwks = new ArrayList<>();
            jwks.add(rsaKey);
            keys.verificationKeys().forEach((keyId, publicKey) -> {
                if (!keyId.equals(properties.getKeyId())) {
                    jwks.add(new RSAKey.Builder(publicKey)
                            .keyID(keyId)
                            .build());
                }
            });
            JWKSet jwkSet = new JWKSet(jwks);
            return (selector, context) -> selector.select(jwkSet);
        }

        /**
         * 处理授权{@code server}{@code settings}，并将结果传给后续步骤。
         *
         * @param properties 属性集合，供本方法处理授权{@code server}{@code settings}时使用
         * @return 处理后的授权{@code server}{@code settings}结果，供调用方继续处理
         */
        @Bean
        AuthorizationServerSettings authorizationServerSettings(
                OpenIntegrationProperties properties) {
            return AuthorizationServerSettings.builder()
                    .issuer(properties.getIssuer())
                    .tokenEndpoint("/oauth2/token")
                    .build();
        }

        /**
         * 处理{@code oauth2}授权服务，并将结果传给后续步骤。
         *
         * @return 处理后的{@code oauth2}授权服务结果，供调用方继续处理
         */
        @Bean
        OAuth2AuthorizationService oauth2AuthorizationService() {
            return new StatelessClientCredentialsAuthorizationService();
        }

        /**
         * 处理{@code machine}令牌声明集合，并将结果传给后续步骤。
         *
         * @param properties 属性集合，供本方法处理{@code machine}令牌声明集合时使用
         * @return 处理后的{@code machine}令牌声明集合结果，供调用方继续处理
         */
        @Bean
        OAuth2TokenCustomizer<JwtEncodingContext> machineTokenClaims(
                OpenIntegrationProperties properties) {
            return context -> {
                if (OAuth2TokenType.ACCESS_TOKEN.equals(
                        context.getTokenType())) {
                    context.getJwsHeader()
                            .algorithm(SignatureAlgorithm.RS256)
                            .keyId(properties.getKeyId());
                    context.getClaims()
                            .audience(List.of(properties.getAudience()))
                            .id(UUID.randomUUID().toString())
                            .claim(
                                    "application_id",
                                    context.getRegisteredClient().getId());
                }
            };
        }

        /**
         * 处理{@code machine}{@code jwt}{@code decoder}，并将结果传给后续步骤。
         *
         * @param properties 属性集合，作为 {@code JwtValidators.createDefaultWithIssuer} 的输入影响后续处理
         * @param openIntegrationJwkSource 打开集成{@code jwk}来源，作为 {@code processor.setJWSKeySelector} 的输入影响后续处理
         * @return 处理后的{@code machine}{@code jwt}{@code decoder}结果，供调用方继续处理
         */
        @Bean("machineJwtDecoder")
        JwtDecoder machineJwtDecoder(
                OpenIntegrationProperties properties,
                JWKSource<SecurityContext> openIntegrationJwkSource) {
            DefaultJWTProcessor<SecurityContext> processor =
                    new DefaultJWTProcessor<>();
            processor.setJWSTypeVerifier(
                    new DefaultJOSEObjectTypeVerifier<>(
                            JOSEObjectType.JWT,
                            new JOSEObjectType("at+jwt"),
                            null));
            processor.setJWSKeySelector(
                    new JWSVerificationKeySelector<>(
                            JWSAlgorithm.RS256,
                            openIntegrationJwkSource));
            NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
            OAuth2TokenValidator<Jwt> issuer =
                    JwtValidators.createDefaultWithIssuer(
                            properties.getIssuer());
            OAuth2TokenValidator<Jwt> audience =
                    new JwtClaimValidator<List<String>>(
                            "aud",
                            values -> values != null
                                    && values.contains(
                                    properties.getAudience()));
            decoder.setJwtValidator(
                    new DelegatingOAuth2TokenValidator<>(
                            issuer,
                            audience));
            return decoder;
        }

        /**
         * 处理授权{@code server}安全，并将结果传给后续步骤。
         *
         * @param http HTTP，供本方法处理授权{@code server}安全时使用
         * @param rateLimitService 频率上限服务，供本方法处理授权{@code server}安全时使用
         * @param properties 属性集合，供本方法处理授权{@code server}安全时使用
         * @param objectMapper 对象映射器，供本方法处理授权{@code server}安全时使用
         * @param secretHasher 密钥{@code hasher}，作为 {@code setPasswordEncoder} 的输入影响后续处理
         * @param networkPolicy {@code network}策略，供本方法处理授权{@code server}安全时使用
         * @param addressResolver 地址解析器，供本方法处理授权{@code server}安全时使用
         * @param auditPort 审计端口，供本方法处理授权{@code server}安全时使用
         * @param credentialUsageService 凭据使用场景服务，供本方法处理授权{@code server}安全时使用
         * @return 处理后的授权{@code server}安全结果，供调用方继续处理
         * @throws Exception 下游操作失败时向调用方传递
         */
        @Bean
        @Order(1)
        SecurityFilterChain authorizationServerSecurity(
                HttpSecurity http,
                IntegrationRateLimitService rateLimitService,
                OpenIntegrationProperties properties,
                ObjectMapper objectMapper,
                IntegrationSecretHasher secretHasher,
                IntegrationClientNetworkPolicy networkPolicy,
                OpenIntegrationClientAddressResolver addressResolver,
                SystemAuditPort auditPort,
                IntegrationCredentialUsageService credentialUsageService)
                throws Exception {
            PathPatternRequestMatcher tokenEndpoint =
                    PathPatternRequestMatcher.withDefaults()
                            .matcher(
                                    HttpMethod.POST,
                                    "/oauth2/token");
            OAuth2AuthorizationServerConfigurer authorizationServer =
                    OAuth2AuthorizationServerConfigurer
                            .authorizationServer();
            http
                    .securityMatcher(tokenEndpoint)
                    .with(
                            authorizationServer,
                            server -> server.clientAuthentication(
                                    client -> client
                                            .authenticationProviders(
                                                    providers -> providers
                                                            .forEach(provider -> {
                                                                if (provider
                                                                        instanceof ClientSecretAuthenticationProvider
                                                                        secretProvider) {
                                                                    secretProvider
                                                                            .setPasswordEncoder(
                                                                                    secretHasher);
                                                                }
                                                            }))
                                            .errorResponseHandler(
                                                    (request, response,
                                                            exception) -> {
                                                response.setHeader(
                                                        "WWW-Authenticate",
                                                        "Basic realm=\"oauth2/client\", "
                                                                + "error=\"invalid_client\"");
                                                response.setStatus(401);
                                                response.setContentType(
                                                        "application/json;charset=UTF-8");
                                                response.setHeader(
                                                        "Cache-Control",
                                                        "no-store");
                                                response.setHeader(
                                                        "Pragma",
                                                        "no-cache");
                                                objectMapper.writeValue(
                                                        response.getOutputStream(),
                                                        java.util.Map.of(
                                                                "error",
                                                                "invalid_client"));
                                            })))
                    .csrf(csrf -> csrf.ignoringRequestMatchers(
                            tokenEndpoint))
                    .requestCache(cache -> cache.disable())
                    .sessionManagement(session ->
                            session.sessionCreationPolicy(
                                    SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(authorize -> authorize
                            .anyRequest().authenticated())
                    .addFilterBefore(
                            new TokenEndpointRateLimitFilter(
                                    rateLimitService,
                                    properties,
                                    objectMapper,
                                    networkPolicy,
                                    addressResolver,
                                    auditPort,
                                    credentialUsageService),
                            BasicAuthenticationFilter.class);
            return http.build();
        }

        /**
         * 处理打开API资源安全，并将结果传给后续步骤。
         *
         * @param http HTTP，供本方法处理打开API资源安全时使用
         * @param machineJwtDecoder {@code machine}{@code jwt}{@code decoder}，作为 {@code decoder} 的输入影响后续处理
         * @param objectMapper 对象映射器，作为 {@code OpenApiSecurityResponseWriter} 的输入影响后续处理
         * @param applicationMapper 应用映射器，作为 {@code addFilterAfter} 的输入影响后续处理
         * @param networkPolicy {@code network}策略，供本方法处理打开API资源安全时使用
         * @param addressResolver 地址解析器，供本方法处理打开API资源安全时使用
         * @param rateLimitService 频率上限服务，供本方法处理打开API资源安全时使用
         * @param concurrencyService {@code concurrency}服务，供本方法处理打开API资源安全时使用
         * @param auditPort 审计端口，供本方法处理打开API资源安全时使用
         * @return 处理后的打开API资源安全结果，供调用方继续处理
         * @throws Exception 下游操作失败时向调用方传递
         */
        @Bean
        @Order(2)
        SecurityFilterChain openApiResourceSecurity(
                HttpSecurity http,
                @Qualifier("machineJwtDecoder")
                JwtDecoder machineJwtDecoder,
                ObjectMapper objectMapper,
                IntegrationApplicationMapper applicationMapper,
                IntegrationClientNetworkPolicy networkPolicy,
                OpenIntegrationClientAddressResolver addressResolver,
                IntegrationRateLimitService rateLimitService,
                OpenApiConcurrencyLeaseService concurrencyService,
                SystemAuditPort auditPort)
                throws Exception {
            OpenApiSecurityResponseWriter responseWriter =
                    new OpenApiSecurityResponseWriter(objectMapper);
            http
                    .securityMatcher("/api/open/**")
                    .csrf(csrf -> csrf.ignoringRequestMatchers(
                            "/api/open/**"))
                    .requestCache(cache -> cache.disable())
                    .sessionManagement(session ->
                            session.sessionCreationPolicy(
                                    SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers(
                                    HttpMethod.POST,
                                    "/api/open/v1/embed-launches")
                            .authenticated()
                            .anyRequest().denyAll())
                    .oauth2ResourceServer(resource -> resource
                            .jwt(jwt -> jwt.decoder(machineJwtDecoder))
                            .authenticationEntryPoint(
                                    (request, response, exception) -> {
                                        response.setHeader(
                                                HttpHeaders.WWW_AUTHENTICATE,
                                                "Bearer error=\"invalid_token\"");
                                        responseWriter.write(
                                                request,
                                                response,
                                                401,
                                                "INVALID_ACCESS_TOKEN",
                                                "Access token is invalid",
                                                null);
                                    })
                            .accessDeniedHandler(
                                    (request, response, exception) ->
                                            responseWriter.write(
                                                    request,
                                                    response,
                                                    403,
                                                    "ACCESS_DENIED",
                                                    "Access is denied",
                                                    null)))
                    .addFilterBefore(
                            new OpenApiRequestGuardFilter(
                                    objectMapper),
                            BearerTokenAuthenticationFilter.class)
                    .addFilterAfter(
                            new OpenApiApplicationPolicyFilter(
                                    applicationMapper,
                                    networkPolicy,
                                    addressResolver,
                                    rateLimitService,
                                    concurrencyService,
                                    responseWriter,
                                    auditPort),
                            BearerTokenAuthenticationFilter.class);
            return http.build();
        }
    }
}
