package com.workflow.admin.setting.api.request;

/** 设置写入契约；归属、名称、类型和说明由服务端注册定义确定，客户端只提交值与预期版本。 */
public final class GlobalSettingRequests {
    /**
     * 初始化全局设置{@code requests}，保存构造参数供后续方法使用。
     */
    private GlobalSettingRequests() { }

    /**
     * 值是序列化文本；首次创建时预期 ID/版本均为 null，否则必须来自读取响应。
     *
     * @param settingValue 设置值，保存在对象中供后续校验、查询或展示
     * @param expectedId 预期ID，后续用于处理保存时定位或关联目标
     * @param expectedVersion 预期版本，保存在对象中供后续校验、查询或展示
     */
    public record Save(String settingValue, String expectedId, Long expectedVersion) { }

    /**
     * 恢复默认需提交当前记录 ID/版本；两者均为 null 仅表示确认当前不存在覆盖。
     *
     * @param expectedId 预期ID，后续用于处理重置时定位或关联目标
     * @param expectedVersion 预期版本，保存在对象中供后续校验、查询或展示
     */
    public record Reset(String expectedId, Long expectedVersion) { }
}
