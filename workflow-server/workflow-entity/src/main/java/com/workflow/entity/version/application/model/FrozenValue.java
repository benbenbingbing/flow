package com.workflow.entity.version.application.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 版本生成时已经解析完成的字段值。
 *
 * <p>历史展示只使用这里的中文文本，不再查询当前字典、人员、部门或实体定义。</p>
 *
 * @param rawValue 原始值，保存在对象中供后续校验、查询或展示
 * @param displayText 展示文本，保存在对象中供后续校验、查询或展示
 * @param displayItems 展示条目，保存在对象中供后续校验、查询或展示
 * @param state 状态标识，决定后续{@code frozen}值采用的处理分支
 * @param resolution 解析，保存在对象中供后续校验、查询或展示
 */
public record FrozenValue(
        Object rawValue,
        String displayText,
        List<DisplayItem> displayItems,
        String state,
        String resolution) {

    /**
     * 初始化{@code frozen}值，保存构造参数供后续方法使用。
     *
     * @param rawValue 原始值，保存在对象中供后续校验、查询或展示
     * @param displayText 展示文本，保存在对象中供后续校验、查询或展示
     * @param displayItems 展示条目，保存在对象中供后续校验、查询或展示
     * @param state 状态标识，决定后续{@code frozen}值采用的处理分支
     * @param resolution 解析，保存在对象中供后续校验、查询或展示
     */
    public FrozenValue {
        displayItems = displayItems == null
                ? new ArrayList<>() : List.copyOf(displayItems);
        state = state == null ? "PRESENT" : state;
        resolution = resolution == null
                ? "RESOLVED" : resolution;
    }

    /**
     * 封装展示条目的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param value 待处理展示条目的原始输入，结果供调用方继续使用
     * @param label 标签，后续用于处理展示条目时匹配或展示
     */
    public record DisplayItem(Object value, String label) {
    }
}
