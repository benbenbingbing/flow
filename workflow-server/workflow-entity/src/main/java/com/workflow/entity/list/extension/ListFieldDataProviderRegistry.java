package com.workflow.entity.list.extension;

import com.workflow.contracts.entity.list.spi.ListFieldDataProvider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.list.api.response.ListFieldDataSourceOptionDTO;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 列表字段数据提供者注册中心
 * 
 * 在构造时收集所有 {@link ListFieldDataProvider} 实现，按数据源类型（大写）注册并提供查询。
 * 列配置只使用内置 FIELD_TEMPLATE 的 schema 与执行能力；业务列统一通过 UiDataSourceProvider 接口扩展接入，
 * 不再从这里暴露或调度自定义 Provider。保留的注册表供内部模板能力复用。
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

    /** 返回固定的列取值方式，具体业务接口由扩展管理提供。 */
    public List<ListFieldDataSourceOptionDTO> getOptions() {
        return getOptions(null);
    }

    /**
     * 返回三种互斥取值方式。字段组合保留模板 schema，业务 Provider 不再成为独立取值方式。
     *
     * @param entityCode 当前实体编码；接口的实体/列表适用范围在接口选项查询中校验
     * @return 固定顺序的实体字段、字段组合和扩展接口选项
     */
    public List<ListFieldDataSourceOptionDTO> getOptions(String entityCode) {
        ListFieldDataProvider template = providers.get("FIELD_TEMPLATE");
        return List.of(
                ListFieldDataSourceOptionDTO.builder()
                        .value("ENTITY_FIELD")
                        .label("实体字段")
                        .description("直接读取当前列对应的实体字段，可用于查询和排序。")
                        .supportsVirtualField(false)
                        .supportsQuery(true)
                        .supportedEntityCodes(List.of())
                        .configSchema(List.of())
                        .build(),
                ListFieldDataSourceOptionDTO.builder()
                        .value("FIELD_TEMPLATE")
                        .label("字段组合")
                        .description("使用 ${fieldCode} 组合当前行字段，仅生成展示文本，不执行脚本或写回数据。")
                        .supportsVirtualField(true)
                        .supportsQuery(false)
                        .supportedEntityCodes(List.of())
                        .configSchema(template == null ? List.of() : template.getConfigSchema())
                        .build(),
                ListFieldDataSourceOptionDTO.builder()
                        .value("INTERFACE")
                        .label("扩展接口")
                        .description("选择扩展管理中的 LIST_COLUMN 接口，计算当前页列值；结果只用于展示，不参与查询和排序。")
                        .supportsVirtualField(true)
                        .supportsQuery(false)
                        .supportedEntityCodes(List.of())
                        .configSchema(List.of())
                        .build());
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
        provider.validateConfig(ListFieldExtensionMapping.field(field), config);
    }

    /**
     * 按 schema 校验配置必填项，schema 为空时跳过。
     *
     * @param schema 结构，供本方法校验结构时使用
     * @param config 配置内容，决定后续结构的处理规则
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
     *
     * @param json JSON，作为 {@code objectMapper.readValue} 的输入影响后续处理
     * @param label 标签，后续用于解析对象时匹配或展示
     * @return 对象键值结果，供调用方继续处理
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

    /**
     * 判断值是否非空（字符串去除空白后判断）
     *
     * @param value 待判断是否具有值的原始输入，结果供调用方继续使用
     * @return 值条件成立时为 true，否则为 false
     */
    private boolean hasValue(Object value) {
        return value != null && (!(value instanceof String text) || !text.isBlank());
    }

    /**
     * 归一化数据源类型：去除空白并转大写，null 返回空串
     *
     * @param value 待规范化列表字段数据提供者{@code registry}的原始输入，结果供调用方继续使用
     * @return 规范化后的列表字段数据提供者{@code registry}文本，供调用方比较或展示
     */
    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

}
