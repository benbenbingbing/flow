package com.workflow.openapi.application.security;

import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationCredentialMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 负责集成凭据使用场景的业务处理；协调校验、状态变化及后续结果传递。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "workflow.open-api.enabled",
        havingValue = "true")
public class IntegrationCredentialUsageService {

    private final IntegrationCredentialMapper credentialMapper;

    /**
     * 记录成功{@code use}；供后续追溯或审计使用。
     *
     * @param clientId 客户端ID，后续用于记录成功{@code use}时定位或关联目标
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccessfulUse(String clientId) {
        credentialMapper.markActiveUsedByClientId(
                clientId,
                LocalDateTime.now(ZoneOffset.UTC));
    }
}
