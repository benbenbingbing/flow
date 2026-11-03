package com.workflow.contracts.identity.model;

import lombok.Builder;
import java.util.List;

/** biz 主动提交的用户快照；只更新携带的扩展组，其余组保留。 */
@Builder
public record UserSaveModel(ExternalIdentityKey key, Long sourceVersion, UserBaseData base, List<UserExtension> extensions) {
}
