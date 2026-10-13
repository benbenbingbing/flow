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
    private String configHash;
    private Map<String, Object> runtimeContext;
    /** 仅供已受理的内部事件链复用快照；JSON 不能指定历史配置。 */
    @JsonIgnore
    private String releaseId;
    @JsonIgnore
    private Integer releaseVersion;
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

    /** 列表入口已验证发布坐标后设置；客户端不能凭 releaseId 请求历史事件链。 */
    @JsonIgnore
    private boolean serverPinnedRelease;

    /** 来源只授予流程读取能力时，任何写接口都必须在产生副作用前拒绝。 */
    @JsonIgnore
    private boolean serverReadOnly;

    /** 列表入口合并的固定条件与可信关联条件，每个事件步骤执行时重新应用。 */
    @JsonIgnore
    private Map<String, Object> serverListFilters;

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

    /** 服务端已核实的字段事件记录，嵌套表单必须先通过实际父子关系校验。 */
    @JsonIgnore
    private String serverRecordId;

    /** 服务端从已鉴权记录/任务核验出的流程实例 ID。 */
    @JsonIgnore
    private String serverProcessInstanceId;
    /** 顶层任务/记录坐标与嵌套来源一起交由后端核实，不从客户端接收授权结论。 */
    public java.util.Map<String, Object> getRuntimeContext() {
        var result = new java.util.LinkedHashMap<String, Object>(runtimeContext == null ? java.util.Map.of() : runtimeContext);
        if (taskId != null && !taskId.isBlank()) result.put("taskId", taskId);
        if (recordId != null && !recordId.isBlank()) {
            if (result.containsKey("rootFormId") && result.get("componentPath") instanceof java.util.List<?> path && !path.isEmpty())
                result.put("childRecordId", recordId);
            else result.put("recordId", recordId);
        }
        if (entityCode != null && !entityCode.isBlank()) result.put("entityCode", entityCode);
        return result;
    }
}
