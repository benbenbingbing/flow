package com.workflow.openapi.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.audit.SystemAuditPort;
import com.workflow.openapi.application.IntegrationSecretHasher;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationApplicationMapper;
import com.workflow.openapi.web.OpenApiRequestGuardFilter;
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

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OpenIntegrationProperties.class)
public class OpenIntegrationSecurityConfiguration {

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

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(
            type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(
            name = "workflow.open-api.enabled",
            havingValue = "true")
    static class EnabledOpenIntegrationSecurity {

        @Bean
        OpenIntegrationKeyMaterial openIntegrationKeyMaterial(
                OpenIntegrationProperties properties,
                ResourceLoader resourceLoader) {
            return new OpenIntegrationKeyMaterial(
                    properties,
                    resourceLoader);
        }

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

        @Bean
        AuthorizationServerSettings authorizationServerSettings(
                OpenIntegrationProperties properties) {
            return AuthorizationServerSettings.builder()
                    .issuer(properties.getIssuer())
                    .tokenEndpoint("/oauth2/token")
                    .build();
        }

        @Bean
        OAuth2AuthorizationService oauth2AuthorizationService() {
            return new StatelessClientCredentialsAuthorizationService();
        }

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
                                    HttpMethod.GET,
                                    "/api/open/v1/process-definitions")
                            .hasAuthority(
                                    "SCOPE_process.definition.read")
                            .requestMatchers(
                                    HttpMethod.POST,
                                    "/api/open/v1/process-instances")
                            .hasAuthority(
                                    "SCOPE_process.instance.start")
                            .requestMatchers(
                                    HttpMethod.GET,
                                    "/api/open/v1/process-instances/*/tasks")
                            .hasAuthority(
                                    "SCOPE_process.task.read")
                            .requestMatchers(
                                    HttpMethod.GET,
                                    "/api/open/v1/process-instances/*")
                            .hasAuthority(
                                    "SCOPE_process.instance.read")
                            .requestMatchers(
                                    HttpMethod.POST,
                                    "/api/open/v1/process-instances/*"
                                            + "/messages/*")
                            .hasAuthority(
                                    "SCOPE_process.message.correlate")
                            .anyRequest().authenticated())
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
                                                    "INSUFFICIENT_SCOPE",
                                                    "Required scope is not granted",
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
