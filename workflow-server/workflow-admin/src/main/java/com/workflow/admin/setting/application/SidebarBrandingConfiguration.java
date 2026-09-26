package com.workflow.admin.setting.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflow.admin.setting.api.error.GlobalSettingException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.regex.Pattern;

/** 侧边栏标识包含名称、备用图标及可选 Base64 小图片，供桌面侧栏及窄屏导航共同使用。 */
public final class SidebarBrandingConfiguration {
    public static final int MAX_IMAGE_BYTES = 32 * 1024;
    // Base64 比原图约增加三分之一；48 KiB 容纳图片及名称，且仍低于现有 TEXT 列上限。
    public static final int MAX_VALUE_BYTES = 48 * 1024;
    private static final Set<String> FIELDS = Set.of("title", "icon", "imageBase64");
    private static final Pattern IMAGE_DATA_URL = Pattern.compile("^data:image/(png|jpeg|gif|webp);base64,([A-Za-z0-9+/]+={0,2})$");

    private SidebarBrandingConfiguration() { }

    /** 未保存系统覆盖或恢复默认时沿用现有标识，无需预建数据库记录。 */
    public static ObjectNode defaultValue() {
        return JsonNodeFactory.instance.objectNode().put("title", "流程配置系统").put("icon", "Connection").put("imageBase64", "");
    }

    /**
     * 校验完整的系统标识并返回独立副本；非法字段或长度抛出设置校验异常，阻止持久化。
     * 图片只接收带 MIME 的 Base64 Data URL；旧配置缺失图片字段时继续使用原图标。
     */
    public static JsonNode normalize(JsonNode value) {
        if (value == null || !value.isObject()) {
            throw GlobalSettingException.invalid("侧边栏标识须包含系统名称和图标");
        }
        value.fieldNames().forEachRemaining(key -> {
            if (!FIELDS.contains(key)) throw GlobalSettingException.invalid("侧边栏标识包含未知配置项");
        });
        if (!value.path("title").isTextual() || !value.path("icon").isTextual()) {
            throw GlobalSettingException.invalid("系统名称和图标须为字符串");
        }
        String title = value.path("title").textValue().strip();
        String icon = value.path("icon").textValue().strip();
        if (title.isBlank() || title.length() > 40) {
            throw GlobalSettingException.invalid("系统名称须为 1 至 40 个字符");
        }
        // 清空图标代表恢复原标识，保证收起菜单后仍有可识别的系统图标。
        if (icon.isEmpty()) icon = "Connection";
        if (!icon.matches("[A-Z][A-Za-z0-9]{0,63}")) {
            throw GlobalSettingException.invalid("请选择有效的内置图标");
        }
        JsonNode image = value.get("imageBase64");
        if (image != null && !image.isTextual()) throw GlobalSettingException.invalid("图片 Base64 须为字符串");
        String imageBase64 = image == null ? "" : normalizeImage(image.textValue());
        return JsonNodeFactory.instance.objectNode().put("title", title).put("icon", icon).put("imageBase64", imageBase64);
    }

    /** 验证图片类型、Base64 编码与文件头一致；浏览器解码失败时由展示组件回退备用图标。 */
    private static String normalizeImage(String input) {
        String image = input.strip();
        if (image.isEmpty()) return "";
        if (image.length() > 4 * ((MAX_IMAGE_BYTES + 2) / 3) + 32) {
            throw GlobalSettingException.invalid("图片不能超过 32 KiB");
        }
        var match = IMAGE_DATA_URL.matcher(image);
        if (!match.matches()) throw GlobalSettingException.invalid("图片须为 PNG、JPG、GIF 或 WebP 的 Base64 Data URL");
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(match.group(2));
        } catch (IllegalArgumentException error) {
            throw GlobalSettingException.invalid("图片 Base64 格式不合法");
        }
        if (!Base64.getEncoder().encodeToString(bytes).equals(match.group(2))) {
            throw GlobalSettingException.invalid("图片 Base64 格式不合法");
        }
        if (bytes.length > MAX_IMAGE_BYTES) throw GlobalSettingException.invalid("图片不能超过 32 KiB");
        String header = new String(bytes, 0, Math.min(bytes.length, 12), StandardCharsets.ISO_8859_1);
        boolean valid = switch (match.group(1)) {
            case "png" -> header.startsWith("\u0089PNG\r\n\u001a\n");
            case "jpeg" -> header.startsWith("\u00ff\u00d8\u00ff");
            case "gif" -> header.startsWith("GIF87a") || header.startsWith("GIF89a");
            case "webp" -> header.startsWith("RIFF") && header.endsWith("WEBP") && header.length() == 12;
            default -> false;
        };
        if (!valid) throw GlobalSettingException.invalid("图片内容与文件类型不一致");
        return image;
    }
}
