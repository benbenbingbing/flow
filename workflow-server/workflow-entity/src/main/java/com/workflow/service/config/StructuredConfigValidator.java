package com.workflow.service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.json.JsonDocumentCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 结构化配置校验器
 * 
 * 基于 {@link JsonDocumentCodec} 对表单/列表等模块中存储的 JSON 配置进行解析与校验，
 * 统一限制 JSON 最大长度（64KB）与最大嵌套深度（8 层），防止超大或过深配置。
 */
@Component
public class StructuredConfigValidator {

    /** JSON 文本最大长度 */
    private static final int MAX_JSON_LENGTH = 65_535;
    /** JSON 最大嵌套深度 */
    private static final int MAX_DEPTH = 8;

    private final JsonDocumentCodec codec;

    /**
     * 注入 JsonDocumentCodec 的构造器。
     *
     * @param codec JSON 文档编解码器
     */
    @Autowired
    public StructuredConfigValidator(JsonDocumentCodec codec) {
        this.codec = codec;
    }

    /**
     * 测试用构造器：内部创建默认的 JsonDocumentCodec。
     *
     * @param objectMapper Jackson ObjectMapper
     */
    public StructuredConfigValidator(ObjectMapper objectMapper) {
        this(new JsonDocumentCodec(objectMapper, MAX_JSON_LENGTH, MAX_DEPTH));
    }

    /**
     * 将 JSON 文本解析为对象 Map，空白返回空 Map。
     *
     * @param json  JSON 文本
     * @param label 配置项名称，用于错误提示
     * @return 解析后的对象 Map
     */
    public Map<String, Object> parseObject(String json, String label) {
        return StringUtils.hasText(json)
                ? codec.readObject(json, label)
                : Map.of();
    }

    /**
     * 将 JSON 文本解析为任意结构对象，空白返回 null。
     *
     * @param json  JSON 文本
     * @param label 配置项名称，用于错误提示
     * @return 解析后的对象
     */
    public Object parseJson(String json, String label) {
        return StringUtils.hasText(json)
                ? codec.read(json, label)
                : null;
    }
}
