package com.workflow.admin.setting.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflow.admin.setting.api.error.GlobalSettingException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 移动端浅色主题协议：只接受颜色数据，禁止将任意 CSS 或全局设置透传至登录页。 */
public final class MobileThemeConfiguration {
    /**
     * 初始化{@code mobile}{@code theme}配置，保存构造参数供后续方法使用。
     */
    private MobileThemeConfiguration() { }
    private static final Set<String> FIELDS = Set.of("version", "preset", "primaryColor", "backgroundColor", "surfaceColor");
    private static final Map<String, String[]> PRESETS = Map.of(
            "green", new String[]{"#196B62", "#F4F7F6"}, "blue", new String[]{"#2563EB", "#F7F8FA"},
            "purple", new String[]{"#722ED1", "#F8F7FB"}, "orange", new String[]{"#B45309", "#FAF8F5"},
            "rose", new String[]{"#B82F61", "#FCF7F9"}, "cyan", new String[]{"#087E8B", "#F3F9FA"},
            "slate", new String[]{"#475569", "#F6F7F9"});

    /**
     * 处理默认值，并将结果传给后续步骤。
     *
     * @return 处理后的默认值结果，供调用方继续处理
     */
    public static ObjectNode defaultValue() {
        return JsonNodeFactory.instance.objectNode().put("version", 1).put("preset", "green")
                .put("primaryColor", "#196B62").put("backgroundColor", "#F4F7F6").put("surfaceColor", "#FFFFFF");
    }

    /**
     * 完整校验后返回规范化副本；页面背景限定浅色，主色可任意选择，由前端派生可读文字色。
     *
     * @param value 待规范化{@code mobile}{@code theme}配置的原始输入，结果供调用方继续使用
     * @return 规范化后的{@code mobile}{@code theme}配置结果，供调用方继续处理
     */
    public static JsonNode normalize(JsonNode value) {
        if (value == null || !value.isObject() || value.size() != FIELDS.size()) throw invalid("主题配置须包含完整的颜色配置");
        value.fieldNames().forEachRemaining(key -> { if (!FIELDS.contains(key)) throw invalid("主题包含未知配置项"); });
        if (!value.path("version").isIntegralNumber() || !value.path("version").canConvertToInt()
                || value.path("version").intValue() != 1) throw invalid("不支持的主题版本");
        String preset = value.path("preset").asText("");
        if (!PRESETS.containsKey(preset) && !"custom".equals(preset)) throw invalid("不支持的主题预设");
        ObjectNode normalized = (ObjectNode) value.deepCopy();
        for (String key : new String[]{"primaryColor", "backgroundColor", "surfaceColor"}) {
            if (!value.path(key).isTextual() || !value.path(key).textValue().matches("#[0-9a-fA-F]{6}")) {
                throw invalid("颜色须使用 #RRGGBB 格式");
            }
            normalized.put(key, value.path(key).textValue().toUpperCase(Locale.ROOT));
        }
        if (luminance(normalized.path("backgroundColor").textValue()) < .6
                || luminance(normalized.path("surfaceColor").textValue()) < .6) {
            throw invalid("当前仅支持浅色主题，请选择较浅的页面背景和内容背景");
        }
        String[] colors = PRESETS.get(preset);
        if (colors != null && (!colors[0].equals(normalized.path("primaryColor").textValue())
                || !colors[1].equals(normalized.path("backgroundColor").textValue())
                || !"#FFFFFF".equals(normalized.path("surfaceColor").textValue()))) normalized.put("preset", "custom");
        return normalized;
    }

    /**
     * 处理{@code luminance}，并将结果传给后续步骤。
     *
     * @param color {@code color}，作为 {@code Integer.parseInt} 的输入影响后续处理
     * @return 处理后的{@code luminance}结果，供调用方继续处理
     */
    private static double luminance(String color) {
        double[] values = new double[3];
        for (int index = 0; index < values.length; index++) {
            double channel = Integer.parseInt(color.substring(1 + index * 2, 3 + index * 2), 16) / 255.0;
            values[index] = channel <= .04045 ? channel / 12.92 : Math.pow((channel + .055) / 1.055, 2.4);
        }
        return values[0] * .2126 + values[1] * .7152 + values[2] * .0722;
    }
    /**
     * 构造无效输入异常，阻止后续业务处理。
     *
     * @param message 消息，供本方法处理无效时使用
     * @return 处理后的无效结果，供调用方继续处理
     */
    private static GlobalSettingException invalid(String message) { return GlobalSettingException.invalid(message); }
}
