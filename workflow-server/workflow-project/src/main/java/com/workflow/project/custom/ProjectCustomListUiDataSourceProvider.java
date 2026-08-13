package com.workflow.project.custom;

import com.workflow.contracts.ui.ListInvocationContext;
import com.workflow.contracts.ui.UiDataSourceUsages;
import com.workflow.contracts.ui.UiInvocationContext;
import com.workflow.core.logging.LogValue;
import com.workflow.core.result.PageResult;
import com.workflow.entity.data.api.response.EntityDataDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
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

    /** {@code LIST_COLUMN} 为每条记录生成虚拟列值时使用的文本前缀。 */
    private static final String COLUMN_PREFIX =
            "columnPrefix";

    /** 列表事件执行成功后返回给页面的提示文本前缀。 */
    private static final String MESSAGE_PREFIX =
            "messagePrefix";

    /**
     * 示例 {@code LIST_QUERY} 返回结果使用的页码配置。
     *
     * <p>这是 Provider 自身的演示配置，不是平台查询 Map 中的同名输入字段。</p>
     */
    private static final String PAGE_NUM =
            "pageNum";

    /** 示例 {@code LIST_QUERY} 返回结果使用的每页条数配置。 */
    private static final String PAGE_SIZE =
            "pageSize";

    /** 由该示例统一转换为页面提示消息的列表标准事件。 */
    private static final Set<String> LIST_EVENTS =
            Set.of(
                    UiDataSourceUsages.LIST_LOAD,
                    UiDataSourceUsages.LIST_EXPORT,
                    UiDataSourceUsages.DETAIL_LOAD,
                    UiDataSourceUsages.DATA_CREATE,
                    UiDataSourceUsages.DATA_UPDATE,
                    UiDataSourceUsages.DATA_DELETE,
                    UiDataSourceUsages.DATA_BATCH_DELETE,
                    UiDataSourceUsages.TOOLBAR_BUTTON_CLICK,
                    UiDataSourceUsages.ROW_BUTTON_CLICK);

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
                        COLUMN_PREFIX, Map.of(
                                "type", "string",
                                "title", "虚拟列前缀",
                                "default", "列表统一列"),
                        MESSAGE_PREFIX, Map.of(
                                "type", "string",
                                "title", "列表事件前缀",
                                "default", "列表统一数据源"),
                        PAGE_NUM, Map.of(
                                "type", "integer",
                                "title", "空分页页码",
                                "default", 1),
                        PAGE_SIZE, Map.of(
                                "type", "integer",
                                "title", "演示分页大小",
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

        if (UiDataSourceUsages.LIST_QUERY.equals(usage)) {
            int pageNum = Math.max(
                    1,
                    integer(
                            configuration.get(PAGE_NUM),
                            1));
            int pageSize = Math.max(
                    1,
                    integer(
                            configuration.get(PAGE_SIZE),
                            20));
            EntityDataDTO sample =
                    querySample(context);
            List<EntityDataDTO> records =
                    pageNum == 1
                            ? List.of(sample)
                            : List.of();
            log.info(
                    "LIST 统一数据源查询分支返回验收记录: code={}, listKey={}, pageNum={}, pageSize={}, recordId={}, recordCount={}, total=1",
                    CODE,
                    LogValue.safe(context.listKey()),
                    pageNum,
                    pageSize,
                    LogValue.safe(sample.getId()),
                    records.size());
            return new PageResult<>(
                    records,
                    1,
                    pageNum,
                    pageSize);
        }
        if (UiDataSourceUsages.LIST_COLUMN.equals(usage)) {
            return columnValues(
                    input,
                    text(
                            configuration.get(
                                    COLUMN_PREFIX),
                            "列表统一列"));
        }
        if (LIST_EVENTS.contains(usage)) {
            return eventMessage(
                    text(
                            configuration.get(
                                    MESSAGE_PREFIX),
                            "列表统一数据源")
                            + "已执行事件: " + usage,
                    context);
        }
        return diagnosticResult(
                context,
                configuration,
                input);
    }

    private EntityDataDTO querySample(
            UiInvocationContext context) {
        EntityDataDTO result = new EntityDataDTO();
        result.setId("PROJECT-UI-LIST-SAMPLE");
        result.setEntityCode(context == null
                ? "project_extension_acceptance"
                : context.entityCode());
        result.setEntityName("项目扩展验收单");
        result.setDataNo("EXT-UI-LIST-001");
        result.setCode("EXT-UI-LIST-001");
        result.setName("LIST 统一数据源演示记录");
        result.setTitle("LIST 统一数据源演示记录");
        result.setStatus("DRAFT");
        result.setCreatedAt(LocalDateTime.now());
        Map<String, Object> data =
                new LinkedHashMap<>();
        data.put("name", result.getName());
        data.put("acceptance_scene",
                "LIST_UNIFIED_DATA_SOURCE");
        data.put("acceptance_score", 92);
        data.put("owner_name", "project 模块");
        data.put("provider_trace",
                "PROJECT_CUSTOM_UI_LIST/LIST_QUERY 已执行");
        data.put("extension_result",
                "该记录由 LIST 作用范围统一数据源返回。");
        result.setData(data);
        result.setExtData(Map.of(
                "provider_column",
                "LIST 统一数据源"));
        return result;
    }
}
