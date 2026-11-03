package com.workflow.contracts.identity.model;

import lombok.Builder;
import java.util.List;

/** biz 主动提交的组织快照；不包含负责人任职和产品权限。 */
@Builder
public record OrganizationSaveModel(ExternalIdentityKey key, Long sourceVersion, OrganizationBaseData base, List<OrganizationExtension> extensions) {
}
