package com.workflow.entity.ui.application;

import com.workflow.entity.form.application.EntityFormService;
import com.workflow.entity.list.application.EntityListConfigService;
import com.workflow.entity.list.application.EntityListRelationalConfigService;
import com.workflow.contracts.ui.UiDataSourceUsages;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.workflow.core.error.RevisionConflictException;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.form.api.request.EntityFormMetadataPatchRequest;
import com.workflow.entity.list.api.response.EntityListConfigDTO;
import com.workflow.entity.list.api.request.EntityListMetadataPatchRequest;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.form.application.validation.EntityFormConfigurationValidator;
import com.workflow.entity.list.application.validation.EntityListConfigurationValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * UI 配置草稿元数据补丁服务，支持表单与列表元数据的乐观锁增量更新。
 *
 * <p>通过 expectedRevision 防止并发覆盖，支持字段级补丁与清除，
 * 更新成功后递增版本号并清空草稿哈希，强制重新计算。</p>
 */
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
    /** 当前列表绑定位置可用接口操作查询服务。 */
    private final UiAvailableOperationService availableOperationService;

    /**
     * 按补丁请求更新表单元数据，基于乐观锁更新并维护默认表单唯一性。
     *
     * @param formId  表单ID
     * @param request 元数据补丁请求
     * @return 更新后的表单
     * @throws IllegalArgumentException    表单不存在或缺少 expectedRevision 时抛出
     * @throws RevisionConflictException   版本冲突时抛出
     */
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

    /**
     * 按补丁请求更新列表元数据，基于乐观锁更新并同步允许场景到关系型存储。
     *
     * @param listId  列表配置ID
     * @param request 元数据补丁请求
     * @return 更新后的列表配置 DTO
     * @throws IllegalArgumentException    列表不存在或缺少 expectedRevision 时抛出
     * @throws RevisionConflictException   版本冲突时抛出
     */
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
        if (request.getQueryDataSourceId() != null
                || clear.contains("queryDataSourceId")) {
            updated.setQueryDataSourceId(
                    clear.contains("queryDataSourceId")
                            ? null
                            : blankToNull(
                                    request.getQueryDataSourceId()));
        }
        if (request.getQueryOperationCode() != null
                || clear.contains("queryOperationCode")) {
            updated.setQueryOperationCode(
                    clear.contains("queryOperationCode")
                            ? null
                            : blankToNull(
                                    request.getQueryOperationCode()));
        }
        listValidator.validate(updated);
        validateListQueryOperation(listId, updated);

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
                .set("query_data_source_id",
                        updated.getQueryDataSourceId())
                .set("query_operation_code",
                        updated.getQueryOperationCode())
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

    /**
     * 校验列表查询绑定在当前列表作用域、上下文、读写类型和分页 Schema 下可用。
     *
     * @param listId 列表配置 ID
     * @param config 待保存的列表配置
     */
    private void validateListQueryOperation(
            String listId,
            EntityListConfigDTO config) {
        if (!StringUtils.hasText(
                config.getQueryDataSourceId())) {
            return;
        }
        boolean available = availableOperationService
                .available(
                        "LIST",
                        listId,
                        UiDataSourceUsages.LIST_QUERY)
                .stream()
                .anyMatch(operation ->
                        Objects.equals(
                                operation.serviceId(),
                                config.getQueryDataSourceId())
                                && Objects.equals(
                                        operation.operationCode(),
                                        config.getQueryOperationCode()));
        if (!available) {
            throw new IllegalArgumentException(
                    "所选接口操作不适用于当前列表查询");
        }
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
