package com.workflow.admin.identity.provisioning.application;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.*;

/** 来源在部署时显式登记；没有配置的来源拒绝写入，不创建任何调度任务。 */
@Data
@Component
@ConfigurationProperties(prefix = "workflow.identity")
public class IdentityWriteProperties {
    private List<String> scanPackages = new ArrayList<>(List.of("com.workflow.biz"));
    private Map<String, Source> sources = new LinkedHashMap<>();

    @Data
    public static class Source {
        private String writePermission;
        /** 无请求上下文时使用显式配置的服务用户，仍检查账号有效性与权限。 */
        private String serviceUserId;
        private VersionMode versionMode = VersionMode.VERSIONED;
        private Set<String> extensions = new LinkedHashSet<>();
    }
    public enum VersionMode { VERSIONED, SERIAL }
}
