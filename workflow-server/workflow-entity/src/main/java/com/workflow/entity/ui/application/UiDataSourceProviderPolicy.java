package com.workflow.entity.ui.application;

import java.util.Map;
import org.springframework.util.StringUtils;

final class UiDataSourceProviderPolicy {

    private UiDataSourceProviderPolicy() {
    }

    static void validate(
            String sourceType,
            String providerCode,
            Map<String, Object> configuration) {
        if ("REGISTERED_PROVIDER".equals(sourceType)
                && !StringUtils.hasText(providerCode)) {
            throw new IllegalArgumentException(
                    "Provider 编码不能为空");
        }
    }
}
