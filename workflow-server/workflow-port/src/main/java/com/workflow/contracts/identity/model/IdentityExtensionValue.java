package com.workflow.contracts.identity.model;

import lombok.Builder;
import java.util.Map;

/** 单组扩展资料；版本用于防止多人编辑时静默覆盖。 */
@Builder
public record IdentityExtensionValue(long rowVersion, Map<String, Object> data) {
}
