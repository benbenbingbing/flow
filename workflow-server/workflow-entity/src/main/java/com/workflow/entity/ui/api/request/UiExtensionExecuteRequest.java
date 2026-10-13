package com.workflow.entity.ui.api.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.Map;

/**
 * 接口扩展内部执行请求。
 *
 * <p>公开运行接口先把可验证的绑定声明转换为此对象；客户端不能通过公开接口
 * 直接提交 entityCode、listKey、release 或任意可信上下文。</p>
 */
@Data
public class UiExtensionExecuteRequest {

    /** 绑定位置或事件编码，例如 FIELD_OPTIONS、LIST_COLUMN。 */
    private String usage;
    /** Provider 内部路由编码，仅由服务端从扩展定义或历史快照设置。 */
    @JsonIgnore
    private String providerOperationCode;
    /** 服务端确认的绑定所有者类型：FORM、LIST 或 ENTITY。 */
    private String configType;
    /** 服务端确认的绑定所有者 ID。 */
    private String configId;
    /** 服务端确认或校验过的 UI 配置发布 ID。 */
    private String releaseId;
    /** 服务端确认或校验过的 UI 配置发布版本。 */
    private Integer releaseVersion;
    /** 服务端内部链路声明的实体编码，公开绑定执行不会采信客户端值。 */
    private String entityCode;
    /** 服务端内部链路声明的列表编码。 */
    private String listKey;
    /** 表单值、筛选条件、记录等业务输入；所有字段均不构成服务端认证身份。 */
    private Map<String, Object> input;
    /** 精确绑定目标类型，例如 OWNER、FIELD、COLUMN 或 BUTTON。 */
    private String targetType;
    /** 精确绑定目标的字段编码、节点编码或按钮编码。 */
    private String targetKey;
    /** 仅供服务端内部事件链传递的非身份扩展参数。 */
    private Map<String, Object> context;
    /** 列表调用页码。 */
    private Integer pageNum;
    /** 列表调用每页条数。 */
    private Integer pageSize;

    /** 服务端生成的幂等键，不序列化给前端。 */
    @JsonIgnore
    private String serverIdempotencyKey;

    /** 是否固定使用服务端指定的发布版本，不序列化给前端。 */
    @JsonIgnore
    private boolean serverPinnedRelease;

    /** 来源只授予流程读取能力时，任何写接口都必须在产生副作用前拒绝。 */
    @JsonIgnore
    private boolean serverReadOnly;

    /** 已验证事件步骤的原始绑定所有者类型，仅供内部精确授权。 */
    @JsonIgnore
    private String serverBindingOwnerType;

    /** 已验证事件步骤的原始绑定所有者 ID，仅供内部精确授权。 */
    @JsonIgnore
    private String serverBindingOwnerId;

    /** 已验证事件步骤的原始绑定目标类型，仅供内部精确授权。 */
    @JsonIgnore
    private String serverBindingTargetType;

    /** 已验证事件步骤的原始绑定目标键，仅供内部精确授权。 */
    @JsonIgnore
    private String serverBindingTargetKey;

    /** 表单按钮或流程上下文已鉴权的记录 ID；新增态允许为空。 */
    @JsonIgnore
    private String serverRecordId;

    /** 服务端已鉴权的 create/edit/view/approve 模式，供 Provider 构造可信上下文。 */
    @JsonIgnore
    private String serverFormMode;

    /** 流程表单调用已核验的任务 ID；普通实体页面为空。 */
    @JsonIgnore
    private String serverTaskId;

    /** 流程表单调用已核验的实例 ID；流程历史详情也保留该只读来源。 */
    @JsonIgnore
    private String serverProcessInstanceId;

    /**
     * 历史运行代码别名；公开请求不能提交 operationCode。
     *
     * @return 读取后的操作编码文本，供调用方比较或展示
     */
    @JsonIgnore
    public String getOperationCode() {
        return providerOperationCode;
    }

    /**
     * 历史运行代码别名；仅供服务端内部适配。
     *
     * @param value 待设置操作编码的原始输入，结果供调用方继续使用
     */
    public void setOperationCode(String value) {
        providerOperationCode = value;
    }
}
