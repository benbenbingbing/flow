package com.workflow.contracts.identity.model;

import lombok.Builder;
import java.util.List;

/** 按权限过滤后的扩展表单描述；writable 还需结合目标对象授权。 */
@Builder
public record IdentityExtensionSchema(String code, String title, ExtensionMaintenance maintenance, boolean writable,
        List<IdentityExtensionFieldView> fields) {
}
