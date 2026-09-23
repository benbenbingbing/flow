package com.workflow.biz.project.custom;

import com.workflow.contracts.entity.ui.context.FormInvocationContext;
import com.workflow.contracts.entity.ui.model.UiDataSourceUsages;
import com.workflow.contracts.entity.ui.context.UiInvocationContext;
import com.workflow.core.logging.LogValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * FORM 作用范围的统一数据源扩展示例。
 *
 * <p>推荐在接口扩展中配置 {@code scopeType=FORM}，scopeId 填写表单 ID。
 * 示例覆盖表单初始化、加载后处理、提交前处理、表单内字段数据源、子表行加载
 * 和表单/字段按钮事件。</p>
 */
@Slf4j
@Component
public class ProjectCustomFormUiDataSourceProvider
        extends ProjectCustomUiDataSourceProviderSupport {

    public static final String CODE =
            "PROJECT_CUSTOM_UI_FORM";
    public static final String RECOMMENDED_SCOPE =
            "FORM";

    /** 表单回填值、字段计算值和事件提示消息共用的文本前缀。 */
    private static final String MESSAGE_PREFIX =
            "messagePrefix";

    /** 表单初始化、加载后和提交前分支需要回填的字段编码；空值表示不回填。 */
    private static final String TARGET_FIELD =
            "targetField";

    /** {@code FIELD_DEFAULT} 分支返回的字段初始值。 */
    private static final String DEFAULT_VALUE =
            "defaultValue";

    /** {@code FIELD_OPTIONS} 返回选项的显示文本前缀。 */
    private static final String OPTION_LABEL_PREFIX =
            "optionLabelPrefix";

    /** 返回字段 patch 的表单生命周期阶段。 */
    private static final Set<String> PATCH_USAGES =
            Set.of(
                    UiDataSourceUsages.FORM_INIT,
                    UiDataSourceUsages.AFTER_LOAD,
                    UiDataSourceUsages.BEFORE_SUBMIT);

    /** 由该示例统一转换为页面提示消息的表单内标准事件。 */
    private static final Set<String> FORM_EVENTS =
            Set.of(
                    UiDataSourceUsages.DETAIL_LOAD,
                    UiDataSourceUsages.DATA_CREATE,
                    UiDataSourceUsages.DATA_UPDATE,
                    UiDataSourceUsages.FORM_OPEN,
                    UiDataSourceUsages.FORM_SAVE,
                    UiDataSourceUsages.FORM_RESET,
                    UiDataSourceUsages.FIELD_CHANGE,
                    UiDataSourceUsages.ENTITY_SELECTED,
                    UiDataSourceUsages.FIELD_BUTTON_CLICK,
                    UiDataSourceUsages.SUBFORM_LOAD,
                    UiDataSourceUsages.SUBFORM_SAVE,
                    UiDataSourceUsages.FORM_BUTTON_CLICK);

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
        return "项目自定义统一数据源 [FORM]";
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
                        MESSAGE_PREFIX, Map.of(
                                "type", "string",
                                "title", "表单演示前缀",
                                "default", "表单统一数据源"),
                        TARGET_FIELD, Map.of(
                                "type", "string",
                                "title", "表单回填字段（可选）",
                                "default", ""),
                        DEFAULT_VALUE, Map.of(
                                "type", "string",
                                "title", "字段默认值",
                                "default", "FORM_DEFAULT"),
                        OPTION_LABEL_PREFIX, Map.of(
                                "type", "string",
                                "title", "字段选项前缀",
                                "default", "表单选项")));
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
        return context instanceof FormInvocationContext;
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
        return "FORM_CONFIG_REQUIRED";
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
        String prefix = text(
                configuration.get(MESSAGE_PREFIX),
                "表单统一数据源");
        String targetField = text(
                configuration.get(TARGET_FIELD),
                "");
        String mode = context instanceof FormInvocationContext formContext
                ? text(formContext.mode(), "unknown")
                : "unknown";
        log.info(
                "FORM 统一数据源处理业务分支: code={}, usage={}, formId={}, entityCode={}, releaseId={}, releaseVersion={}, mode={}, targetField={}, inputKeys={}",
                CODE,
                LogValue.safe(usage),
                LogValue.safe(context.configId()),
                LogValue.safe(context.entityCode()),
                LogValue.safe(context.releaseId()),
                context.releaseVersion(),
                LogValue.safe(mode),
                LogValue.safe(targetField),
                input.keySet());
        if (PATCH_USAGES.contains(usage)
                && !hasText(targetField)) {
            log.info(
                    "FORM 统一数据源未配置 targetField，仅记录执行日志: code={}, usage={}, formId={}",
                    CODE,
                    LogValue.safe(usage),
                    LogValue.safe(context.configId()));
        }

        return switch (usage) {
            case UiDataSourceUsages.FORM_INIT,
                    UiDataSourceUsages.AFTER_LOAD,
                    UiDataSourceUsages.BEFORE_SUBMIT ->
                    fieldPatch(
                            targetField,
                            prefix + ":" + usage
                                    + ":" + mode);
            case UiDataSourceUsages.FIELD_OPTIONS ->
                    options(text(
                            configuration.get(
                                    OPTION_LABEL_PREFIX),
                            "表单选项"));
            case UiDataSourceUsages.FIELD_DEFAULT ->
                    fieldValue(text(
                            configuration.get(
                                    DEFAULT_VALUE),
                            "FORM_DEFAULT"));
            case UiDataSourceUsages.FIELD_COMPUTE ->
                    fieldValue(
                            prefix + ":COMPUTED:"
                                    + text(
                                    input.get("value"),
                                    "EMPTY"));
            case UiDataSourceUsages.SUBFORM_ROWS -> {
                log.info(
                        "FORM 统一数据源子表分支无业务场景，返回空行集合: code={}, formId={}, fieldCode={}",
                        CODE,
                        LogValue.safe(context.configId()),
                        LogValue.safe(text(
                                input.get("fieldCode"),
                                null)));
                yield List.of();
            }
            default -> FORM_EVENTS.contains(usage)
                    ? eventMessage(
                    prefix + "已执行事件: "
                            + usage,
                    context)
                    : diagnosticResult(
                    context,
                    configuration,
                    input);
        };
    }
}
