package com.workflow.entity.ui.api.request;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * UI 事件绑定链保存请求。
 */
@Data
public class UiEventBindingSaveRequest {

    /** 事件绑定 ID；新增时为空。 */
    private String id;
    /** 客户端读取到的修订号，用于更新时的乐观并发控制。 */
    private Integer expectedRevision;
    /** 绑定所有者类型：ENTITY、FORM 或 LIST；事件必须属于对应作用域。 */
    private String ownerType;
    /** 绑定所有者 ID。 */
    private String ownerId;
    /** 精确目标类型：ENTITY 仅 OWNER，FORM 可用 FIELD/BUTTON，LIST 可用 BUTTON。 */
    private String targetType;
    /** 字段编码或按钮编码；OWNER 目标时为空。 */
    private String targetKey;
    /** 事件编码，例如 FORM_OPEN、LIST_LOAD；必须与所有者和目标类型匹配。 */
    private String eventCode;
    /** 继承模式：INHERIT、REPLACE 或 DISABLE。 */
    private String inheritanceMode;
    /** 按顺序执行的接口操作或纯映射步骤。 */
    private List<Map<String, Object>> steps;
    /** 是否启用当前事件绑定。 */
    private Boolean enabled;
}
