package com.workflow.contracts.identity.model;

import lombok.Builder;
import java.util.List;

/** 前端安全字段描述，不包含数据库列名或 Java 类型名称。 */
@Builder
public record IdentityExtensionFieldView(String name, String label, ExtensionControl control, boolean required,
        int maxLength, List<String> options, boolean numberAsString) {
}
