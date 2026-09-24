package com.workflow.entity.list.extension;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
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
 * 不执行任何脚本逻辑。空值显示沿用列渲染配置中的 emptyText，
 * 结果写入 record.extData。
 */
@Component
@RequiredArgsConstructor
public class TemplateListFieldDataProvider implements ListFieldDataProvider {

    /** 模板占位符正则，匹配 ${fieldCode} 形式，fieldCode 须以字母开头 */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z][A-Za-z0-9_]*)}");

    private final ObjectMapper objectMapper;

    /**
     * 读取数据来源类型；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的数据来源类型文本，供调用方比较或展示
     */
    @Override
    public String getDataSourceType() {
        return "FIELD_TEMPLATE";
    }

    /**
     * 读取用户可见名称，供页面和操作日志展示。
     *
     * @return 读取后的展示名称文本，供调用方比较或展示
     */
    @Override
    public String getDisplayName() {
        return "字段组合模板";
    }

    /**
     * 读取描述；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的描述文本，供调用方比较或展示
     */
    @Override
    public String getDescription() {
        return "使用 ${fieldCode} 占位符组合当前行字段，不执行脚本。";
    }

    /**
     * 模板只在数据库分页后计算展示值，不能参与筛选，否则必须读取全部候选行。
     *
     * @return 查询条件成立时为 true，否则为 false
     */
    @Override
    public boolean supportsQuery() {
        return false;
    }

    /**
     * 读取配置结构；查询结果供调用方展示或继续处理。
     *
     * @return 模板列表字段数据提供者集合，供调用方遍历或展示
     */
    @Override
    public List<Map<String, Object>> getConfigSchema() {
        return List.of(
                schema("template", "组合模板", "textarea", true, "${code} - ${name}"));
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
            String emptyText = resolveEmptyText(field);
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
     * 空值文本统一由单元格渲染配置维护，避免数据源和显示组件重复配置。
     *
     * @param field 字段，作为 {@code parse} 的输入影响后续处理
     * @return 解析后的空文本文本，供调用方比较或展示
     */
    private String resolveEmptyText(EntityListField field) {
        Map<String, Object> renderConfig = parse(field.getRenderConfig());
        Object configured = renderConfig.get("emptyText");
        return configured == null || String.valueOf(configured).isBlank()
                ? "-"
                : String.valueOf(configured);
    }

    /**
     * 渲染模板：将 ${fieldCode} 占位符替换为记录中对应字段值，空值用 emptyText 替代。
     *
     * @param template 模板，作为 {@code PLACEHOLDER.matcher} 的输入影响后续处理
     * @param emptyText 空文本，供本方法处理{@code render}时使用
     * @param record 记录，作为 {@code getValue} 的输入影响后续处理
     * @return 处理后的{@code render}文本，供调用方比较或展示
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

    /**
     * 按优先级从记录中取字段值：扩展数据 > 业务数据 > 系统基础字段
     *
     * @param record 记录，供本方法读取值时使用
     * @param fieldCode 字段编码，后续用于读取值时定位或关联目标
     * @return 符合条件的模板列表字段数据提供者结果，供调用方继续处理
     */
    private Object getValue(EntityDataDTO record, String fieldCode) {
        if (record.getExtData() != null && record.getExtData().containsKey(fieldCode)) {
            return record.getExtData().get(fieldCode);
        }
        if (record.getData() != null && record.getData().containsKey(fieldCode)) {
            return record.getData().get(fieldCode);
        }
        return switch (fieldCode) {
            case "id" -> record.getId();
            case "code" -> record.getCode();
            case "name" -> record.getName();
            case "status" -> record.getStatus();
            case "submitterName" -> record.getSubmitterName();
            case "create_time" -> record.getCreateTime();
            case "update_time" -> record.getUpdateTime();
            case "create_by" -> record.getCreateBy();
            case "update_by" -> record.getUpdateBy();
            default -> null;
        };
    }

    /**
     * 解析数据源配置 JSON，空白返回空 Map，格式错误抛出 IllegalArgumentException
     *
     * @param json JSON，作为 {@code objectMapper.readValue} 的输入影响后续处理
     * @return 模板列表字段数据提供者键值结果，供调用方继续处理
     */
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

    /**
     * 构造一个配置项 schema 描述对象
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param label 标签，后续用于处理结构时匹配或展示
     * @param type 类型标识，决定后续结构采用的处理分支
     * @param required 必填，作为 {@code schema.put} 的输入影响后续处理
     * @param defaultValue 首选值不可用时采用的兜底值，保证后续处理有稳定输入
     * @return 结构键值结果，供调用方继续处理
     */
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
