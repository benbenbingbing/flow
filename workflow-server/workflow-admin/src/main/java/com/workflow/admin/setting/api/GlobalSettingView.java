package com.workflow.admin.setting.api;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 设置的有效值与当前编辑作用域的版本。override 即使值非法仍保留，便于修复或重置；
 * 它不一定是有效值的来源，不能拿系统记录的版本写入个人设置。
 * sensitive=true 时 value/defaultValue 始终为空，仅用 configured 表达是否已配置有效值。
 *
 * @param settingKey 设置键，后续用于授权校验、关联或幂等去重
 * @param name 展示名称，供界面或日志识别
 * @param remark {@code remark}，保存在对象中供后续校验、查询或展示
 * @param settingValueType 设置值类型标识，决定后续全局设置视图采用的处理分支
 * @param value 待处理全局设置视图的原始输入，结果供调用方继续使用
 * @param defaultValue 首选值不可用时采用的兜底值，保证后续处理有稳定输入
 * @param source 待处理全局设置视图的原始输入，结果供调用方继续使用
 * @param userOverridable 用户{@code overridable}，保存在对象中供后续校验、查询或展示
 * @param override 覆盖，保存在对象中供后续校验、查询或展示
 * @param sensitive {@code sensitive}，保存在对象中供后续校验、查询或展示
 * @param configured 已配置，保存在对象中供后续校验、查询或展示
 */
public record GlobalSettingView(String settingKey, String name, String remark,
                                String settingValueType, JsonNode value, JsonNode defaultValue,
                                String source, boolean userOverridable, StoredVersion override,
                                boolean sensitive, boolean configured) {
    /**
     * 封装已存储版本的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param version 版本，保存在对象中供后续校验、查询或展示
     */
    public record StoredVersion(String id, long version) { }
}
