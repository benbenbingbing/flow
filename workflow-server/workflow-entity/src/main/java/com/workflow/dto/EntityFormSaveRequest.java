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

    private Integer expectedRevision;
    private String entityId;
    private String formName;
    private String formKey;
    private String description;
    private String layoutType;
    private Boolean isDefault;
    private Integer status;
    private String customComponent;
    private Integer customComponentVersion;
    private Integer customComponentSnapshotVersion;
    private String initConfig;
    private String dataSourceBindingsDocument;
    private String viewConfig;
    private List<EntityFormField> fields;

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
