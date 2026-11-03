package com.workflow.contracts.identity.model;

import lombok.Builder;

/** 来源用户的完整基础快照；空联系方式/归属表示清空，account 不改变流程内部身份。 */
@Builder
public record UserBaseData(String account, String nickname, String email, String phone, String avatar,
        String organizationExternalId, String departmentExternalId, boolean enabled) {
}
