package com.workflow.service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.json.JsonDocumentCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

@Component
public class StructuredConfigValidator {

    private static final int MAX_JSON_LENGTH = 65_535;
    private static final int MAX_DEPTH = 8;

    private final JsonDocumentCodec codec;

    @Autowired
    public StructuredConfigValidator(JsonDocumentCodec codec) {
        this.codec = codec;
    }

    public StructuredConfigValidator(ObjectMapper objectMapper) {
        this(new JsonDocumentCodec(objectMapper, MAX_JSON_LENGTH, MAX_DEPTH));
    }

    public Map<String, Object> parseObject(String json, String label) {
        return StringUtils.hasText(json)
                ? codec.readObject(json, label)
                : Map.of();
    }

    public Object parseJson(String json, String label) {
        return StringUtils.hasText(json)
                ? codec.read(json, label)
                : null;
    }
}
