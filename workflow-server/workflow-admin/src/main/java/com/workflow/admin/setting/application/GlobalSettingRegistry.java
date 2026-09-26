package com.workflow.admin.setting.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.workflow.admin.setting.api.error.GlobalSettingException;
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
    public static final String USER_INTERFACE_PREFERENCES = "ui.user_preferences";
    public static final String SIDEBAR_BRANDING = "ui.layout.sidebar_branding";
    public static final String MOBILE_THEME = "ui.mobile.theme";
    public static final String MIGRATION_SIGNING_KEY = "config.migration.signing_key";
    private static final int MAX_VALUE_BYTES = 16 * 1024;
    private final ObjectMapper json = new ObjectMapper();
    private final List<Definition> definitions = List.of(new Definition(
            USER_INTERFACE_PREFERENCES, "用户界面偏好",
            "统一维护字段类型面板收起、左侧主菜单收起和顶部多标签页三个选项，默认均关闭。用户主动切换后保存个人选择；按字段优先使用个人配置，未设置的字段继承系统默认值。同一账号跨页面和浏览器共用，恢复某个选项不会清除其他个人选择。",
            ValueType.JSON, UserInterfacePreferences.defaultValue(), Set.of(SYSTEM, USER), true, false), new Definition(
            SIDEBAR_BRANDING, "侧边栏标识",
            "配置左侧菜单顶部的名称、图标和自定义图片，桌面侧栏与窄屏导航共用。图片以 imageBase64 存入 JSON，支持不超过 32 KiB 的 PNG、JPG、GIF、WebP，优先于图标显示；未配置时默认使用 Connection 图标和“流程配置系统”。仅支持系统配置，保存后当前页面立即更新，其他页面刷新后生效。",
            ValueType.JSON, SidebarBrandingConfiguration.defaultValue(), Set.of(SYSTEM), true, false), new Definition(
            MOBILE_THEME, "移动端主题",
            "系统统一的移动端配色。支持预设与自定义浅色主题，保存后刷新移动端页面即可生效；不支持个人覆盖，不影响电脑端配色。",
            ValueType.JSON, MobileThemeConfiguration.defaultValue(), Set.of(SYSTEM), true, false), new Definition(
            MIGRATION_SIGNING_KEY, "配置迁移签名密钥",
            "用于配置迁移包的 HMAC-SHA256 签名与验签。初始化时生成随机密钥；可将需要互认的环境设置为相同值。输入 32 至 256 字节的密钥，不能包含首尾空白。保存后立即生效，已生成的包保留原签名；签名不一致时需确认来源后导入。已保存的密钥不回显，也不支持个人覆盖或恢复默认值。",
            ValueType.STRING, NullNode.instance, Set.of(SYSTEM), false, true));

    /** JSON 专用于对象/数组，前三种标量类型各自单独校验。 */
    public enum ValueType { BOOLEAN, NUMBER, STRING, JSON }

    /**
     * clientReadable 限制个人接口，sensitive 要求系统接口也不回显值且禁止删除恢复默认。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param name 展示名称，供界面或日志识别
     * @param remark {@code remark}，保存在对象中供后续校验、查询或展示
     * @param valueType 值类型标识，决定后续定义采用的处理分支
     * @param defaultValue 首选值不可用时采用的兜底值，保证后续处理有稳定输入
     * @param scopes {@code scopes}，保存在对象中供后续校验、查询或展示
     * @param clientReadable 客户端可读，保存在对象中供后续校验、查询或展示
     * @param sensitive {@code sensitive}，保存在对象中供后续校验、查询或展示
     */
    public record Definition(String key, String name, String remark, ValueType valueType,
                             JsonNode defaultValue, Set<String> scopes, boolean clientReadable, boolean sensitive) { }

    /**
     * 整理全部数据，供调用方遍历或继续处理。
     *
     * @return 定义集合，供调用方遍历或展示
     */
    public List<Definition> all() { return definitions; }

    /**
     * 精确匹配注册键，拒绝大小写变体与未知键。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @return 校验并获取后的全局设置{@code registry}结果，供调用方继续处理
     */
    public Definition require(String key) {
        return definitions.stream().filter(item -> item.key().equals(key)).findFirst()
                .orElseThrow(() -> GlobalSettingException.invalid("未知设置项"));
    }

    /**
     * 在应用层完整解析文本并验证业务类型；拒绝尾随内容、顶层 null 及超长值。
     * 返回保留原始业务类型的节点，不依赖任何数据库 JSON 能力。
     *
     * @param definition 定义，供本方法解析全局设置{@code registry}时使用
     * @param text 待解析全局设置{@code registry}的原始输入，结果供调用方继续使用
     * @return 解析后的全局设置{@code registry}结果，供调用方继续处理
     */
    public JsonNode parse(Definition definition, String text) {
        int maxBytes = SIDEBAR_BRANDING.equals(definition.key()) ? SidebarBrandingConfiguration.MAX_VALUE_BYTES : MAX_VALUE_BYTES;
        JsonNode value = parse(definition.valueType(), text, maxBytes);
        if (USER_INTERFACE_PREFERENCES.equals(definition.key())) return UserInterfacePreferences.normalize(value);
        if (MOBILE_THEME.equals(definition.key())) return MobileThemeConfiguration.normalize(value);
        if (SIDEBAR_BRANDING.equals(definition.key())) return SidebarBrandingConfiguration.normalize(value);
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

    /**
     * 按持久化类型解析文本；数字必须有限，JSON 必须是对象或数组。
     *
     * @param type 类型标识，决定后续全局设置{@code registry}采用的处理分支
     * @param text 待解析全局设置{@code registry}的原始输入，结果供调用方继续使用
     * @return 解析后的全局设置{@code registry}结果，供调用方继续处理
     */
    public JsonNode parse(ValueType type, String text) {
        return parse(type, text, MAX_VALUE_BYTES);
    }

    /** 标识单独放宽以容纳小图片，其他设置继续使用通用上限；大小校验先于 JSON 解析。 */
    private JsonNode parse(ValueType type, String text, int maxBytes) {
        if (text == null || text.isBlank() || text.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw GlobalSettingException.invalid("设置值不能为空且不能超过 " + maxBytes / 1024 + " KiB");
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
