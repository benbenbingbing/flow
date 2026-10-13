package com.workflow.entity.ui.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.ui.api.request.UiViewCompositionResolveRequest;
import java.util.Map;

/** 仅解码 GET/JSON 中的业务定位信息；所有值仍不可信，必须交给运行时授权服务核验。 */
public final class RuntimeRequestContext {
    private static final ObjectMapper JSON = new ObjectMapper();
    private RuntimeRequestContext() { }

    public static Map<String, Object> map(Object input) {
        if (input == null || input instanceof String value && value.isBlank()) return Map.of();
        try {
            if (input instanceof String value) return JSON.readValue(value, new TypeReference<>() { });
            return JSON.convertValue(input, new TypeReference<>() { });
        } catch (Exception exception) {
            throw new IllegalArgumentException("运行时业务上下文格式无效", exception);
        }
    }

    public static UiViewCompositionResolveRequest composition(Object input) {
        if (input == null || input instanceof String value && value.isBlank()) return null;
        return JSON.convertValue(map(input), UiViewCompositionResolveRequest.class);
    }
}
