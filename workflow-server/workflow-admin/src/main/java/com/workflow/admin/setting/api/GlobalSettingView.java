package com.workflow.admin.setting.api;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 设置的有效值与当前编辑作用域的版本。override 即使值非法仍保留，便于修复或重置；
 * 它不一定是有效值的来源，不能拿系统记录的版本写入个人设置。
 */
public record GlobalSettingView(String settingKey, String name, String remark,
                                String settingValueType, JsonNode value, JsonNode defaultValue,
                                String source, boolean userOverridable, StoredVersion override) {
    public record StoredVersion(String id, long version) { }
}
