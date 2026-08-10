package com.workflow.project.custom;

import com.workflow.contracts.ui.UiInvocationContext;
import com.workflow.core.logging.LogValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ENTITY 作用范围的统一数据源扩展示例。
 *
 * <p>推荐在接口服务中配置 {@code scopeType=ENTITY}，scopeId 选择实体 ID。
 * 同一实体下的多个表单和列表都可以复用该服务。示例重点展示字段选项、字段
 * 默认值、字段计算，以及字段变化、实体选择和字段按钮事件。</p>
 */
@Slf4j
@Component
public class ProjectCustomEntityUiDataSourceProvider
        extends ProjectCustomUiDataSourceProviderSupport {

    public static final String CODE =
            "PROJECT_CUSTOM_UI_ENTITY";
    public static final String RECOMMENDED_SCOPE =
            "ENTITY";
    private static final Set<String> FIELD_EVENTS =
            Set.of(
                    "FIELD_CHANGE",
                    "ENTITY_SELECTED",
                    "FIELD_BUTTON_CLICK");

    @Override
    public String getCode() {
        return CODE;
    }

    @Override
    public String getDisplayName() {
        return "项目自定义统一数据源 [ENTITY/字段]";
    }

    @Override
    public Map<String, Object> configurationSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "optionLabelPrefix", Map.of(
                                "type", "string",
                                "title", "字段选项前缀",
                                "default", "实体选项"),
                        "defaultValue", Map.of(
                                "type", "string",
                                "title", "字段默认值",
                                "default", "ENTITY_DEFAULT"),
                        "computedPrefix", Map.of(
                                "type", "string",
                                "title", "字段计算前缀",
                                "default", "实体计算"),
                        "targetField", Map.of(
                                "type", "string",
                                "title", "事件回填字段（可选）",
                                "default", "")));
    }

    @Override
    protected String recommendedScope() {
        return RECOMMENDED_SCOPE;
    }

    @Override
    protected boolean acceptsContext(
            UiInvocationContext context) {
        return context != null
                && hasText(context.entityCode());
    }

    @Override
    protected String contextMismatchReason(
            UiInvocationContext context) {
        return "ENTITY_CODE_REQUIRED";
    }

    @Override
    protected Object executeUsage(
            UiInvocationContext context,
            Map<String, Object> configuration,
            Map<String, Object> input) {
        String usage = usage(context);
        String fieldCode = text(
                input.get("fieldCode"),
                fieldCode(input.get("field")));
        log.info(
                "ENTITY 统一数据源处理业务分支: code={}, usage={}, entityCode={}, configType={}, configId={}, fieldCode={}, inputValuePresent={}",
                CODE,
                LogValue.safe(usage),
                LogValue.safe(context.entityCode()),
                LogValue.safe(context.configType()),
                LogValue.safe(context.configId()),
                LogValue.safe(fieldCode),
                input.containsKey("value"));

        if ("FIELD_OPTIONS".equals(usage)) {
            return options(text(
                    configuration.get(
                            "optionLabelPrefix"),
                    "实体选项"));
        }
        if ("FIELD_DEFAULT".equals(usage)) {
            return fieldValue(text(
                    configuration.get("defaultValue"),
                    "ENTITY_DEFAULT"));
        }
        if ("FIELD_COMPUTE".equals(usage)) {
            String prefix = text(
                    configuration.get("computedPrefix"),
                    "实体计算");
            return fieldValue(
                    prefix + ":"
                            + text(
                            input.get("value"),
                            context.entityCode()));
        }
        if ("AFTER_LOAD".equals(usage)) {
            return fieldPatch(
                    text(
                            configuration.get("targetField"),
                            ""),
                    "ENTITY_AFTER_LOAD:"
                            + context.entityCode());
        }
        if (FIELD_EVENTS.contains(usage)) {
            return fieldEventResult(
                    context,
                    configuration,
                    usage);
        }
        return diagnosticResult(
                context,
                configuration,
                input);
    }

    /**
     * 字段事件示例同时返回 message 和 FIELD_MAPPING。
     *
     * <p>message 会显示成功提示；FIELD_MAPPING 会把事件标记回填到配置的
     * targetField。目标字段必须真实存在于当前表单，否则前端虽会执行映射，
     * 但页面上不会有可见字段承载该值。</p>
     */
    private Map<String, Object> fieldEventResult(
            UiInvocationContext context,
            Map<String, Object> configuration,
            String usage) {
        String targetField = text(
                configuration.get("targetField"),
                "");
        String value =
                "ENTITY_EVENT:" + usage;
        Map<String, Object> result =
                new LinkedHashMap<>(eventMessage(
                        "ENTITY Provider 已执行字段事件: "
                                + usage,
                        context));
        if (!hasText(targetField)) {
            log.info(
                    "ENTITY 字段事件未配置 targetField，仅返回消息: code={}, usage={}, entityCode={}",
                    CODE,
                    LogValue.safe(usage),
                    LogValue.safe(context.entityCode()));
            return result;
        }
        result.put(
                "effects",
                List.of(Map.of(
                        "type", "FIELD_MAPPING",
                        "data", Map.of(
                                "data", Map.of(
                                        targetField,
                                        value)),
                        "mappings", List.of(Map.of(
                                "targetPath",
                                "data." + targetField,
                                "overwrite",
                                "ALWAYS")))));
        return result;
    }
}
