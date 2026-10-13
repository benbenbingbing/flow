package com.workflow.entity.ui.api.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/** 关联内容解析后的可信运行时描述。 */
@Value
@Builder(toBuilder = true)
public class UiViewCompositionResolveResponse {

    String ownerType;
    String ownerId;
    String releaseId;
    Integer releaseVersion;
    String compositionKey;

    String sourceEntityCode;
    String sourceRecordId;

    String targetEntityId;
    String targetEntityCode;
    String targetContentType;
    String targetContentId;
    String targetContentKey;
    String targetReleaseId;
    Integer targetReleaseVersion;
    String targetContentHash;

    /** FORM 目标解析出的唯一记录 ID；未匹配时为空。 */
    String targetRecordId;
    /** LIST 目标的服务端可信固定条件；来源行原始数据不会返回。 */
    @Builder.Default
    Map<String, Object> fixedFilters = Map.of();
    /** 缺少关联值或未匹配记录时为 true，调用方不得退化为无条件查询。 */
    boolean matchNone;

    /** 发布快照中的展示配置。 */
    @Builder.Default
    Map<String, Object> presentation = Map.of();
    /** 发布快照中的允许动作。 */
    @Builder.Default
    List<String> actions = List.of();
    /** 特殊处理失败时的安全展示策略。 */
    String failurePolicy;

    /** 普通业务定位路径，不携带签名、有效期或授权结论。 */
    com.workflow.entity.ui.api.request.UiViewCompositionResolveRequest compositionContext;
    /** 已解析宿主配置摘要，用于写入前检查配置变化。 */
    String configHash;
    /** 沿路径继承的只读限制，由服务端实时计算。 */
    boolean readonlyContext;
}
