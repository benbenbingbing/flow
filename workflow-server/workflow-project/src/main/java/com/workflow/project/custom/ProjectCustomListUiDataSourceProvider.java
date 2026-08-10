package com.workflow.project.custom;

import com.workflow.contracts.ui.ListInvocationContext;
import com.workflow.contracts.ui.UiInvocationContext;
import com.workflow.core.logging.LogValue;
import com.workflow.core.result.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * LIST 作用范围的统一数据源扩展示例。
 *
 * <p>推荐在接口服务中配置 {@code scopeType=LIST}，scopeId 填写列表配置 ID。
 * 示例覆盖 LIST_QUERY、LIST_COLUMN、列表加载/导出、数据动作，以及工具栏按钮
 * 和行按钮事件。</p>
 */
@Slf4j
@Component
public class ProjectCustomListUiDataSourceProvider
        extends ProjectCustomUiDataSourceProviderSupport {

    public static final String CODE =
            "PROJECT_CUSTOM_UI_LIST";
    public static final String RECOMMENDED_SCOPE =
            "LIST";
    private static final Set<String> LIST_EVENTS =
            Set.of(
                    "LIST_LOAD",
                    "LIST_EXPORT",
                    "DETAIL_LOAD",
                    "DATA_CREATE",
                    "DATA_UPDATE",
                    "DATA_DELETE",
                    "DATA_BATCH_DELETE",
                    "TOOLBAR_BUTTON_CLICK",
                    "ROW_BUTTON_CLICK");

    @Override
    public String getCode() {
        return CODE;
    }

    @Override
    public String getDisplayName() {
        return "项目自定义统一数据源 [LIST]";
    }

    @Override
    public Map<String, Object> configurationSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "columnPrefix", Map.of(
                                "type", "string",
                                "title", "虚拟列前缀",
                                "default", "列表统一列"),
                        "messagePrefix", Map.of(
                                "type", "string",
                                "title", "列表事件前缀",
                                "default", "列表统一数据源"),
                        "pageNum", Map.of(
                                "type", "integer",
                                "title", "空分页页码",
                                "default", 1),
                        "pageSize", Map.of(
                                "type", "integer",
                                "title", "空分页大小",
                                "default", 20)));
    }

    @Override
    protected String recommendedScope() {
        return RECOMMENDED_SCOPE;
    }

    @Override
    protected boolean acceptsContext(
            UiInvocationContext context) {
        return context instanceof ListInvocationContext;
    }

    @Override
    protected String contextMismatchReason(
            UiInvocationContext context) {
        return "LIST_CONFIG_REQUIRED";
    }

    @Override
    protected Object executeUsage(
            UiInvocationContext context,
            Map<String, Object> configuration,
            Map<String, Object> input) {
        String usage = usage(context);
        String fieldCode =
                fieldCode(input.get("field"));
        log.info(
                "LIST 统一数据源处理业务分支: code={}, usage={}, listConfigId={}, listKey={}, entityCode={}, releaseId={}, releaseVersion={}, fieldCode={}, recordCount={}, filterKeys={}",
                CODE,
                LogValue.safe(usage),
                LogValue.safe(context.configId()),
                LogValue.safe(context.listKey()),
                LogValue.safe(context.entityCode()),
                LogValue.safe(context.releaseId()),
                context.releaseVersion(),
                LogValue.safe(fieldCode),
                listValue(input.get("records")).size(),
                input.get("filters")
                        instanceof Map<?, ?> filters
                        ? filters.keySet() : List.of());

        if ("LIST_QUERY".equals(usage)) {
            int pageNum = Math.max(
                    1,
                    integer(
                            configuration.get("pageNum"),
                            1));
            int pageSize = Math.max(
                    1,
                    integer(
                            configuration.get("pageSize"),
                            20));
            log.info(
                    "LIST 统一数据源查询分支无真实业务查询，返回空分页: code={}, listKey={}, pageNum={}, pageSize={}",
                    CODE,
                    LogValue.safe(context.listKey()),
                    pageNum,
                    pageSize);
            return new PageResult<>(
                    List.of(),
                    0,
                    pageNum,
                    pageSize);
        }
        if ("LIST_COLUMN".equals(usage)) {
            return columnValues(
                    input,
                    text(
                            configuration.get(
                                    "columnPrefix"),
                            "列表统一列"));
        }
        if (LIST_EVENTS.contains(usage)) {
            return eventMessage(
                    text(
                            configuration.get(
                                    "messagePrefix"),
                            "列表统一数据源")
                            + "已执行事件: " + usage,
                    context);
        }
        return diagnosticResult(
                context,
                configuration,
                input);
    }
}
