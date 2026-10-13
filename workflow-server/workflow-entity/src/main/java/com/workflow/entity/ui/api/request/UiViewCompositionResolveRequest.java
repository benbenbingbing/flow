package com.workflow.entity.ui.api.request;

import lombok.Data;
import java.util.List;

/** 关联内容的业务定位信息。客户端不能指定目标实体、版本或固定筛选；每次请求从发布配置逐层重算。 */
@Data
public class UiViewCompositionResolveRequest {
    private String ownerType;
    private String ownerId;
    private String compositionKey;
    private String recordId;
    /** 仅用于发现页面配置变化，不提供权限或选择历史配置。 */
    private String configHash;
    /** 根表单任务、流程或嵌入入口的业务定位值；由对应模块重新鉴权。 */
    private java.util.Map<String,Object> runtimeContext = java.util.Map.of();
    /** 首次打开子关联时携带父路径；服务端验证父目标后追加当前节点。 */
    private UiViewCompositionResolveRequest compositionContext;
    /** 根关联之后的导航路径。每个来源行都必须属于上一层服务端解析出的目标范围。 */
    private List<PathEntry> path = List.of();

    @Data
    public static class PathEntry {
        private String compositionKey;
        private String recordId;
    }
}
