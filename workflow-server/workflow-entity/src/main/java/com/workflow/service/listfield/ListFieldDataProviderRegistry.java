package com.workflow.service.listfield;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.ListFieldDataSourceOptionDTO;
import com.workflow.entity.EntityListField;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 列表字段数据提供者注册中心
 */
@Slf4j
@Component
public class ListFieldDataProviderRegistry {

    private static final Pattern PROVIDER_KEY = Pattern.compile("[A-Z][A-Z0-9_]{1,63}");

    private final Map<String, ListFieldDataProvider> providers = new LinkedHashMap<>();
    private final ObjectMapper objectMapper;

    public ListFieldDataProviderRegistry(
            List<ListFieldDataProvider> providerList,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        for (ListFieldDataProvider provider : providerList) {
            String type = normalize(provider.getDataSourceType());
            if (!PROVIDER_KEY.matcher(type).matches()) {
                throw new IllegalStateException("列表字段数据源编码不合法: " + type);
            }
            if (providers.putIfAbsent(type, provider) != null) {
                throw new IllegalStateException("列表字段数据源重复注册: " + type);
            }
            log.info("注册列表字段数据提供者: type={}, class={}", type, provider.getClass().getSimpleName());
        }
    }

    /**
     * 获取指定类型的数据提供者
     */
    public ListFieldDataProvider getProvider(String dataSourceType) {
        return providers.get(normalize(dataSourceType));
    }

    /**
     * 是否支持该数据源类型
     */
    public boolean supports(String dataSourceType) {
        return providers.containsKey(normalize(dataSourceType));
    }

    public List<ListFieldDataSourceOptionDTO> getOptions() {
        List<ListFieldDataSourceOptionDTO> options = new ArrayList<>();
        options.add(ListFieldDataSourceOptionDTO.builder()
                .value("ENTITY_FIELD")
                .label("实体字段")
                .description("直接读取实体系统字段或自定义字段。")
                .supportsVirtualField(false)
                .supportsQuery(true)
                .configSchema(List.of())
                .build());
        for (Map.Entry<String, ListFieldDataProvider> entry : providers.entrySet()) {
            ListFieldDataProvider provider = entry.getValue();
            options.add(ListFieldDataSourceOptionDTO.builder()
                    .value(entry.getKey())
                    .label(provider.getDisplayName())
                    .description(provider.getDescription())
                    .supportsVirtualField(provider.supportsVirtualField())
                    .supportsQuery(provider.supportsQuery())
                    .configSchema(provider.getConfigSchema())
                    .build());
        }
        return options;
    }

    public void validate(EntityListField field) {
        String type = normalize(field.getDataSourceType());
        if ("ENTITY_FIELD".equals(type)) {
            return;
        }
        ListFieldDataProvider provider = providers.get(type);
        if (provider == null) {
            throw new IllegalArgumentException("未注册的列表字段数据源: " + type);
        }
        Map<String, Object> config = parseObject(field.getDataSourceConfig(), "数据源配置");
        validateSchema(provider.getConfigSchema(), config);
        provider.validateConfig(field, config);
    }

    private void validateSchema(List<Map<String, Object>> schema, Map<String, Object> config) {
        if (schema == null) {
            return;
        }
        for (Map<String, Object> item : schema) {
            String key = String.valueOf(item.get("key"));
            if (Boolean.TRUE.equals(item.get("required")) && !hasValue(config.get(key))) {
                throw new IllegalArgumentException("数据源配置缺少必填项: " + key);
            }
        }
    }

    private Map<String, Object> parseObject(String json, String label) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> value = objectMapper.readValue(json, new TypeReference<>() {});
            if (value == null) {
                throw new IllegalArgumentException(label + "必须为 JSON 对象");
            }
            return value;
        } catch (Exception e) {
            throw new IllegalArgumentException(label + "不是合法 JSON 对象");
        }
    }

    private boolean hasValue(Object value) {
        return value != null && (!(value instanceof String text) || !text.isBlank());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
