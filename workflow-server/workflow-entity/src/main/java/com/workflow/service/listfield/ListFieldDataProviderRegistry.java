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
 * 
 * 在构造时收集所有 {@link ListFieldDataProvider} 实现，按数据源类型（大写）注册并提供查询。
 * 负责提供数据源选项清单、校验列表字段的数据源配置及必填项，是列表字段自定义数据能力的核心调度入口。
 */
@Slf4j
@Component
public class ListFieldDataProviderRegistry {

    /** 数据源类型编码正则：大写字母开头，长度 2~64，仅含大写字母、数字、下划线 */
    private static final Pattern PROVIDER_KEY = Pattern.compile("[A-Z][A-Z0-9_]{1,63}");

    /** 已注册的数据提供者，key 为归一化后的数据源类型 */
    private final Map<String, ListFieldDataProvider> providers = new LinkedHashMap<>();
    private final ObjectMapper objectMapper;

    /**
     * 构造时注入所有数据提供者并完成注册。
     *
     * @param providerList Spring 容器中所有 ListFieldDataProvider 实现
     * @param objectMapper JSON 序列化工具
     * @throws IllegalStateException 数据源编码不合法或重复注册时抛出
     */
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
     *
     * @param dataSourceType 数据源类型（大小写不敏感）
     * @return 对应的数据提供者，未注册时返回 null
     */
    public ListFieldDataProvider getProvider(String dataSourceType) {
        return providers.get(normalize(dataSourceType));
    }

    /**
     * 是否支持该数据源类型
     *
     * @param dataSourceType 数据源类型（大小写不敏感）
     * @return 已注册返回 true
     */
    public boolean supports(String dataSourceType) {
        return providers.containsKey(normalize(dataSourceType));
    }

    /**
     * 构造可用于前端下拉选择的数据源选项清单。
     * 首项固定为内置的 ENTITY_FIELD（实体字段），其余按注册顺序追加各提供者的展示信息。
     *
     * @return 数据源选项列表
     */
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

    /**
     * 校验列表字段的数据源配置。
     * ENTITY_FIELD 类型直接放行；其余类型需提供者已注册、配置为合法 JSON，
     * 并通过 schema 必填项校验与提供者自定义校验。
     *
     * @param field 待校验的列表字段配置
     * @throws IllegalArgumentException 未注册数据源、配置非 JSON 或缺少必填项时抛出
     */
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

    /**
     * 按 schema 校验配置必填项，schema 为空时跳过。
     */
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

    /**
     * 将 JSON 字符串解析为对象，空白返回空 Map，非对象或格式错误抛出 IllegalArgumentException。
     */
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

    /** 判断值是否非空（字符串去除空白后判断） */
    private boolean hasValue(Object value) {
        return value != null && (!(value instanceof String text) || !text.isBlank());
    }

    /** 归一化数据源类型：去除空白并转大写，null 返回空串 */
    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
