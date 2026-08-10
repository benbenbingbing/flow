package com.workflow.entity.ui.api.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.Map;

/**
 * 接口服务内部执行请求。
 *
 * <p>公开运行接口先把可验证的绑定声明转换为此对象；客户端不能通过公开接口
 * 直接提交 entityCode、listKey、release 或任意可信上下文。</p>
 */
@Data
public class UiDataSourceExecuteRequest {

    /** 绑定位置或事件编码，例如 FIELD_OPTIONS、LIST_COLUMN。 */
    private String usage;
    /** 必填的接口服务操作编码。 */
    private String operationCode;
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
    /** 表单值、筛选条件、记录等业务输入。 */
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

    /** 服务端解析的实体 CRUD 操作类型，不接受客户端赋值。 */
    @JsonIgnore
    private String serverEntityOperation;

    /** 是否固定使用服务端指定的发布版本，不序列化给前端。 */
    @JsonIgnore
    private boolean serverPinnedRelease;
}
