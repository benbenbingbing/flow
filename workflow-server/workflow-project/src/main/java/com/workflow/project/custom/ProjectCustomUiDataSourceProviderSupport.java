package com.workflow.project.custom;

import com.workflow.contracts.entity.list.DataScopePlan;
import com.workflow.contracts.ui.UiDataSourceProvider;
import com.workflow.contracts.ui.UiDataSourceUsages;
import com.workflow.contracts.ui.UiInvocationContext;
import com.workflow.core.logging.LogValue;
import com.workflow.core.result.PageResult;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 项目统一数据源示例的公共执行骨架。
 *
 * <p>需要特别区分两个概念：</p>
 * <ul>
 *     <li>GLOBAL、ENTITY、FORM、LIST 是接口服务定义中的作用范围，由平台在
 *     Provider 执行前完成发布绑定和权限校验。</li>
 *     <li>LIST_COLUMN、FORM_INIT、FIELD_OPTIONS、ROW_BUTTON_CLICK 等是本次
 *     调用的 usage，Provider 根据 usage 决定输入和输出结构。</li>
 * </ul>
 *
 * <p>Provider 接口本身不会收到接口服务定义的 scopeType，因此这里的
 * recommendedScope 只用于示例说明和日志定位，不能替代平台授权。</p>
 */
