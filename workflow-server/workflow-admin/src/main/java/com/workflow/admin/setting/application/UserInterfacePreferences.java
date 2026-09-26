package com.workflow.admin.setting.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflow.admin.setting.api.error.GlobalSettingException;
import java.util.Set;

/** 用户界面偏好保存在同一 JSON 对象；缺失字段表示继承，显式 false 仍是个人选择。 */
public final class UserInterfacePreferences {
    private static final Set<String> FIELDS = Set.of("fieldTypesCollapsed", "sidebarCollapsed", "tabsEnabled");

    private UserInterfacePreferences() { }

    /** 三项初始行为与原界面一致；读取默认值不会为用户创建覆盖记录。 */
    public static ObjectNode defaultValue() {
        return JsonNodeFactory.instance.objectNode().put("fieldTypesCollapsed", false)
                .put("sidebarCollapsed", false).put("tabsEnabled", false);
    }

    /** 校验稀疏覆盖对象，拒绝未知字段及非布尔值；不补默认值，避免锁定未操作的选项。 */
    public static JsonNode normalize(JsonNode value) {
        if (value == null || !value.isObject()) throw GlobalSettingException.invalid("用户界面偏好须为 JSON 对象");
        value.fields().forEachRemaining(field -> {
            if (!FIELDS.contains(field.getKey()) || !field.getValue().isBoolean()) {
                throw GlobalSettingException.invalid("用户界面偏好仅支持 fieldTypesCollapsed、sidebarCollapsed、tabsEnabled 三个布尔字段");
            }
        });
        return value.deepCopy();
    }

    /** 按字段依次叠加系统及个人覆盖，确保只修改一个选项不会固定另外两项的继承值。 */
    public static JsonNode resolve(JsonNode inherited, JsonNode current) {
        ObjectNode effective = defaultValue();
        if (inherited != null) effective.setAll((ObjectNode) inherited);
        if (current != null) effective.setAll((ObjectNode) current);
        return effective;
    }
}
