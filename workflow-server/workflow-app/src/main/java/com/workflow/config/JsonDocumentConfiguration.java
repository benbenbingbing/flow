package com.workflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.json.JsonDocumentCodec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JSON 文档编解码配置类。
 * 
 * <p>负责将基于 Jackson 的 {@link JsonDocumentCodec} 注册为 Spring Bean，
 * 供各业务模块统一处理 JSON 文档的序列化与反序列化。
 */
@Configuration
public class JsonDocumentConfiguration {

    /**
     * 创建 JSON 文档编解码器 Bean。
     *
     * @param objectMapper Spring 容器中已配置的 Jackson ObjectMapper
     * @return 基于 ObjectMapper 的 JsonDocumentCodec 实例
     */
    @Bean
    public JsonDocumentCodec jsonDocumentCodec(ObjectMapper objectMapper) {
        return new JsonDocumentCodec(objectMapper);
    }
}
