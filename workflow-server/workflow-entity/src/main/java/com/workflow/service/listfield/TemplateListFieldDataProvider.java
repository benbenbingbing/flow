package com.workflow.service.listfield;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.EntityDataDTO;
import com.workflow.entity.EntityListField;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模板字段组合数据提供者
 * 
 * 数据源类型 FIELD_TEMPLATE：使用 ${fieldCode} 占位符组合当前行字段值生成展示文本，
 * 不执行任何脚本逻辑。空值以 emptyText 替代，结果写入 record.extData。
 */
@Component
@RequiredArgsConstructor
public class TemplateListFieldDataProvider implements ListFieldDataProvider {

    /** 模板占位符正则，匹配 ${fieldCode} 形式，fieldCode 须以字母开头 */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z][A-Za-z0-9_]*)}");

    private final ObjectMapper objectMapper;

    @Override
    public String getDataSourceType() {
        return "FIELD_TEMPLATE";
    }

    @Override
    public String getDisplayName() {
        return "字段组合模板";
    }

    @Override
    public String getDescription() {
        return "使用 ${fieldCode} 占位符组合当前行字段，不执行脚本。";
    }

    @Override
    public boolean supportsQuery() {
        return true;
    }

    @Override
    public List<Map<String, Object>> getConfigSchema() {
        return List.of(
                schema("template", "组合模板", "textarea", true, "${dataNo} - ${name}"),
                schema("emptyText", "空值替代", "text", false, "-"));
    }

    /**
     * 按字段配置的模板对每条记录渲染组合文本，结果写入 record.extData。
     *
     * @param records 基础查询结果列表（会被直接修改）
     * @param fields  需要补充的字段配置列表
     * @param context 上下文参数（本实现未使用）
     */
    @Override
    public void enrich(
            List<EntityDataDTO> records,
            List<EntityListField> fields,
            Map<String, Object> context) {
        for (EntityListField field : fields) {
            Map<String, Object> config = parse(field.getDataSourceConfig());
            String template = String.valueOf(config.getOrDefault("template", ""));
            String emptyText = String.valueOf(config.getOrDefault("emptyText", "-"));
            for (EntityDataDTO record : records) {
                String value = render(template, emptyText, record);
                if (record.getExtData() == null) {
                    record.setExtData(new HashMap<>());
                }
                record.getExtData().put(field.getFieldCode(), value);
            }
        }
    }

    /**
     * 渲染模板：将 ${fieldCode} 占位符替换为记录中对应字段值，空值用 emptyText 替代。
     */
    private String render(String template, String emptyText, EntityDataDTO record) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            Object value = getValue(record, matcher.group(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement(
                    value == null || String.valueOf(value).isBlank()
                            ? emptyText
                            : String.valueOf(value)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /** 按优先级从记录中取字段值：扩展数据 > 业务数据 > 系统基础字段 */
    private Object getValue(EntityDataDTO record, String fieldCode) {
        if (record.getExtData() != null && record.getExtData().containsKey(fieldCode)) {
            return record.getExtData().get(fieldCode);
        }
        if (record.getData() != null && record.getData().containsKey(fieldCode)) {
            return record.getData().get(fieldCode);
        }
        return switch (fieldCode) {
            case "id" -> record.getId();
            case "dataNo" -> record.getDataNo();
            case "name" -> record.getName();
            case "title" -> record.getTitle();
            case "status" -> record.getStatus();
            case "submitterName" -> record.getSubmitterName();
            default -> null;
        };
    }

    /** 解析数据源配置 JSON，空白返回空 Map，格式错误抛出 IllegalArgumentException */
    private Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("字段组合模板配置不是合法 JSON");
        }
    }

    /** 构造一个配置项 schema 描述对象 */
    private Map<String, Object> schema(
            String key,
            String label,
            String type,
            boolean required,
            Object defaultValue) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("key", key);
        schema.put("label", label);
        schema.put("type", type);
        schema.put("required", required);
        schema.put("defaultValue", defaultValue);
        return schema;
    }
}
