package com.workflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.json.JsonDocumentCodec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JsonDocumentConfiguration {

    @Bean
    public JsonDocumentCodec jsonDocumentCodec(ObjectMapper objectMapper) {
        return new JsonDocumentCodec(objectMapper);
    }
}
