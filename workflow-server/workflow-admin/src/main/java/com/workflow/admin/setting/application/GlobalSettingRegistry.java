package com.workflow.admin.setting.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.workflow.admin.setting.api.GlobalSettingException;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * 受控设置目录。新增设置须注册名称、逻辑说明、类型、作用域与默认值，
 * 不能通过插入任意数据库键开放新的系统选项。
 */
@Component
public class GlobalSettingRegistry {
    public static final String SYSTEM = "SYSTEM";
    public static final String USER = "USER";
    public static final String FIELD_TYPES_COLLAPSED = "ui.entity_design.field_types_collapsed";
    private static final int MAX_VALUE_BYTES = 16 * 1024;
    private final ObjectMapper json = new ObjectMapper();
    private final List<Definition> definitions = List.of(new Definition(
            FIELD_TYPES_COLLAPSED, "实体设计字段类型面板收起状态",
            "true 表示收起，false 表示展开，默认展开。同一账号在所有实体设计页共用；用户设置优先于系统设置，删除个人记录后恢复继承。切换状态自动保存，不影响实体未保存状态和发布。",
            ValueType.BOOLEAN, BooleanNode.FALSE, Set.of(SYSTEM, USER), true));

    /** JSON 专用于对象/数组，前三种标量类型各自单独校验。 */
    public enum ValueType { BOOLEAN, NUMBER, STRING, JSON }

    /** clientReadable 显式限制哪些系统值可返回普通用户页面。 */
    public record Definition(String key, String name, String remark, ValueType valueType,
                             JsonNode defaultValue, Set<String> scopes, boolean clientReadable) { }

    public List<Definition> all() { return definitions; }

    /** 精确匹配注册键，拒绝大小写变体与未知键。 */
    public Definition require(String key) {
        return definitions.stream().filter(item -> item.key().equals(key)).findFirst()
                .orElseThrow(() -> GlobalSettingException.invalid("未知设置项"));
    }

    /**
     * 在应用层完整解析文本并验证业务类型；拒绝尾随内容、顶层 null 及超长值。
     * 返回保留原始业务类型的节点，不依赖任何数据库 JSON 能力。
     */
    public JsonNode parse(Definition definition, String text) {
        return parse(definition.valueType(), text);
    }

    /** 按持久化类型解析文本；数字必须有限，JSON 必须是对象或数组。 */
    public JsonNode parse(ValueType type, String text) {
        if (text == null || text.isBlank() || text.getBytes(StandardCharsets.UTF_8).length > MAX_VALUE_BYTES) {
            throw GlobalSettingException.invalid("设置值不能为空且不能超过 16 KiB");
        }
        try {
            JsonNode value = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(text);
            boolean valid = value != null && !value.isNull() && switch (type) {
                case BOOLEAN -> value.isBoolean();
                case NUMBER -> value.isNumber() && (!value.isFloatingPointNumber() || Double.isFinite(value.doubleValue()));
                case STRING -> value.isTextual();
                case JSON -> value.isObject() || value.isArray();
            };
            if (!valid) throw GlobalSettingException.invalid("设置值类型必须为 " + type);
            return value;
        } catch (JsonProcessingException exception) {
            throw GlobalSettingException.invalid("设置值格式不合法");
        }
    }
}