abstract class ProjectCustomUiDataSourceProviderSupport
        implements UiDataSourceProvider {

    /** 接口服务操作标识，只参与执行日志和诊断结果，不改变 usage 路由。 */
    private static final String OPERATION = "operation";

    /**
     * 返回该 Provider 推荐配置的接口服务作用范围。
     */
    protected abstract String recommendedScope();

    /**
     * 执行具体 usage 的业务示例。
     */
    protected abstract Object executeUsage(
            UiInvocationContext context,
            Map<String, Object> configuration,
            Map<String, Object> input);

    /**
     * 对运行时来源做额外的示例级检查。
     *
     * <p>真正的 scopeType/scopeId 校验已经由平台完成；这里仅用于帮助开发者
     * 尽早发现把 FORM Provider 绑定到 LIST、或把 LIST Provider 绑定到 FORM
     * 之类的配置错误。</p>
     */
    protected boolean acceptsContext(
            UiInvocationContext context) {
        return true;
    }

    /**
     * 返回上下文不匹配时的可读原因。
     */
    protected String contextMismatchReason(
            UiInvocationContext context) {
        return "CONTEXT_NOT_SUPPORTED";
    }

    @Override
    public final Object execute(
            UiInvocationContext context,
            DataScopePlan dataScopePlan,
            Map<String, Object> configuration,
            Map<String, Object> input) {
        Logger log = LoggerFactory.getLogger(getClass());
        long startedAt = System.nanoTime();
        Map<String, Object> safeConfiguration =
                safeMap(configuration);
        Map<String, Object> safeInput =
                safeMap(input);
        String usage = usage(context);
        boolean allowed =
                dataScopePlan != null && dataScopePlan.allowed();
        int recordCount = listValue(
                safeInput.get("records")).size();

        log.info(
                "项目统一数据源开始执行: providerCode={}, providerName={}, recommendedScope={}, usage={}, operation={}, configType={}, configId={}, releaseId={}, releaseVersion={}, entityCode={}, listKey={}, userId={}, allowed={}, recordCount={}, configurationKeys={}, inputKeys={}, runtimeContextKeys={}",
                LogValue.safe(getCode()),
                LogValue.safe(getDisplayName()),
                LogValue.safe(recommendedScope()),
                LogValue.safe(usage),
                LogValue.safe(text(
                        safeConfiguration.get(OPERATION),
                        "default")),
                LogValue.safe(context == null
                        ? null : context.configType()),
                LogValue.safe(context == null
                        ? null : context.configId()),
                LogValue.safe(context == null
                        ? null : context.releaseId()),
                context == null
                        ? null : context.releaseVersion(),
                LogValue.safe(context == null
                        ? null : context.entityCode()),
                LogValue.safe(context == null
                        ? null : context.listKey()),
                LogValue.safe(context == null
                        ? null : context.userId()),
                allowed,
                recordCount,
                safeConfiguration.keySet(),
                safeInput.keySet(),
                List.of());

        if (!allowed) {
            Object result = emptyResult(usage);
            log.info(
                    "项目统一数据源结束执行: providerCode={}, recommendedScope={}, usage={}, resultType={}, resultCount={}, reason=DATA_SCOPE_DENIED, durationMs={}",
                    LogValue.safe(getCode()),
                    LogValue.safe(recommendedScope()),
                    LogValue.safe(usage),
                    resultType(result),
                    resultCount(result),
                    durationMs(startedAt));
            return result;
        }
        if (!acceptsContext(context)) {
            Object result = emptyResult(usage);
            log.info(
                    "项目统一数据源结束执行: providerCode={}, recommendedScope={}, usage={}, configType={}, configId={}, resultType={}, resultCount={}, reason={}, durationMs={}",
                    LogValue.safe(getCode()),
                    LogValue.safe(recommendedScope()),
                    LogValue.safe(usage),
                    LogValue.safe(context == null
                            ? null : context.configType()),
                    LogValue.safe(context == null
                            ? null : context.configId()),
                    resultType(result),
                    resultCount(result),
                    LogValue.safe(contextMismatchReason(context)),
                    durationMs(startedAt));
            return result;
        }

        try {
            Object result = executeUsage(
                    context,
                    safeConfiguration,
                    safeInput);
            log.info(
                    "项目统一数据源结束执行: providerCode={}, recommendedScope={}, usage={}, resultType={}, resultCount={}, durationMs={}",
                    LogValue.safe(getCode()),
                    LogValue.safe(recommendedScope()),
                    LogValue.safe(usage),
                    resultType(result),
                    resultCount(result),
                    durationMs(startedAt));
            return result;
        } catch (RuntimeException exception) {
            log.error(
                    "项目统一数据源执行失败: providerCode={}, recommendedScope={}, usage={}, configType={}, configId={}, entityCode={}, listKey={}, failureType={}, durationMs={}",
                    LogValue.safe(getCode()),
                    LogValue.safe(recommendedScope()),
                    LogValue.safe(usage),
                    LogValue.safe(context == null
                            ? null : context.configType()),
                    LogValue.safe(context == null
                            ? null : context.configId()),
                    LogValue.safe(context == null
                            ? null : context.entityCode()),
                    LogValue.safe(context == null
                            ? null : context.listKey()),
                    LogValue.failureType(exception),
                    durationMs(startedAt),
                    exception);
            throw exception;
        }
    }

    /**
     * 返回各类 usage 对应的安全空结果，避免日志示例伪造业务数据。
     */
    protected Object emptyResult(
            String usage) {
        return switch (normalize(usage)) {
            case UiDataSourceUsages.LIST_QUERY ->
                    new PageResult<>(List.of(), 0, 1, 20);
            case UiDataSourceUsages.FIELD_OPTIONS,
                    UiDataSourceUsages.SUBFORM_ROWS ->
                    List.of();
            default -> Map.of();
        };
    }

    /**
     * 构造列表虚拟列要求的“记录 ID -> 列值”结果。
     */
    protected Map<String, Object> columnValues(
            Map<String, Object> input,
            String valuePrefix) {
        Map<String, Object> result =
                new LinkedHashMap<>();
        for (Object record :
                listValue(input.get("records"))) {
            String recordId =
                    recordValue(record, "id");
            if (!hasText(recordId)) {
                continue;
            }
            String identity =
                    recordValue(record, "dataNo");
            result.put(
                    recordId,
                    valuePrefix + ":"
                            + (hasText(identity)
                            ? identity : recordId));
        }
        return result;
    }

    /**
     * 构造表单初始化、加载后处理、提交前处理使用的字段补丁。
     */
    protected Map<String, Object> fieldPatch(
            String targetField,
            Object value) {
        if (!hasText(targetField)) {
            return Map.of();
        }
        Map<String, Object> result =
                new LinkedHashMap<>();
        result.put(targetField, value);
        return result;
    }

    /**
     * 构造字段默认值或字段计算结果。
     *
     * <p>前端同时兼容直接标量和 {@code {"value": ...}}；示例统一返回对象，
     * 便于在接口服务中定义稳定的输出 Schema。</p>
     */
    protected Map<String, Object> fieldValue(
            Object value) {
        Map<String, Object> result =
                new LinkedHashMap<>();
        result.put("value", value);
        return result;
    }

    /**
     * 构造 UI 事件链可识别的消息结果。
     */
    protected Map<String, Object> eventMessage(
            String message,
            UiInvocationContext context) {
        Map<String, Object> data =
                new LinkedHashMap<>();
        data.put("providerCode", getCode());
        data.put("usage", usage(context));
        data.put("entityCode", context == null
                ? null : context.entityCode());
        data.put("configId", context == null
                ? null : context.configId());

        Map<String, Object> result =
                new LinkedHashMap<>();
        result.put("message", message);
        result.put("data", data);
        return result;
    }

    /**
     * 构造接口调试入口使用的诊断结果，仅返回上下文元数据，不回显业务数据。
     */
    protected Map<String, Object> diagnosticResult(
            UiInvocationContext context,
            Map<String, Object> configuration,
            Map<String, Object> input) {
        Map<String, Object> result =
                new LinkedHashMap<>();
        result.put("providerCode", getCode());
        result.put("recommendedScope",
                recommendedScope());
        result.put("usage", usage(context));
        result.put("operation", text(
                configuration.get(OPERATION),
                "default"));
        result.put("configType", context == null
                ? null : context.configType());
        result.put("configId", context == null
                ? null : context.configId());
        result.put("entityCode", context == null
                ? null : context.entityCode());
        result.put("listKey", context == null
                ? null : context.listKey());
        result.put("configurationKeys",
                configuration.keySet());
        result.put("inputKeys", input.keySet());
        return result;
    }

    protected List<Map<String, Object>> options(
            String labelPrefix) {
        return List.of(
                Map.of(
                        "label", labelPrefix + " A",
                        "value", "A"),
                Map.of(
                        "label", labelPrefix + " B",
                        "value", "B"));
    }

    protected String fieldCode(
            Object field) {
        if (field instanceof EntityListField value) {
            return value.getFieldCode();
        }
        if (field instanceof Map<?, ?> values) {
            return text(
                    values.get("fieldCode"),
                    text(values.get("fieldKey"), null));
        }
        return null;
    }

    protected String recordValue(
            Object record,
            String key) {
        if (record instanceof EntityDataDTO value) {
            String direct = switch (key) {
                case "id" -> value.getId();
                case "dataNo" -> value.getDataNo();
                case "title" -> value.getTitle();
                case "name" -> value.getName();
                case "code" -> value.getCode();
                default -> null;
            };
            if (hasText(direct)) {
                return direct;
            }
            Object nested = value.getData() == null
                    ? null : value.getData().get(key);
            return text(nested, null);
        }
        if (record instanceof Map<?, ?> values) {
            Object direct = values.get(key);
            if (direct != null) {
                return text(direct, null);
            }
            if (values.get("data")
                    instanceof Map<?, ?> data) {
                return text(data.get(key), null);
            }
        }
        return null;
    }

    protected Map<String, Object> safeMap(
            Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }

    protected List<?> listValue(
            Object value) {
        return value instanceof List<?> list
                ? list : List.of();
    }

    protected String usage(
            UiInvocationContext context) {
        return normalize(context == null
                ? null : context.usage());
    }

    protected String normalize(
            String value) {
        return hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT)
                : "";
    }

    protected boolean hasText(
            Object value) {
        return value != null
                && !String.valueOf(value).isBlank();
    }

    protected String text(
            Object value,
            String fallback) {
        return hasText(value)
                ? String.valueOf(value)
                : fallback;
    }

    protected int integer(
            Object value,
            int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return hasText(value)
                    ? Integer.parseInt(String.valueOf(value))
                    : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String resultType(
            Object result) {
        return result == null
                ? "null"
                : result.getClass().getSimpleName();
    }

    private int resultCount(
            Object result) {
        if (result == null) {
            return 0;
        }
        if (result instanceof PageResult<?> page) {
            return page.getRecords() == null
                    ? 0 : page.getRecords().size();
        }
        if (result instanceof Map<?, ?> map) {
            return map.size();
        }
        if (result instanceof Collection<?> collection) {
            return collection.size();
        }
        return 1;
    }

    private long durationMs(
            long startedAt) {
        return (System.nanoTime() - startedAt)
                / 1_000_000;
    }
}
