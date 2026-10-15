package com.workflow.entity.list.api.request;

import com.workflow.entity.list.api.response.EntityListRuntimeContextDTO;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 实体列表数据查询请求。
 */
@Data
public class EntityListQueryRequest {
    /** 页码，默认 1 */
    private long pageNum = 1;
    /** 每页大小，默认 10 */
    private long pageSize = 10;
    /** 用户点击表头选择的实体字段编码；为空时恢复列表发布配置中的默认排序。 */
    private String sortField;
    /** 与 sortField 配套的 asc/desc；只允许发布配置中明确启用排序的展示列。 */
    private String sortDirection;
    /** 场景（PAGE/EXPORT 等），默认 PAGE */
    private String scene = "PAGE";
    /** 页面配置摘要用于变更提示，不能指定历史发布版本。 */
    private String configHash;
    private Map<String, Object> runtimeContext = Map.of();
    /** 关联范围每次都从已发布关系与来源记录重新计算。 */
    private com.workflow.entity.ui.api.request.UiViewCompositionResolveRequest compositionContext;
    /** LINK/UNLINK 候选查询仍须核验对应按钮能力，不能凭此字段扩大范围。 */
    private String candidateAction;
    /** 查询过滤条件 */
    private Map<String, Object> filters = new LinkedHashMap<>();
    /** 列表运行时上下文（关联来源、参数等） */
    private EntityListRuntimeContextDTO context;
}
