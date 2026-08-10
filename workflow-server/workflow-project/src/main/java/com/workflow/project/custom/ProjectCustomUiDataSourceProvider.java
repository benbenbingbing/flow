package com.workflow.project.custom;

import com.workflow.contracts.ui.UiInvocationContext;
import com.workflow.core.logging.LogValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ENTITY 作用范围的复合上下文统一数据源扩展示例。
 *
 * <p>接口服务中选择“注册 Provider”，Provider 选择 {@value #CODE}，
 * 作用范围选择具体实体后，可绑定到该实体的已发布表单或列表。该实现覆盖列表列、
 * 表单初始化、字段选项/默认值/计算和按钮事件等常见绑定位置，适合验证一个
 * Provider 复用多个 FORM/LIST 操作的全链路。
 * </p>
 */
@Slf4j
@Component
public class ProjectCustomUiDataSourceProvider
        extends ProjectCustomUiDataSourceProviderSupport {

    public static final String CODE =
            "PROJECT_CUSTOM_UI_DATA_SOURCE";
    public static final String RECOMMENDED_SCOPE =
            "ENTITY";
    private static final String DEFAULT_VALUE_PREFIX =
            "统一数据源演示";
    private static final Set<String> BUTTON_USAGES =
            Set.of(
                    "FIELD_BUTTON_CLICK",
                    "TOOLBAR_BUTTON_CLICK",
                    "ROW_BUTTON_CLICK",
                    "FORM_BUTTON_CLICK");

    @Override
    public String getCode() {
        return CODE;
    }

    @Override
    public String getDisplayName() {
        return "项目自定义统一数据源 [ENTITY/复合上下文]";
    }

    @Override
    public Map<String, Object> configurationSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "scene", Map.of(
                                "type", "string",
                                "title", "验证场景"),
                        "message", Map.of(
                                "type", "string",
                                "title", "日志说明"),
                        "valuePrefix", Map.of(
                                "type", "string",
                                "title", "列值前缀",
                                "default", DEFAULT_VALUE_PREFIX),
                        "targetField", Map.of(
                                "type", "string",
                                "title", "表单回填字段（可选）",
                                "default", ""),
                        "defaultValue", Map.of(
                                "type", "string",
                                "title", "字段默认值",
                                "default", "GLOBAL_DEFAULT")));
    }

    @Override
    protected String recommendedScope() {
        return RECOMMENDED_SCOPE;
    }

    @Override
    protected Object executeUsage(
            UiInvocationContext context,
            Map<String, Object> configuration,
            Map<String, Object> input) {
        String usage = usage(context);
        String fieldCode = fieldCode(input.get("field"));
        String valuePrefix = text(
                configuration.get("valuePrefix"),
                DEFAULT_VALUE_PREFIX);
        log.info(
                "ENTITY 复合上下文统一数据源路由: code={}, usage={}, branch={}, entityCode={}, listKey={}, fieldCode={}",
                CODE,
                LogValue.safe(usage),
                LogValue.safe(branch(usage)),
                LogValue.safe(context == null
                        ? null : context.entityCode()),
                LogValue.safe(context == null
                        ? null : context.listKey()),
                LogValue.safe(fieldCode));

        return switch (usage) {
            case "LIST_COLUMN" ->
                    columnValues(input, valuePrefix);
            case "LIST_QUERY" ->
                    emptyResult(usage);
            case "FORM_INIT", "AFTER_LOAD",
                    "BEFORE_SUBMIT" ->
                    fieldPatch(
                            text(
                                    configuration.get(
                                            "targetField"),
                                    ""),
                            valuePrefix + ":" + usage);
            case "FIELD_OPTIONS" ->
                    options("全局选项");
            case "FIELD_DEFAULT" ->
                    fieldValue(text(
                            configuration.get(
                                    "defaultValue"),
                            "GLOBAL_DEFAULT"));
            case "FIELD_COMPUTE" ->
                    fieldValue(
                            valuePrefix + ":"
                                    + text(
                                    input.get("value"),
                                    "EMPTY"));
            case "SUBFORM_ROWS" ->
                    List.of();
            default -> BUTTON_USAGES.contains(usage)
                    ? eventMessage(
                    "GLOBAL Provider 已执行按钮事件: "
                            + usage,
                    context)
                    : diagnosticResult(
                    context,
                    configuration,
                    input);
        };
    }

    private String branch(
            String usage) {
        return switch (usage) {
            case "LIST_COLUMN" -> "LIST_COLUMN_VALUES";
            case "LIST_QUERY" -> "EMPTY_LIST_QUERY";
            case "FORM_INIT", "AFTER_LOAD",
                    "BEFORE_SUBMIT" -> "FORM_PATCH";
            case "FIELD_OPTIONS" -> "FIELD_OPTIONS";
            case "FIELD_DEFAULT" -> "FIELD_DEFAULT";
            case "FIELD_COMPUTE" -> "FIELD_COMPUTE";
            case "SUBFORM_ROWS" -> "EMPTY_SUBFORM_ROWS";
            default -> BUTTON_USAGES.contains(usage)
                    ? "BUTTON_EVENT"
                    : "DIAGNOSTIC";
        };
    }
}
