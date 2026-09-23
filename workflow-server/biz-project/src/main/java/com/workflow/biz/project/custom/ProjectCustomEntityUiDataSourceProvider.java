package com.workflow.biz.project.custom;

import com.workflow.contracts.entity.ui.context.UiInvocationContext;
import com.workflow.contracts.entity.ui.model.UiDataSourceUsages;
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
 * <p>推荐在接口扩展中配置 {@code scopeType=ENTITY}，scopeId 选择实体 ID。
 * 同一实体下的多个表单和列表都可以复用该接口。示例重点展示字段选项、字段
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

    /** {@code FIELD_OPTIONS} 返回选项的显示文本前缀。 */
    private static final String OPTION_LABEL_PREFIX =
            "optionLabelPrefix";

    /** {@code FIELD_DEFAULT} 分支返回的字段初始值。 */
    private static final String DEFAULT_VALUE =
            "defaultValue";

    /** {@code FIELD_COMPUTE} 拼接当前输入值时使用的结果前缀。 */
    private static final String COMPUTED_PREFIX =
            "computedPrefix";

    /** 加载后或字段事件需要产生字段映射时写入的目标字段编码。 */
    private static final String TARGET_FIELD =
            "targetField";

    /** 这些字段交互事件返回提示，并在配置目标字段后附带字段回填 effect。 */
    private static final Set<String> FIELD_EVENTS =
            Set.of(
                    UiDataSourceUsages.FIELD_CHANGE,
                    UiDataSourceUsages.ENTITY_SELECTED,
                    UiDataSourceUsages.FIELD_BUTTON_CLICK);

    /**
     * 读取编码；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的编码文本，供调用方比较或展示
     */
    @Override
    public String getCode() {
        return CODE;
    }

    /**
     * 读取用户可见名称，供页面和操作日志展示。
     *
     * @return 读取后的展示名称文本，供调用方比较或展示
     */
    @Override
    public String getDisplayName() {
        return "项目自定义统一数据源 [ENTITY/字段]";
    }

    /**
     * 整理配置结构数据，供调用方遍历或继续处理。
     *
     * @return 配置结构键值结果，供调用方继续处理
     */
    @Override
    public Map<String, Object> configurationSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        OPTION_LABEL_PREFIX, Map.of(
                                "type", "string",
                                "title", "字段选项前缀",
                                "default", "实体选项"),
                        DEFAULT_VALUE, Map.of(
                                "type", "string",
                                "title", "字段默认值",
                                "default", "ENTITY_DEFAULT"),
                        COMPUTED_PREFIX, Map.of(
                                "type", "string",
                                "title", "字段计算前缀",
                                "default", "实体计算"),
                        TARGET_FIELD, Map.of(
                                "type", "string",
                                "title", "事件回填字段（可选）",
                                "default", "")));
    }

    /**
     * 生成推荐作用域文本，供后续匹配或展示。
     *
     * @return 处理后的推荐作用域文本，供调用方比较或展示
     */
    @Override
    protected String recommendedScope() {
        return RECOMMENDED_SCOPE;
    }

    /**
     * 判断{@code accepts}上下文条件是否成立，供调用方选择后续分支。
     *
     * @param context 执行上下文，向后续{@code accepts}上下文步骤传递身份、配置或状态
     * @return {@code accepts}上下文条件成立时为 true，否则为 false
     */
    @Override
    protected boolean acceptsContext(
            UiInvocationContext context) {
        return context != null
                && hasText(context.entityCode());
    }

    /**
     * 生成上下文{@code mismatch}原因文本，供后续匹配或展示。
     *
     * @param context 执行上下文，向后续上下文{@code mismatch}原因步骤传递身份、配置或状态
     * @return 处理后的上下文{@code mismatch}原因文本，供调用方比较或展示
     */
    @Override
    protected String contextMismatchReason(
            UiInvocationContext context) {
        return "ENTITY_CODE_REQUIRED";
    }

    /**
     * 执行使用场景，并将结果传给后续步骤。
     *
     * @param context 执行上下文，向后续使用场景步骤传递身份、配置或状态
     * @param configuration 配置内容，决定后续使用场景的处理规则
     * @param input 待执行使用场景的原始输入，结果供调用方继续使用
     * @return 执行后的使用场景结果，供调用方继续处理
     */
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

        if (UiDataSourceUsages.FIELD_OPTIONS.equals(usage)) {
            return options(text(
                    configuration.get(
                            OPTION_LABEL_PREFIX),
                    "实体选项"));
        }
        if (UiDataSourceUsages.FIELD_DEFAULT.equals(usage)) {
            return fieldValue(text(
                    configuration.get(DEFAULT_VALUE),
                    "ENTITY_DEFAULT"));
        }
        if (UiDataSourceUsages.FIELD_COMPUTE.equals(usage)) {
            String prefix = text(
                    configuration.get(COMPUTED_PREFIX),
                    "实体计算");
            return fieldValue(
                    prefix + ":"
                            + text(
                            input.get("value"),
                            context.entityCode()));
        }
        if (UiDataSourceUsages.AFTER_LOAD.equals(usage)) {
            return fieldPatch(
                    text(
                            configuration.get(TARGET_FIELD),
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
     *
     * @param context 执行上下文，向后续字段事件结果步骤传递身份、配置或状态
     * @param configuration 配置内容，决定后续字段事件结果的处理规则
     * @param usage 使用场景，作为 {@code eventMessage} 的输入影响后续处理
     * @return 字段事件结果键值结果，供调用方继续处理
     */
    private Map<String, Object> fieldEventResult(
            UiInvocationContext context,
            Map<String, Object> configuration,
            String usage) {
        String targetField = text(
                configuration.get(TARGET_FIELD),
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
