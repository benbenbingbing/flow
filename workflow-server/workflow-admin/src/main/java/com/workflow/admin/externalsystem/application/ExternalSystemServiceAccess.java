package com.workflow.admin.externalsystem.application;

import com.workflow.core.error.ForbiddenException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/** 部署时为后台服务授予指定系统的配置读取权限。缺少配置时默认拒绝。 */
@Component
@ConfigurationProperties(prefix = "workflow.external-system")
public class ExternalSystemServiceAccess {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private Map<String, Grant> services = Map.of();

    public Map<String, Grant> getServices() {
        return services;
    }

    public void setServices(Map<String, Grant> services) {
        this.services = Map.copyOf(services);
    }

    /** 在读取参数前校验服务凭据和精确授权范围，避免调用方仅凭服务编码取得权限。 */
    public void require(String serviceCode, String credential, String systemCode) {
        Grant grant = serviceCode == null ? null : services.get(serviceCode);
        if (grant == null || systemCode == null || !grant.systemCodes().contains(systemCode)
                || credential == null || credential.isBlank() || credential.length() > 72
                || credential.getBytes(StandardCharsets.UTF_8).length > 72
                || grant.credentialHash() == null || !encoder.matches(credential, grant.credentialHash())) {
            throw new ForbiddenException("后台服务无权读取该外部系统配置");
        }
    }

    public record Grant(String credentialHash, Set<String> systemCodes) {
        public Grant {
            systemCodes = systemCodes == null ? Set.of() : Set.copyOf(systemCodes);
        }

        @Override
        public String toString() {
            return "Grant[systemCodes=" + systemCodes + "]";
        }
    }
}
