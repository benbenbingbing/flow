package com.workflow.entity.excel.config;

import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.excel.model.ExcelSchemeDocument;

import java.util.ArrayList;
import java.util.List;

/** 发布校验的可读定位信息。列序号按各方向分别计算，与编辑器的 A、B、… 标记一致。 */
public final class ExcelConfigurationErrors {
    private ExcelConfigurationErrors() { }

    /**
     * 为列级失败附加方案、方向、列标题、字段名称/编码及配置区位置。
     * columnIndex 是原始 columns 下标；历史双向列可能在两个页签中有不同序号，必须同时提示。
     * 仅负责构造错误，不跳过校验、不更改草稿或发布内容。
     */
    public static IllegalArgumentException column(ExcelSchemeDocument doc, int columnIndex,
            EntityField field, String setting, String reason) {
        var column = doc.columns().get(columnIndex);
        int inputs = 0, outputs = 0, inactive = 0;
        for (int i = 0; i <= columnIndex; i++) {
            var current = doc.columns().get(i);
            if (current.importEnabled()) inputs++;
            if (current.exportEnabled()) outputs++;
            if (!current.importEnabled() && !current.exportEnabled()) inactive++;
        }
        List<String> locations = new ArrayList<>();
        if (column.importEnabled()) locations.add("导入设置 → " + letter(inputs) + " 列");
        if (column.exportEnabled()) locations.add("导出设置 → " + letter(outputs) + " 列");
        if (locations.isEmpty()) locations.add("未启用的历史列 → 第 " + inactive + " 项");
        String fieldCode = value(column.fieldCode(), "未选择");
        String fieldName = field == null ? fieldCode : value(field.getFieldName(), fieldCode);
        return new IllegalArgumentException(scheme(doc) + " → " + String.join(" / ", locations)
                + "“" + value(column.header(), "未填写标题") + "”（实体字段：“" + fieldName + "” / " + fieldCode
                + "）→ " + setting + "：" + reason);
    }

    /** 非列级错误使用界面上的区块/属性名称，避免写入表单等错误被误认为字段转换错误。 */
    public static IllegalArgumentException setting(ExcelSchemeDocument doc, String setting, String reason) {
        return new IllegalArgumentException(scheme(doc) + " → " + setting + "：" + reason);
    }

    private static String scheme(ExcelSchemeDocument doc) {
        return "方案“" + value(doc.name(), "未命名") + "”（" + value(doc.schemeCode(), "未填写编码") + "）";
    }
    private static String value(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private static String letter(int number) {
        StringBuilder result = new StringBuilder();
        while (number > 0) { number--; result.insert(0, (char) ('A' + number % 26)); number /= 26; }
        return result.toString();
    }
}
