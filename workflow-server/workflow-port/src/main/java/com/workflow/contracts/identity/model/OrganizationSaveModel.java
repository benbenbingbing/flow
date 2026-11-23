package com.workflow.contracts.identity.model;

import lombok.Builder;
import java.util.List;

/** biz 主动提交的组织快照；不包含负责人任职和产品权限。
 * @param rawDataJson 可选单条来源 JSON 对象原文，在字段映射前取得；独立留存，不参与业务版本哈希，调用方须剔除凭据
 * @param mainFields 可选主表文本槽位；省略保留，携带时完整替换，参与来源业务版本校验
 */
@Builder
public record OrganizationSaveModel(ExternalIdentityKey key, Long sourceVersion, OrganizationBaseData base, List<OrganizationExtension> extensions, String rawDataJson, IdentityMainFields mainFields) {
    /**
     * 直接提交五个主表字段，供后续按来源文本查询和关联组织。
     * 五列按本次快照整体替换，单项 null 清空对应列。无需留存来源原文时 rawDataJson 可传 null。
     */
    public OrganizationSaveModel(ExternalIdentityKey key, Long sourceVersion, OrganizationBaseData base,
            List<OrganizationExtension> extensions, String rawDataJson,
            String ext1, String ext2, String ext3, String ext4, String ext5) {
        this(key, sourceVersion, base, extensions, rawDataJson, new IdentityMainFields(ext1, ext2, ext3, ext4, ext5));
    }

    /** 兼容已接入原文留存的调用；不携带主表扩展列，保留原有值和摘要规则。 */
    public OrganizationSaveModel(ExternalIdentityKey key, Long sourceVersion, OrganizationBaseData base, List<OrganizationExtension> extensions, String rawDataJson) {
        this(key, sourceVersion, base, extensions, rawDataJson, null);
    }
    /** 兼容已有调用；省略原文时按来源 raw-data-required 配置校验。 */
    public OrganizationSaveModel(ExternalIdentityKey key, Long sourceVersion, OrganizationBaseData base, List<OrganizationExtension> extensions) {
        this(key, sourceVersion, base, extensions, null);
    }
}
