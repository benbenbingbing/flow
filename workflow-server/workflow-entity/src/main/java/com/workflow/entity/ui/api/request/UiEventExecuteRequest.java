package com.workflow.entity.ui.api.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * UI 事件运行请求。
 *
 * <p>客户端只声明事件来源与业务输入，实际接口服务和操作由服务端从已发布
 * 配置中解析，不能由客户端任意指定。</p>
 */
@Data
public class UiEventExecuteRequest {

    private String eventCode;
    private String configType;
    private String configId;
    private String releaseId;
    private Integer releaseVersion;
    private String releaseResolutionToken;
    private String entityCode;
    private String listKey;
    private String targetType;
    private String targetKey;
    /**
     * 客户端为一次用户动作生成的稳定请求标识。
     *
     * <p>仅作为服务端生成可信幂等键的种子，不能直接下传 Provider，
     * 也不能代替租户、用户、发布版本和按钮身份等服务端边界。</p>
     */
    private String requestId;
    private String recordId;
    /** 审批模式的未受信任务坐标；服务端必须重新绑定当前可办理待办。 */
    private String taskId;
    private List<String> selectedIds;
    private Object selection;
    private Map<String, Object> input;
    private Map<String, Object> context;

    @JsonIgnore
    private boolean preview;

    @JsonIgnore
    private String serverIdempotencyKey;

    /** 表单按钮鉴权后确认的模式，仅供同一次内部事件链使用。 */
    @JsonIgnore
    private String serverAuthorizedMode;

    /**
     * 从已验证发布快照取得的按钮描述。客户端同名 input.button 不得写入该字段，
     * 事件条件和映射只使用这里的服务端副本。
     */
    @JsonIgnore
    private Map<String, Object> serverPublishedButton;

    /** 服务端核验后的活动任务 ID，仅供同一次内部事件链使用。 */
    @JsonIgnore
    private String serverTaskId;

    /** 服务端从已鉴权记录/任务核验出的流程实例 ID。 */
    @JsonIgnore
    private String serverProcessInstanceId;
}
