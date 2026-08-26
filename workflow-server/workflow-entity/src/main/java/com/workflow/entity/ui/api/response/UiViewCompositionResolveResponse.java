package com.workflow.entity.ui.api.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/** 关联内容解析后的可信运行时描述。 */
@Value
@Builder
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

    /** 重新解析同一来源行时可使用的短期签名令牌。 */
    String rowContextToken;
    /**
     * 执行权威 SELECT/LINK/UNLINK 时提交的动作上下文。
     * 当前与 rowContextToken 同值，独立命名避免前端把整行数据当作动作依据。
     */
    String actionContextToken;
    /** FORM 目标精确钉定发布版本的解析令牌。 */
    String targetReleaseResolutionToken;
    /** LIST 条件的短期签名凭证，供后续可信列表查询链路消费。 */
    String listContextToken;
    /** 已追加当前节点的短期遍历令牌，供嵌套关联内容继续执行循环和深度保护。 */
    String traversalContextToken;
}
