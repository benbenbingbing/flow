package com.workflow.admin.setting.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.workflow.admin.setting.api.GlobalSettingException;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
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
    public static final String SIDEBAR_COLLAPSED = "ui.layout.sidebar_collapsed";
    public static final String TABS_ENABLED = "ui.layout.tabs_enabled";
    public static final String MOBILE_THEME = "ui.mobile.theme";
    public static final String MIGRATION_SIGNING_KEY = "config.migration.signing_key";
    private static final int MAX_VALUE_BYTES = 16 * 1024;
    private final ObjectMapper json = new ObjectMapper();
    private final List<Definition> definitions = List.of(new Definition(
            FIELD_TYPES_COLLAPSED, "实体设计字段类型面板收起状态",
            "true 表示收起，false 表示展开，默认展开。同一账号在所有实体设计页共用；用户设置优先于系统设置，删除个人记录后恢复继承。切换状态自动保存，不影响实体未保存状态和发布。",
            ValueType.BOOLEAN, BooleanNode.FALSE, Set.of(SYSTEM, USER), true, false), new Definition(
            SIDEBAR_COLLAPSED, "左侧主菜单收起状态",
            "true 表示收起，false 表示展开，默认展开。未保存个人偏好时使用系统设置；用户手动切换后自动保存个人偏好，同一账号跨页面和浏览器共用，用户配置优先于系统配置。仅控制桌面主菜单，移动端导航抽屉不受影响。",
            ValueType.BOOLEAN, BooleanNode.FALSE, Set.of(SYSTEM, USER), true, false), new Definition(
            TABS_ENABLED, "启用顶部多标签页",
            "true 表示在顶部面包屑位置显示页面选项卡，false 表示单页模式并显示面包屑，默认关闭。用户可在右上角用户菜单切换，个人配置优先于系统配置。切换标签保留页面状态，关闭未保存页面时确认；已打开标签和业务输入仅保留在当前会话内，刷新页面后不恢复。偏好保存失败不影响本次模式切换。",
            ValueType.BOOLEAN, BooleanNode.FALSE, Set.of(SYSTEM, USER), true, false), new Definition(
            MOBILE_THEME, "移动端主题",
            "系统统一的移动端配色。支持预设与自定义浅色主题，保存后刷新移动端页面即可生效；不支持个人覆盖，不影响电脑端配色。",
            ValueType.JSON, MobileThemeConfiguration.defaultValue(), Set.of(SYSTEM), true, false), new Definition(
            MIGRATION_SIGNING_KEY, "配置迁移签名密钥",
            "用于配置迁移包的 HMAC-SHA256 签名与验签。初始化时生成随机密钥；可将需要互认的环境设置为相同值。输入 32 至 256 字节的密钥，不能包含首尾空白。保存后立即生效，已生成的包保留原签名；签名不一致时需确认来源后导入。已保存的密钥不回显，也不支持个人覆盖或恢复默认值。",
            ValueType.STRING, NullNode.instance, Set.of(SYSTEM), false, true));

    /** JSON 专用于对象/数组，前三种标量类型各自单独校验。 */
    public enum ValueType { BOOLEAN, NUMBER, STRING, JSON }

    /** clientReadable 限制个人接口，sensitive 要求系统接口也不回显值且禁止删除恢复默认。 */
    public record Definition(String key, String name, String remark, ValueType valueType,
                             JsonNode defaultValue, Set<String> scopes, boolean clientReadable, boolean sensitive) { }

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
        JsonNode value = parse(definition.valueType(), text);
        if (MOBILE_THEME.equals(definition.key())) return MobileThemeConfiguration.normalize(value);
        if (MIGRATION_SIGNING_KEY.equals(definition.key())) {
            String key = value.textValue();
            int bytes = key.getBytes(StandardCharsets.UTF_8).length;
            String normalized = key.toLowerCase(Locale.ROOT);
            if (bytes < 32 || bytes > 256 || !key.equals(key.strip())
                    || normalized.contains("workflow-config-migration")
                    || normalized.contains("replace-with") || normalized.contains("changeme")) {
                throw GlobalSettingException.invalid("迁移签名密钥须为 32 至 256 字节，不能包含首尾空白或使用公开示例值");
            }
        }
        return value;
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
