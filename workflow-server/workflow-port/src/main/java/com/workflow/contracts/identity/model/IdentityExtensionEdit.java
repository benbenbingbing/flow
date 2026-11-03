package com.workflow.contracts.identity.model;

import lombok.Builder;
import java.util.Map;

/** 本地扩展编辑请求；新建使用版本 0，已有资料必须提交详情返回的版本。 */
@Builder
public record IdentityExtensionEdit(Long expectedVersion, Map<String, Object> data) {
}
