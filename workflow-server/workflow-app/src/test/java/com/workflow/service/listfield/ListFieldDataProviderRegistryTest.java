package com.workflow.service.listfield;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.EntityDataDTO;
import com.workflow.entity.EntityListField;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 列表字段数据提供者注册表测试。
 *
 * <p>被测对象：{@link ListFieldDataProviderRegistry}，覆盖暴露提供者元数据与必填配置校验、
 * 拒绝重复提供者键等场景。
 */
class ListFieldDataProviderRegistryTest {

    /** 测试暴露提供者元数据并校验必填配置：验证 supports/options 正确，缺失必填配置抛异常而补全后通过 */
    @Test
    void exposesProviderMetadataAndValidatesRequiredConfig() {
        ListFieldDataProvider provider = provider("CUSTOM_SCORE");
        ListFieldDataProviderRegistry registry = new ListFieldDataProviderRegistry(
                List.of(provider),
                new ObjectMapper());

        assertTrue(registry.supports("custom_score"));
        assertEquals("CUSTOM_SCORE", registry.getOptions().get(1).getValue());

        EntityListField field = new EntityListField();
        field.setDataSourceType("CUSTOM_SCORE");
        field.setDataSourceConfig("{}");
        assertThrows(IllegalArgumentException.class, () -> registry.validate(field));

        field.setDataSourceConfig("{\"sourceField\":\"amount\"}");
        registry.validate(field);
    }

    /** 测试拒绝重复的提供者键：验证大小写归一化后键重复时抛出 IllegalStateException */
    @Test
    void rejectsDuplicateProviderKeys() {
        assertThrows(
                IllegalStateException.class,
                () -> new ListFieldDataProviderRegistry(
                        List.of(provider("CUSTOM_SCORE"), provider("custom_score")),
                        new ObjectMapper()));
    }

    /** 构造指定键的测试数据提供者，含 sourceField 必填配置 schema */
    private ListFieldDataProvider provider(String key) {
        return new ListFieldDataProvider() {
            @Override
            public String getDataSourceType() {
                return key;
            }

            @Override
            public List<Map<String, Object>> getConfigSchema() {
                return List.of(Map.of(
                        "key", "sourceField",
                        "label", "来源字段",
                        "type", "text",
                        "required", true));
            }

            @Override
            public void enrich(
                    List<EntityDataDTO> records,
                    List<EntityListField> fields,
                    Map<String, Object> context) {
            }
        };
    }
}
