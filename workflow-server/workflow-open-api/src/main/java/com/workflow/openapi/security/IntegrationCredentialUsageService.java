package com.workflow.openapi.security;

import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationCredentialMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "workflow.open-api.enabled",
        havingValue = "true")
public class IntegrationCredentialUsageService {

    private final IntegrationCredentialMapper credentialMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccessfulUse(String clientId) {
        credentialMapper.markActiveUsedByClientId(
                clientId,
                LocalDateTime.now(ZoneOffset.UTC));
    }
}
