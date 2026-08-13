package com.workflow.project.custom;

import com.workflow.contracts.ui.UiInvocationContext;
import com.workflow.contracts.ui.UiDataSourceUsages;
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

    /** 列表虚拟列、表单 patch 和字段计算结果共用的文本前缀。 */
    private static final String VALUE_PREFIX = "valuePrefix";

    /** 表单生命周期分支需要回填的字段编码；空值表示返回空 patch。 */
    private static final String TARGET_FIELD = "targetField";

    /** {@code FIELD_DEFAULT} 分支返回的字段初始值。 */
    private static final String DEFAULT_VALUE = "defaultValue";
    private static final String DEFAULT_VALUE_PREFIX =
            "统一数据源演示";

    /** 由该复合 Provider 统一处理并返回页面提示的按钮事件。 */
    private static final Set<String> BUTTON_USAGES =
            Set.of(
                    UiDataSourceUsages.FIELD_BUTTON_CLICK,
                    UiDataSourceUsages.TOOLBAR_BUTTON_CLICK,
                    UiDataSourceUsages.ROW_BUTTON_CLICK,
                    UiDataSourceUsages.FORM_BUTTON_CLICK);

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
                        VALUE_PREFIX, Map.of(
                                "type", "string",
                                "title", "列值前缀",
                                "default", DEFAULT_VALUE_PREFIX),
                        TARGET_FIELD, Map.of(
                                "type", "string",
                                "title", "表单回填字段（可选）",
                                "default", ""),
                        DEFAULT_VALUE, Map.of(
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
                configuration.get(VALUE_PREFIX),
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
            case UiDataSourceUsages.LIST_COLUMN ->
                    columnValues(input, valuePrefix);
            case UiDataSourceUsages.LIST_QUERY ->
                    emptyResult(usage);
            case UiDataSourceUsages.FORM_INIT,
                    UiDataSourceUsages.AFTER_LOAD,
                    UiDataSourceUsages.BEFORE_SUBMIT ->
                    fieldPatch(
                            text(
                                    configuration.get(
                                            TARGET_FIELD),
                                    ""),
                            valuePrefix + ":" + usage);
            case UiDataSourceUsages.FIELD_OPTIONS ->
                    options("全局选项");
            case UiDataSourceUsages.FIELD_DEFAULT ->
                    fieldValue(text(
                            configuration.get(
                                    DEFAULT_VALUE),
                            "GLOBAL_DEFAULT"));
            case UiDataSourceUsages.FIELD_COMPUTE ->
                    fieldValue(
                            valuePrefix + ":"
                                    + text(
                                    input.get("value"),
                                    "EMPTY"));
            case UiDataSourceUsages.SUBFORM_ROWS ->
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
            case UiDataSourceUsages.LIST_COLUMN ->
                    "LIST_COLUMN_VALUES";
            case UiDataSourceUsages.LIST_QUERY ->
                    "EMPTY_LIST_QUERY";
            case UiDataSourceUsages.FORM_INIT,
                    UiDataSourceUsages.AFTER_LOAD,
                    UiDataSourceUsages.BEFORE_SUBMIT ->
                    "FORM_PATCH";
            case UiDataSourceUsages.FIELD_OPTIONS ->
                    UiDataSourceUsages.FIELD_OPTIONS;
            case UiDataSourceUsages.FIELD_DEFAULT ->
                    UiDataSourceUsages.FIELD_DEFAULT;
            case UiDataSourceUsages.FIELD_COMPUTE ->
                    UiDataSourceUsages.FIELD_COMPUTE;
            case UiDataSourceUsages.SUBFORM_ROWS ->
                    "EMPTY_SUBFORM_ROWS";
            default -> BUTTON_USAGES.contains(usage)
                    ? "BUTTON_EVENT"
                    : "DIAGNOSTIC";
        };
    }
}
