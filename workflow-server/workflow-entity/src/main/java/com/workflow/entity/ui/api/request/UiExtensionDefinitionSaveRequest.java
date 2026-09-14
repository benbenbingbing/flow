package com.workflow.entity.ui.api.request;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * UI 扩展定义保存请求。
 */
@Data
public class UiExtensionDefinitionSaveRequest {

    /** 扩展定义 ID（更新时传入） */
    private String id;
    /** 扩展类型 */
    private String extensionType;
    /** 扩展编码 */
    private String extensionKey;
    /** 显示名称 */
    private String displayName;
    /** 版本号 */
    private Integer version;
    /** 快照版本 */
    private Integer snapshotVersion;
    /** 适用范围：GLOBAL/ENTITY */
    private String visibilityScope;
    /** 指定适用实体编码 */
    private List<String> entityCodes;
    /** 支持的模式列表 */
    private List<String> supportedModes;
    /** 支持的节点类型列表 */
    private List<String> supportedNodeTypes;
    /** 支持的绑定类型列表 */
    private List<String> supportedBindings;
    /** 配置项 Schema */
    private Object configSchema;
    /** 能力声明 */
    private Map<String, Object> capabilities;
    /** 接口实现类型；仅 INTERFACE 类型使用。 */
    private String implementationType;
    /** 注册 Provider 编码；仅 REGISTERED_PROVIDER 使用。 */
    private String providerCode;
    /** 接口作用范围：GLOBAL/ENTITY/FORM/LIST。 */
    private String scopeType;
    /** 非 GLOBAL 作用范围对象 ID。 */
    private String scopeId;
    /** 接口实现配置。 */
    private Map<String, Object> implementationConfig;
    /** 接口执行策略。 */
    private Map<String, Object> executionPolicy;
    /** 接口输入 Schema。 */
    private Map<String, Object> inputSchema;
    /** 接口输出 Schema。 */
    private Map<String, Object> outputSchema;
    /** 接口类型：READ/WRITE。 */
    private String interfaceKind;
    /** 接口上下文：FORM/LIST/ENTITY。 */
    private String interfaceContextType;
    /** Provider 内部路由编码；不作为设计器可选操作。 */
    private String providerOperationCode;
    /** 状态 */
    private String status;
    /** 客户端读取到的草稿修订号，用于乐观并发控制 */
    private Integer expectedRevision;
}
