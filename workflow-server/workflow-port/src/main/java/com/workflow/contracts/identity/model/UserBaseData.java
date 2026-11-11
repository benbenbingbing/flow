package com.workflow.contracts.identity.model;

import lombok.Builder;

/**
 * 来源用户的完整基础快照，空联系方式/归属表示清空。
 * account 原样用作 username，必填且最多 50 字符，全局唯一，创建后固定，供任务及跨环境配置引用。
 */
@Builder
public record UserBaseData(String account, String nickname, String email, String phone, String avatar,
        String organizationExternalId, String departmentExternalId, boolean enabled) {
}
