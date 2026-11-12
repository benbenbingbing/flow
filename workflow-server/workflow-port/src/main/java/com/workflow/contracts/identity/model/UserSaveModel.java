package com.workflow.contracts.identity.model;

import lombok.Builder;
import java.util.List;

/** biz 主动提交的用户快照；只更新携带的扩展组，其余组保留。
 * @param rawDataJson 可选单条来源 JSON 对象原文，在字段映射前取得；独立留存，不参与业务版本哈希，调用方须剔除凭据
 */
@Builder
public record UserSaveModel(ExternalIdentityKey key, Long sourceVersion, UserBaseData base, List<UserExtension> extensions, String rawDataJson) {
    /** 兼容已有调用；省略原文时按来源 raw-data-required 配置校验。 */
    public UserSaveModel(ExternalIdentityKey key, Long sourceVersion, UserBaseData base, List<UserExtension> extensions) {
        this(key, sourceVersion, base, extensions, null);
    }
}
