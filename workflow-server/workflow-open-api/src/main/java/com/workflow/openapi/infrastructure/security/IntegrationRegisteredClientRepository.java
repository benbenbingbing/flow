package com.workflow.openapi.infrastructure.security;

import com.workflow.openapi.infrastructure.config.OpenIntegrationProperties;

import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationApplicationMapper;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationCredentialMapper;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationApplicationCredentialRecord;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationApplicationRecord;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

/**
 * 封装集成{@code registered}客户端的数据访问；应用服务通过它读取或持久化业务状态。
 */
@Component
@ConditionalOnProperty(
        name = "workflow.open-api.enabled",
        havingValue = "true")
public class IntegrationRegisteredClientRepository
        implements RegisteredClientRepository {

    private final IntegrationApplicationMapper applicationMapper;
    private final IntegrationCredentialMapper credentialMapper;
    private final OpenIntegrationProperties properties;
    private final Clock clock;

    /**
     * 初始化集成{@code registered}客户端仓储，保存构造参数供后续方法使用。
     *
     * @param applicationMapper 应用映射器，保存在对象中供后续校验、查询或展示
     * @param credentialMapper 凭据映射器，保存在对象中供后续校验、查询或展示
     * @param properties 属性集合，保存在对象中供后续校验、查询或展示
     */
    @Autowired
    public IntegrationRegisteredClientRepository(
            IntegrationApplicationMapper applicationMapper,
            IntegrationCredentialMapper credentialMapper,
            OpenIntegrationProperties properties) {
        this(
                applicationMapper,
                credentialMapper,
                properties,
                Clock.systemUTC());
    }

    /**
     * 初始化集成{@code registered}客户端仓储，保存构造参数供后续方法使用。
     *
     * @param applicationMapper 应用映射器依赖，保存到当前对象供后续业务方法调用
     * @param credentialMapper 凭据映射器依赖，保存到当前对象供后续业务方法调用
     * @param properties 属性集合依赖，保存到当前对象供后续业务方法调用
     * @param clock 时钟依赖，保存到当前对象供后续业务方法调用
     */
    IntegrationRegisteredClientRepository(
            IntegrationApplicationMapper applicationMapper,
            IntegrationCredentialMapper credentialMapper,
            OpenIntegrationProperties properties,
            Clock clock) {
        this.applicationMapper = applicationMapper;
        this.credentialMapper = credentialMapper;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 保存集成{@code registered}客户端；后续读取或执行将使用更新后的状态。
     *
     * @param registeredClient {@code registered}客户端，供本方法保存集成{@code registered}客户端时使用
     * @throws UnsupportedOperationException 当前实现不支持指定操作时抛出
     */
    @Override
    public void save(RegisteredClient registeredClient) {
        throw new UnsupportedOperationException(
                "接入应用必须通过管理服务创建");
    }

    /**
     * 按ID查询{@code registered}客户端；结果供后续展示或处理。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 符合条件的{@code registered}客户端结果，供调用方继续处理
     */
    @Override
    public RegisteredClient findById(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return toRegisteredClient(applicationMapper.selectById(id));
    }

    /**
     * 按客户端ID查询{@code registered}客户端；结果供后续展示或处理。
     *
     * @param clientId 客户端ID，后续用于查询客户端ID时定位或关联目标
     * @return 符合条件的{@code registered}客户端结果，供调用方继续处理
     */
    @Override
    public RegisteredClient findByClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return null;
        }
        return toRegisteredClient(
                applicationMapper.findByClientId(clientId));
    }

    /**
     * 转换为{@code registered}客户端；输出作为后续校验或处理的输入。
     *
     * @param application 应用，作为 {@code credentialMapper.findActive} 的输入影响后续处理
     * @return 转换为后的{@code registered}客户端结果，供调用方继续处理
     */
    private RegisteredClient toRegisteredClient(
            IntegrationApplicationRecord application) {
        LocalDateTime now = LocalDateTime.ofInstant(
                clock.instant(),
                ZoneOffset.UTC);
        if (application == null
                || !"ACTIVE".equals(application.getStatus())
                || application.getExpiresAt() != null
                && !application.getExpiresAt().isAfter(now)) {
            return null;
        }
        IntegrationApplicationCredentialRecord credential =
                credentialMapper.findActive(application.getId());
        if (credential == null
                || credential.getExpiresAt() != null
                && !credential.getExpiresAt().isAfter(now)) {
            return null;
        }
        return RegisteredClient.withId(application.getId())
                .clientId(application.getClientId())
                .clientSecret(credential.getSecretHash())
                .clientAuthenticationMethod(
                        ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(
                        AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(
                                properties.getAccessTokenTtl())
                        .build())
                .build();
    }
}
