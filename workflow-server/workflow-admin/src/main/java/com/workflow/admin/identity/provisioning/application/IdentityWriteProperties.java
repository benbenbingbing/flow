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
    /** 单条来源原文的 UTF-8 字节上限，在 JSON 解析和版本判断之前限制内存占用。 */
    private int rawDataMaxBytes = 262144;
    /** 一批来源原文的累计 UTF-8 字节上限，避免 100 条上限仍产生过大的事务。 */
    private int rawDataMaxBatchBytes = 8388608;

    @Data
    public static class Source {
        /** 默认校验来源写权限，可信来源可显式关闭，操作人有效性和扩展组各自的权限仍独立校验。 */
        private boolean permissionCheckEnabled = true;
        /** 来源写权限校验开启时必填，用于限制允许主动同步该来源的操作人。 */
        private String writePermission;
        /** 无请求上下文时用于账号有效性校验和审计归属，来源及扩展权限按各自规则检查。 */
        private String serviceUserId;
        /** 可选默认角色编码，仅用于该来源首次创建的用户，按现有启用角色解析并与用户同事务分配。 */
        private String defaultRoleCode;
        /** 接受的新增、更新和相同业务快照是否必须携带原文；旧版本忽略请求不受此要求限制。 */
        private boolean rawDataRequired;
        private VersionMode versionMode = VersionMode.VERSIONED;
        private Set<String> extensions = new LinkedHashSet<>();
    }
    public enum VersionMode { VERSIONED, SERIAL }
}
