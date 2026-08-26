package com.workflow.entity.ui.api.request;

import lombok.Data;

/**
 * 关联内容运行时解析请求。
 *
 * <p>请求只携带宿主发布身份、关联内容编码和来源记录标识，禁止提交来源行数据、
 * 目标筛选条件或实体身份。来源记录始终由服务端按当前用户的数据权限重新读取。</p>
 */
@Data
public class UiViewCompositionResolveRequest {

    /** 宿主类型：FORM 或 LIST。 */
    private String ownerType;
    /** 宿主表单或列表 ID。 */
    private String ownerId;
    /** 宿主 UI 发布 ID。 */
    private String releaseId;
    /** 宿主 UI 发布版本号。 */
    private Integer releaseVersion;
    /** 发布快照内稳定的关联内容编码。 */
    private String compositionKey;
    /** 来源记录 ID；首次解析当前激活版本时使用。 */
    private String recordId;
    /**
     * 服务端签发的宿主表单发布解析令牌；列表按钮等已发布入口使用它读取
     * 精确钉定的历史表单版本，客户端不能自行选择历史版本。
     */
    private String releaseResolutionToken;
    /** 服务端签发的短期来源行令牌；历史钉定版本或后续刷新时使用。 */
    private String rowContextToken;
    /**
     * 上一层关联内容返回的短期遍历令牌；仅用于阻断运行时重入和超深导航，
     * 不替代宿主版本、实体或记录权限校验。
     */
    private String traversalContextToken;
}
