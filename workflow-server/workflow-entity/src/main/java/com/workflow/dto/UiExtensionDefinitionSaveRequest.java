package com.workflow.dto;

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
    /** 支持的模式列表 */
    private List<String> supportedModes;
    /** 支持的节点类型列表 */
    private List<String> supportedNodeTypes;
    /** 支持的绑定类型列表 */
    private List<String> supportedBindings;
    /** 配置项 Schema */
    private Map<String, Object> configSchema;
    /** 能力声明 */
    private Map<String, Object> capabilities;
    /** 状态 */
    private String status;
    /** 客户端读取到的草稿修订号，用于乐观并发控制 */
    private Integer expectedRevision;
}
