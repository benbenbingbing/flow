package com.workflow.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.workflow.common.RevisionConflictException;
import com.workflow.common.json.JsonDocumentCodec;
import com.workflow.dto.EntityFormMetadataPatchRequest;
import com.workflow.dto.EntityListConfigDTO;
import com.workflow.dto.EntityListMetadataPatchRequest;
import com.workflow.entity.EntityForm;
import com.workflow.entity.EntityListConfig;
import com.workflow.mapper.EntityFormMapper;
import com.workflow.mapper.EntityListConfigMapper;
import com.workflow.service.config.EntityFormConfigurationValidator;
import com.workflow.service.config.EntityListConfigurationValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UiConfigDraftMetadataService {

    private final EntityFormMapper formMapper;
    private final EntityListConfigMapper listMapper;
    private final EntityFormService formService;
    private final EntityListConfigService listService;
    private final EntityFormConfigurationValidator formValidator;
    private final EntityListConfigurationValidator listValidator;
    private final EntityListRelationalConfigService relationalConfigService;
    private final JsonDocumentCodec codec;

    @Transactional(rollbackFor = Exception.class)
    public EntityForm patchForm(
            String formId,
            EntityFormMetadataPatchRequest request) {
        EntityForm current = formService.getById(formId);
        if (current == null) {
            throw new IllegalArgumentException("表单不存在");
        }
        requireRevision(request == null ? null : request.getExpectedRevision(), current.getRevision(), current);
        EntityForm updated = new EntityForm();
        BeanUtils.copyProperties(current, updated);
        Set<String> clear = request.getClearFields() == null
                ? Set.of() : request.getClearFields();
        if (request.getFormName() != null) updated.setFormName(request.getFormName());
        if (request.getDescription() != null || clear.contains("description")) {
            updated.setDescription(clear.contains("description")
                    ? null : blankToNull(request.getDescription()));
        }
        if (request.getLayoutType() != null) updated.setLayoutType(request.getLayoutType());
        if (request.getIsDefault() != null) updated.setIsDefault(request.getIsDefault());
        if (request.getStatus() != null) updated.setStatus(request.getStatus());
        if (request.getCustomComponent() != null || clear.contains("customComponent")) {
            updated.setCustomComponent(clear.contains("customComponent")
                    ? null : blankToNull(request.getCustomComponent()));
        }
        if (request.getCustomComponentVersion() != null
                || clear.contains("customComponentVersion")) {
            updated.setCustomComponentVersion(
                    clear.contains("customComponentVersion")
                            ? null : request.getCustomComponentVersion());
        }
        if (request.getCustomComponentSnapshotVersion() != null
                || clear.contains("customComponentSnapshotVersion")) {
            updated.setCustomComponentSnapshotVersion(
                    clear.contains("customComponentSnapshotVersion")
                            ? null
                            : request.getCustomComponentSnapshotVersion());
        }
        if (request.getInitConfig() != null || clear.contains("initConfig")) {
            updated.setInitConfig(clear.contains("initConfig")
                    ? null : write(request.getInitConfig(), "表单初始化配置"));
        }
        if (request.getDataSourceBindings() != null
                || clear.contains("dataSourceBindings")) {
            updated.setDataSourceBindingsDocument(
                    clear.contains("dataSourceBindings")
                            ? null
                            : write(
                            request.getDataSourceBindings(),
                            "表单级数据源绑定"));
        }
        if (request.getViewConfig() != null || clear.contains("viewConfig")) {
            updated.setViewConfig(clear.contains("viewConfig")
                    ? null : write(request.getViewConfig(), "表单视图配置"));
        }
        updated.setRevision(current.getRevision() + 1);
        updated.setFields(null);
        updated.setNodes(null);
        formValidator.validateForm(updated);

        UpdateWrapper<EntityForm> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", formId)
                .eq("revision", current.getRevision())
                .set("form_name", updated.getFormName())
                .set("description", updated.getDescription())
                .set("layout_type", updated.getLayoutType())
                .set("is_default", updated.getIsDefault())
                .set("status", updated.getStatus())
                .set("custom_component", updated.getCustomComponent())
                .set("custom_component_version", updated.getCustomComponentVersion())
                .set("custom_component_snapshot_version",
                        updated.getCustomComponentSnapshotVersion())
                .set("init_config", updated.getInitConfig())
                .set(
                        "data_source_bindings_document",
                        updated.getDataSourceBindingsDocument())
                .set("view_config", updated.getViewConfig())
                .set("revision", updated.getRevision())
                .set("draft_hash", null)
                .set("update_time", LocalDateTime.now());
        if (formMapper.update(null, wrapper) != 1) {
            throw new RevisionConflictException(
                    "表单元数据已被其他人修改，请刷新后重试",
                    formService.getById(formId));
        }
        if (Boolean.TRUE.equals(updated.getIsDefault())) {
            for (EntityForm form : formService.getFormsByEntityId(updated.getEntityId())) {
                if (!formId.equals(form.getId()) && Boolean.TRUE.equals(form.getIsDefault())) {
                    UpdateWrapper<EntityForm> defaultUpdate = new UpdateWrapper<>();
                    defaultUpdate.eq("id", form.getId()).set("is_default", false);
                    formMapper.update(null, defaultUpdate);
                }
            }
        }
        return formService.getById(formId);
    }

    @Transactional(rollbackFor = Exception.class)
    public EntityListConfigDTO patchList(
            String listId,
            EntityListMetadataPatchRequest request) {
        EntityListConfigDTO current = listService.findById(listId);
        if (current == null) {
            throw new IllegalArgumentException("列表配置不存在");
        }
        requireRevision(request == null ? null : request.getExpectedRevision(), current.getRevision(), current);
        EntityListConfigDTO updated = new EntityListConfigDTO();
        BeanUtils.copyProperties(current, updated);
        Set<String> clear = request.getClearFields() == null
                ? Set.of() : request.getClearFields();
        if (request.getListName() != null) updated.setListName(request.getListName());
        if (request.getDescription() != null || clear.contains("description")) {
            updated.setDescription(clear.contains("description")
                    ? null : blankToNull(request.getDescription()));
        }
        if (request.getIsDefault() != null) updated.setIsDefault(request.getIsDefault());
        if (request.getCustomComponent() != null || clear.contains("customComponent")) {
            updated.setCustomComponent(clear.contains("customComponent")
                    ? null : blankToNull(request.getCustomComponent()));
        }
        if (request.getDataScopeMode() != null) updated.setDataScopeMode(request.getDataScopeMode());
        if (request.getAccessPermissionCode() != null || clear.contains("accessPermissionCode")) {
            updated.setAccessPermissionCode(clear.contains("accessPermissionCode")
                    ? null : blankToNull(request.getAccessPermissionCode()));
        }
        if (request.getAllowedScenes() != null) updated.setAllowedScenes(request.getAllowedScenes());
        if (request.getSelectionConfig() != null) updated.setSelectionConfig(request.getSelectionConfig());
        if (request.getFixedFilterConfig() != null) updated.setFixedFilterConfig(request.getFixedFilterConfig());
        if (request.getContextBindingConfig() != null) {
            updated.setContextBindingConfig(request.getContextBindingConfig());
        }
        if (request.getViewConfig() != null) updated.setViewConfig(request.getViewConfig());
        if (request.getQueryProviderCode() != null || clear.contains("queryProviderCode")) {
            updated.setQueryProviderCode(clear.contains("queryProviderCode")
                    ? null : blankToNull(request.getQueryProviderCode()));
        }
        if (request.getQueryDataSourceId() != null || clear.contains("queryDataSourceId")) {
            updated.setQueryDataSourceId(clear.contains("queryDataSourceId")
                    ? null : blankToNull(request.getQueryDataSourceId()));
        }
        listValidator.validate(updated);

        UpdateWrapper<EntityListConfig> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", listId)
                .eq("revision", current.getRevision())
                .set("list_name", updated.getListName())
                .set("description", updated.getDescription())
                .set("is_default", updated.getIsDefault())
                .set("custom_component", updated.getCustomComponent())
                .set("data_scope_mode", updated.getDataScopeMode())
                .set("access_permission_code", updated.getAccessPermissionCode())
                .set("allowed_scenes", write(updated.getAllowedScenes(), "允许场景配置"))
                .set("selection_config", write(updated.getSelectionConfig(), "选择模式配置"))
                .set("fixed_filter_config", write(updated.getFixedFilterConfig(), "固定查询条件"))
                .set("context_binding_config", write(updated.getContextBindingConfig(), "上下文绑定配置"))
                .set("view_config", write(updated.getViewConfig(), "列表视图配置"))
                .set("query_provider_code", updated.getQueryProviderCode())
                .set("query_data_source_id", updated.getQueryDataSourceId())
                .set("revision", current.getRevision() + 1)
                .set("draft_hash", null)
                .set("update_time", LocalDateTime.now());
        if (listMapper.update(null, wrapper) != 1) {
            throw new RevisionConflictException(
                    "列表元数据已被其他人修改，请刷新后重试",
                    listService.findById(listId));
        }
        if (request.getAllowedScenes() != null) {
            relationalConfigService.replaceScenes(listId, request.getAllowedScenes());
        }
        return listService.findById(listId);
    }

    private void requireRevision(
            Integer expected,
            Integer current,
            Object currentData) {
        if (expected == null) {
            throw new IllegalArgumentException("expectedRevision 不能为空");
        }
        if (!expected.equals(current)) {
            throw new RevisionConflictException("配置已被其他人修改", currentData);
        }
    }

    private String write(Object value, String label) {
        return value == null ? null : codec.write(value, label);
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
