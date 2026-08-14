package com.workflow.entity.version.application.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 版本生成时已经解析完成的字段值。
 *
 * <p>历史展示只使用这里的中文文本，不再查询当前字典、人员、部门或实体定义。</p>
 */
public record FrozenValue(
        Object rawValue,
        String displayText,
        List<DisplayItem> displayItems,
        String state,
        String resolution) {

    public FrozenValue {
        displayItems = displayItems == null
                ? new ArrayList<>() : List.copyOf(displayItems);
        state = state == null ? "PRESENT" : state;
        resolution = resolution == null
                ? "RESOLVED" : resolution;
    }

    public record DisplayItem(Object value, String label) {
    }
}
