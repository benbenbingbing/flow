package com.workflow.dto;

import lombok.Data;

import java.util.Map;
import java.util.Set;

/**
 * 表单节点局部更新请求。
 * 仅更新节点属性，支持通过 clearFields 清空指定字段。
 */
@Data
public class EntityFormNodePatchRequest {

    /** 客户端读取到的草稿修订号，用于乐观并发控制 */
    private Integer expectedRevision;
    /** 父节点 ID */
    private String parentId;
    /** 节点编码 */
    private String nodeKey;
    /** 节点类型（SECTION/GRID/FIELD 等） */
    private String nodeType;
    /** 绑定类型 */
    private String bindingType;
    /** 绑定引用（如字段编码） */
    private String bindingRef;
    /** 自定义组件注册名 */
    private String componentName;
    /** 自定义组件版本 */
    private Integer componentVersion;
    /** 自定义组件快照版本 */
    private Integer snapshotVersion;
    /** 子表单 ID */
    private String childFormId;
    /** 子表单发布 ID */
    private String childFormReleaseId;
    /** 子表单发布版本 */
    private Integer childFormReleaseVersion;
    /** 节点属性 */
    private Map<String, Object> props;
    /** 校验规则 */
    private Map<String, Object> rules;
    /** 数据源绑定配置 */
    private Map<String, Object> dataSourceBindings;
    /** 旧版属性（兼容字段） */
    private Map<String, Object> legacyProps;
    /** 排序键 */
    private Long orderKey;
    /** 来源模板 ID */
    private String templateId;
    /** 来源模板版本 */
    private Integer templateVersion;
    /** 模板本地覆盖配置 */
    private Map<String, Object> localOverrides;
    /** 需要清空的字段集合（局部更新时置空指定字段） */
    private Set<String> clearFields;
}
