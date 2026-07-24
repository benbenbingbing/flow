package com.workflow.dto;

import com.workflow.entity.EntityForm;
import com.workflow.entity.EntityFormField;
import lombok.Data;

import java.util.List;

/**
 * 兼容整包表单更新请求。
 *
 * 仅暴露允许由配置 API 修改的字段，数据库维护字段由服务端保留。
 */
@Data
public class EntityFormSaveRequest {

    /** 客户端读取到的草稿修订号，用于乐观并发控制 */
    private Integer expectedRevision;
    /** 实体 ID */
    private String entityId;
    /** 表单名称 */
    private String formName;
    /** 表单标识 */
    private String formKey;
    /** 表单描述 */
    private String description;
    /** 布局类型 */
    private String layoutType;
    /** 是否为默认表单 */
    private Boolean isDefault;
    /** 表单状态 */
    private Integer status;
    /** 自定义组件注册名 */
    private String customComponent;
    /** 自定义组件版本 */
    private Integer customComponentVersion;
    /** 自定义组件快照版本 */
    private Integer customComponentSnapshotVersion;
    /** 初始化配置（JSON 文档） */
    private String initConfig;
    /** 数据源绑定配置（JSON 文档） */
    private String dataSourceBindingsDocument;
    /** 视图配置（JSON 文档） */
    private String viewConfig;
    /** 表单字段列表 */
    private List<EntityFormField> fields;

    /**
     * 将请求转换为实体对象，便于持久化。
     *
     * @param id 表单 ID
     * @return 转换后的 EntityForm 实体
     */
    public EntityForm toEntity(String id) {
        EntityForm form = new EntityForm();
        form.setId(id);
        form.setEntityId(entityId);
        form.setFormName(formName);
        form.setFormKey(formKey);
        form.setDescription(description);
        form.setLayoutType(layoutType);
        form.setIsDefault(isDefault);
        form.setStatus(status);
        form.setCustomComponent(customComponent);
        form.setCustomComponentVersion(customComponentVersion);
        form.setCustomComponentSnapshotVersion(
                customComponentSnapshotVersion);
        form.setInitConfig(initConfig);
        form.setDataSourceBindingsDocument(dataSourceBindingsDocument);
        form.setViewConfig(viewConfig);
        form.setFields(fields);
        return form;
    }
}
