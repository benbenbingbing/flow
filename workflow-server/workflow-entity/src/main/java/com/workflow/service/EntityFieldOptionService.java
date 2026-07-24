package com.workflow.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.workflow.common.json.JsonDocumentCodec;
import com.workflow.entity.EntityFieldOption;
import com.workflow.mapper.EntityFieldOptionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 实体字段选项服务，负责字段下拉选项的持久化、文档序列化与解析。
 *
 * <p>选项以 value/label 为核心字段，可携带样式类型、禁用状态等附加属性，
 * 序列化为 JSON 文档便于发布快照与表单运行时加载。</p>
 */
@Service
@RequiredArgsConstructor
public class EntityFieldOptionService {

    private static final TypeReference<List<Map<String, Object>>> OPTIONS_TYPE =
            new TypeReference<>() {};

    private final EntityFieldOptionMapper optionMapper;
    private final JsonDocumentCodec codec;

    /**
     * 查询字段的所有选项，返回 Map 列表。
     *
     * @param fieldId 字段ID
     * @return 选项 Map 列表，fieldId 为空返回空列表
     */
    public List<Map<String, Object>> findOptions(String fieldId) {
        if (!StringUtils.hasText(fieldId)) {
            return List.of();
        }
        return optionMapper.findByFieldId(fieldId).stream()
                .map(this::toMap)
                .toList();
    }

    /**
     * 将字段选项序列化为 JSON 文档，无选项返回 null。
     *
     * @param fieldId 字段ID
     * @return 选项 JSON 文档
     */
    public String toDocument(String fieldId) {
        List<Map<String, Object>> options = findOptions(fieldId);
        return options.isEmpty() ? null : codec.write(options, "实体字段选项");
    }

    /**
     * 解析选项 JSON 文档为 Map 列表，兼容转义引号场景。
     *
     * @param document 选项 JSON 文档
     * @return 选项 Map 列表，文档为空返回空列表
     */
    public List<Map<String, Object>> parseDocument(String document) {
        if (!StringUtils.hasText(document)) {
            return List.of();
        }
        String normalized = document;
        try {
            return codec.read(normalized, OPTIONS_TYPE, "实体字段选项");
        } catch (IllegalArgumentException first) {
            normalized = document.replace("\\\"", "\"");
            return codec.read(normalized, OPTIONS_TYPE, "实体字段选项");
        }
    }

    /**
     * 全量替换字段选项（先删除再插入），忽略 value 为空的项。
     *
     * @param fieldId 字段ID
     * @param options 选项 Map 列表
     */
    @Transactional(rollbackFor = Exception.class)
    public void replace(String fieldId, List<Map<String, Object>> options) {
        optionMapper.deleteByFieldId(fieldId);
        int sort = 0;
        for (Map<String, Object> source : options == null
                ? List.<Map<String, Object>>of()
                : options) {
            String value = text(source.get("value"));
            if (!StringUtils.hasText(value)) {
                continue;
            }
            EntityFieldOption option = new EntityFieldOption();
            option.setFieldId(fieldId);
            option.setOptionValue(value);
            option.setOptionLabel(StringUtils.hasText(text(source.get("label")))
                    ? text(source.get("label"))
                    : value);
            option.setStyleType(text(source.get("type")));
            option.setDisabled(Boolean.TRUE.equals(source.get("disabled")));
            option.setSortOrder(sort++);
            option.setOptionDocument(codec.write(source, "实体字段选项"));
            option.setCreatedAt(LocalDateTime.now());
            option.setUpdatedAt(LocalDateTime.now());
            optionMapper.insert(option);
        }
    }

    /**
     * 从选项 JSON 文档解析并全量替换字段选项。
     *
     * @param fieldId  字段ID
     * @param document 选项 JSON 文档
     */
    @Transactional(rollbackFor = Exception.class)
    public void replaceFromDocument(String fieldId, String document) {
        replace(fieldId, parseDocument(document));
    }

    /**
     * 删除字段的所有选项。
     *
     * @param fieldId 字段ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String fieldId) {
        optionMapper.deleteByFieldId(fieldId);
    }

    private Map<String, Object> toMap(EntityFieldOption option) {
        Map<String, Object> result = StringUtils.hasText(option.getOptionDocument())
                ? new LinkedHashMap<>(codec.readObject(
                        option.getOptionDocument(), "实体字段选项"))
                : new LinkedHashMap<>();
        result.put("value", option.getOptionValue());
        result.put("label", option.getOptionLabel());
        if (StringUtils.hasText(option.getStyleType())) {
            result.put("type", option.getStyleType());
        }
        if (Boolean.TRUE.equals(option.getDisabled())) {
            result.put("disabled", true);
        }
        return result;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }
}
