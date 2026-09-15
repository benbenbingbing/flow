package com.workflow.admin.setting.api;

/** 设置写入契约；归属、名称、类型和说明由服务端注册定义确定，客户端只提交值与预期版本。 */
public final class GlobalSettingRequests {
    private GlobalSettingRequests() { }

    /** 值是序列化文本；首次创建时预期 ID/版本均为 null，否则必须来自读取响应。 */
    public record Save(String settingValue, String expectedId, Long expectedVersion) { }

    /** 恢复默认需提交当前记录 ID/版本；两者均为 null 仅表示确认当前不存在覆盖。 */
    public record Reset(String expectedId, Long expectedVersion) { }
}
