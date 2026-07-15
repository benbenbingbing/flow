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

@Component
@RequiredArgsConstructor
public class TemplateListFieldDataProvider implements ListFieldDataProvider {

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
