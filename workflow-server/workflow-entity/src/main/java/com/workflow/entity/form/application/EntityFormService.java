package com.workflow.entity.form.application;

import com.workflow.core.logging.LogValue;
import com.workflow.entity.definition.application.EntityUiConfigurationPolicy;
import com.workflow.entity.definition.application.SystemEntityFieldPolicy;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflow.core.error.RevisionConflictException;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.contracts.audit.model.AuditAction;
import com.workflow.contracts.audit.model.AuditModule;
import com.workflow.contracts.audit.model.AuditRiskLevel;
import com.workflow.contracts.audit.annotation.SystemAudit;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormField;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormNodeMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListActionMapper;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListAction;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import com.workflow.entity.ui.application.UiViewCompositionService;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityRelationMapper;
import com.workflow.entity.form.application.validation.EntityFormConfigurationValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 实体表单服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityFormService {
    /**
     * 定义保存模式的可选值；调用方据此选择对应的处理分支。
     */
    private enum SaveMode {
        USER_CAS,
        SYSTEM_IMPORT
    }

    private final EntityFormMapper formMapper;
    private final EntityFormNodeMapper formNodeMapper;
    private final EntityDefinitionMapper entityMapper;
    private final EntityFieldMapper fieldMapper;
    private final EntityRelationMapper relationMapper;
    private final EntityFormConfigurationValidator configurationValidator;
    private final EntityUiConfigurationPolicy entityUiConfigurationPolicy;
    private final SystemEntityFieldPolicy systemEntityFieldPolicy;
    private final EntityListActionMapper listActionMapper;
    private final UiConfigReleaseMapper uiConfigReleaseMapper;
    private final JsonDocumentCodec jsonDocumentCodec;
    private UiViewCompositionService viewCompositionService;

    /**
     * 延迟注入关联内容服务，避免表单服务、发布服务和接口服务之间形成启动期
     * Bean 循环；仅复制完整表单设计时才会解析该依赖。
     *
     * @param viewCompositionService 视图组合服务，供本方法设置视图组合服务时使用
     */
    @Autowired
    void setViewCompositionService(
            @Lazy UiViewCompositionService viewCompositionService) {
        this.viewCompositionService = viewCompositionService;
    }

    /**
     * 查询所有表单列表
     *
     * @return 实体表单集合，供调用方遍历或展示
     */
    public List<EntityForm> list() {
        List<EntityForm> forms = formMapper.selectList(null);
        forms.forEach(this::fillFormDetails);
        return forms;
    }

    /**
     * 查询实体的表单列表
     *
     * @param entityId 实体ID，后续用于读取表单集合实体ID时定位或关联目标
     * @return 实体表单集合，供调用方遍历或展示
     */
    public List<EntityForm> getFormsByEntityId(String entityId) {
        List<EntityForm> forms = formMapper.selectByEntityId(entityId);
        forms.forEach(this::fillFormDetails);
        return forms;
    }

    /**
     * 根据ID查询表单
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 符合条件的实体表单结果，供调用方继续处理
     */
    public EntityForm getById(String id) {
        return populateDesign(formMapper.selectById(id));
    }

    /**
     * 保存表单；后续读取或执行将使用更新后的状态。
     *
     * @param form 表单，作为 {@code saveFormInternal} 的输入影响后续处理
     * @return 保存后的表单结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(module = AuditModule.ENTITY, action = AuditAction.UPSERT, operation = "保存实体表单", risk = AuditRiskLevel.HIGH, targetType = "ENTITY_FORM", captureArguments = true, captureResult = true)
    public EntityForm saveForm(EntityForm form) {
        return saveFormInternal(form, null, SaveMode.SYSTEM_IMPORT);
    }

    /**
     * 保存表单；后续读取或执行将使用更新后的状态。
     *
     * @param form 表单，作为 {@code saveFormInternal} 的输入影响后续处理
     * @param expectedRevision 预期修订版本，作为 {@code saveFormInternal} 的输入影响后续处理
     * @return 保存后的表单结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    public EntityForm saveForm(EntityForm form, Integer expectedRevision) {
        return saveFormInternal(form, expectedRevision, SaveMode.USER_CAS);
    }

    /**
     * 保存表单导入；后续读取或执行将使用更新后的状态。
     *
     * @param form 表单，作为 {@code saveFormInternal} 的输入影响后续处理
     * @return 保存后的表单导入结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    public EntityForm saveFormForImport(EntityForm form) {
        return saveFormInternal(form, null, SaveMode.SYSTEM_IMPORT);
    }

    /**
     * 按发布快照恢复表单元数据并校验 revision；节点由发布服务在同一事务中恢复。
     *
     * @param form 表单，作为 {@code lockForm} 的输入影响后续处理
     * @param expectedRevision 预期修订版本，作为 {@code requireExpectedRevision} 的输入影响后续处理
     * @return 恢复后的表单发布版本结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    public EntityForm restoreFormForRelease(
            EntityForm form,
            Integer expectedRevision) {
        if (form == null || !StringUtils.hasText(form.getId())) {
            throw new IllegalArgumentException("发布表单快照不能为空");
        }
        EntityForm current = lockForm(form.getId());
        requireExpectedRevision(
                expectedRevision,
                current,
                "表单已被其他人修改");
        return saveFormInternal(form, null, SaveMode.SYSTEM_IMPORT);
    }

    /**
     * 保存表单内部；后续读取或执行将使用更新后的状态。
     *
     * @param source 待保存表单内部的原始输入，结果供调用方继续使用
     * @param expectedRevision 预期修订版本，作为 {@code requireExpectedRevision} 的输入影响后续处理
     * @param saveMode 保存模式标识，决定后续表单内部采用的处理分支
     * @return 保存后的表单内部结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private EntityForm saveFormInternal(
            EntityForm source,
            Integer expectedRevision,
            SaveMode saveMode) {
        if (source == null) {
            throw new IllegalArgumentException("表单配置不能为空");
        }
        EntityForm current = null;
        EntityForm desired = new EntityForm();
        boolean isNew = !StringUtils.hasText(source.getId());
        if (isNew) {
            desired.setId(saveMode == SaveMode.SYSTEM_IMPORT
                    ? source.getId()
                    : null);
            desired.setEntityId(source.getEntityId());
            desired.setFormKey(source.getFormKey());
            desired.setRevision(1);
            desired.setCreateTime(LocalDateTime.now());
        } else {
            current = formMapper.selectByIdForUpdate(source.getId());
            if (current == null) {
                throw new IllegalArgumentException("表单不存在");
            }
            if (saveMode == SaveMode.USER_CAS) {
                requireExpectedRevision(
                        expectedRevision,
                        current,
                        "表单已被其他人修改");
            }
            BeanUtils.copyProperties(current, desired);
        }
        applyMutableFormProperties(source, desired, isNew, saveMode);
        desired.setFields(source.getFields());
        desired.setNodes(null);
        applyFormDefaults(desired);
        entityUiConfigurationPolicy.requireConfigurableById(
                desired.getEntityId());
        validateSystemFormConfiguration(
                desired,
                source.getFields());
        configurationValidator.validateForm(desired);
        validateFormKey(desired);
        LocalDateTime now = LocalDateTime.now();
        desired.setUpdateTime(now);
        if (isNew) {
            formMapper.insert(desired);
            log.info("新增实体表单：{}", LogValue.safe(desired.getFormName()));
        } else {
            int currentRevision = revisionOf(current);
            desired.setRevision(currentRevision + 1);
            desired.setDraftHash(null);
            UpdateWrapper<EntityForm> wrapper = formRevisionCondition(
                    desired.getId(), current);
            setMutableFormColumns(wrapper, desired);
            wrapper.set("revision", desired.getRevision())
                    .set("draft_hash", null)
                    .set("update_time", now);
            if (formMapper.update(null, wrapper) != 1) {
                throw formConflict(
                        desired.getId(),
                        "表单已被其他人修改，请刷新后重试");
            }
            log.info("更新实体表单：{}", LogValue.safe(desired.getFormName()));
        }
        if (Boolean.TRUE.equals(desired.getIsDefault())) {
            clearOtherDefaultForm(desired.getEntityId(), desired.getId());
        }
        return getById(desired.getId());
    }

    /**
     * 将同一实体下的其他表单设为非默认
     *
     * @param entityId 实体ID，后续用于清理{@code other}默认表单时定位或关联目标
     * @param currentFormId 当前表单ID，后续用于清理{@code other}默认表单时定位或关联目标
     */
    private void clearOtherDefaultForm(String entityId, String currentFormId) {
        List<EntityForm> forms = formMapper.selectByEntityId(entityId);
        for (EntityForm form : forms) {
            if (!form.getId().equals(currentFormId) && Boolean.TRUE.equals(form.getIsDefault())) {
                UpdateWrapper<EntityForm> wrapper = new UpdateWrapper<>();
                wrapper.eq("id", form.getId())
                        .eq("deleted", 0)
                        .eq("is_default", true)
                        .set("is_default", false)
                        .setSql("revision = revision + 1")
                        .set("draft_hash", null)
                        .set("update_time", LocalDateTime.now());
                formMapper.update(null, wrapper);
                log.info("取消表单默认状态：{}", form.getFormName());
            }
        }
    }

    /**
     * 读取默认表单；查询结果供调用方展示或继续处理。
     *
     * @param entityId 实体ID，后续用于读取默认表单时定位或关联目标
     * @return 符合条件的实体表单结果，供调用方继续处理
     */
    public EntityForm getDefaultForm(String entityId) {
        return populateDesign(formMapper.selectDefaultByEntityId(entityId));
    }

    /**
     * 删除表单
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(module = AuditModule.ENTITY, action = AuditAction.DELETE, operation = "删除实体表单", risk = AuditRiskLevel.HIGH, targetType = "ENTITY_FORM", targetIdArg = 0)
    public void deleteForm(String id) {
        EntityForm form = formMapper.selectById(id);
        if (form == null) {
            throw new RuntimeException("表单不存在");
        }
        requireNoListButtonReference(id);

        // 节点随表单逻辑删除，已发布快照保留原有配置。
        formNodeMapper.delete(new LambdaQueryWrapper<com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode>()
                .eq(com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode::getFormId, id));
        // 逻辑删除表单
        formMapper.deleteById(id);
        log.info("删除实体表单：{}", form.getFormName());
    }

    /**
     * 校验并获取无列表按钮引用；不满足约束时阻止后续处理。
     *
     * @param formId 表单ID，后续用于校验并获取无列表按钮引用时定位或关联目标
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private void requireNoListButtonReference(String formId) {
        List<EntityListAction> actions = listActionMapper.selectList(
                new LambdaQueryWrapper<EntityListAction>()
                        .eq(EntityListAction::getDeleted, 0));
        boolean draftReferenced = actions.stream().anyMatch(action -> containsTargetFormReference(
                action.getActionParamsDocument(),
                formId,
                "列表按钮参数"));
        if (draftReferenced) {
            throw new IllegalStateException(
                    "表单仍被列表按钮草稿引用，请先清除按钮的打开表单配置");
        }

        List<UiConfigRelease> activeLists = uiConfigReleaseMapper.selectList(
                new LambdaQueryWrapper<UiConfigRelease>()
                        .eq(UiConfigRelease::getConfigType, "LIST")
                        .eq(UiConfigRelease::getStatus, "ACTIVE"));
        boolean publishedReferenced = activeLists.stream().anyMatch(release -> containsTargetFormReference(
                release.getSnapshotDocument(),
                formId,
                "列表发布快照"));
        if (publishedReferenced) {
            throw new IllegalStateException(
                    "表单仍被已发布列表按钮引用，请先发布移除引用后的列表版本");
        }
    }

    /**
     * 判断是否包含目标表单引用；判断结果决定调用方的后续分支。
     *
     * @param document 文档，供本方法判断是否包含目标表单引用时使用
     * @param formId 表单ID，后续用于判断是否包含目标表单引用时定位或关联目标
     * @param label 标签，后续用于判断是否包含目标表单引用时匹配或展示
     * @return 目标表单引用条件成立时为 true，否则为 false
     */
    private boolean containsTargetFormReference(
            String document,
            String formId,
            String label) {
        if (!StringUtils.hasText(document)) {
            return false;
        }
        return containsTargetFormReference(
                jsonDocumentCodec.readObject(document, label),
                formId);
    }

    /**
     * 判断是否包含目标表单引用；判断结果决定调用方的后续分支。
     *
     * @param value 待判断是否包含目标表单引用的原始输入，结果供调用方继续使用
     * @param formId 表单ID，后续用于判断是否包含目标表单引用时定位或关联目标
     * @return 目标表单引用条件成立时为 true，否则为 false
     */
    private boolean containsTargetFormReference(
            Object value,
            String formId) {
        if (value instanceof Map<?, ?> map) {
            if (Objects.equals(
                    formId,
                    String.valueOf(map.get("targetFormId")))) {
                return true;
            }
            return map.values().stream().anyMatch(child -> containsTargetFormReference(child, formId));
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object child : iterable) {
                if (containsTargetFormReference(child, formId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 校验系统表单配置；不满足约束时阻止后续处理。
     *
     * @param form 表单，作为 {@code entityMapper.selectById} 的输入影响后续处理
     * @param fields 字段集合，后续逐项校验、转换或持久化
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void validateSystemFormConfiguration(
            EntityForm form,
            List<EntityFormField> fields) {
        EntityDefinition entity = entityMapper.selectById(form.getEntityId());
        if (entity == null
                || entity.getStorageMode() != EntityDefinition.StorageMode.SYSTEM) {
            return;
        }
        if (StringUtils.hasText(form.getCustomComponent())) {
            throw new IllegalArgumentException(
                    "平台系统表表单不能使用自定义写入组件");
        }
        new EntityFormActionConfigPolicy().validate(
                StringUtils.hasText(form.getViewConfig())
                        ? jsonDocumentCodec.readObject(
                                form.getViewConfig(),
                                "平台系统表表单视图配置")
                        : Map.of(),
                true,
                Set.of(),
                false,
                Set.of(),
                false);
        Map<String, EntityField> byId = new HashMap<>();
        Map<String, EntityField> byCode = new HashMap<>();
        fieldMapper.findByEntityId(entity.getId()).forEach(field -> {
            byId.put(field.getId(), field);
            byCode.put(field.getFieldCode(), field);
        });
        for (EntityFormField configured : fields == null ? List.<EntityFormField>of() : fields) {
            EntityField field = StringUtils.hasText(configured.getFieldId())
                    ? byId.get(configured.getFieldId())
                    : byCode.get(configured.getFieldCode());
            if (field == null
                    || !systemEntityFieldPolicy
                            .isUiConfigurable(entity, field)) {
                throw new IllegalArgumentException(
                        "平台系统表字段不可配置: "
                                + configured.getFieldCode());
            }
            if (StringUtils.hasText(configured.getFieldCode())
                    && !Objects.equals(
                            configured.getFieldCode(),
                            field.getFieldCode())) {
                throw new IllegalArgumentException(
                        "平台系统表字段编码与字段目录不一致");
            }
            configured.setFieldId(field.getId());
            configured.setFieldCode(field.getFieldCode());
            configured.setFieldType(
                    field.getFieldType() == null
                            ? configured.getFieldType()
                            : field.getFieldType().name());
            configured.setIsReadonly(1);
        }
    }

    /**
     * 应用可变表单属性集合，并将结果传给后续步骤。
     *
     * @param source 待应用可变表单属性集合的原始输入，结果供调用方继续使用
     * @param target 目标，供本方法应用可变表单属性集合时使用
     * @param isNew 是否新，供本方法应用可变表单属性集合时使用
     * @param saveMode 保存模式标识，决定后续可变表单属性集合采用的处理分支
     */
    private void applyMutableFormProperties(
            EntityForm source,
            EntityForm target,
            boolean isNew,
            SaveMode saveMode) {
        target.setFormName(source.getFormName());
        target.setDescription(source.getDescription());
        target.setLayoutType(source.getLayoutType());
        target.setIsDefault(source.getIsDefault());
        target.setStatus(source.getStatus());
        target.setCustomComponent(source.getCustomComponent());
        target.setCustomComponentVersion(source.getCustomComponentVersion());
        target.setCustomComponentSnapshotVersion(
                source.getCustomComponentSnapshotVersion());
        if (isNew
                || saveMode == SaveMode.SYSTEM_IMPORT
                || source.getDataSourceBindingsDocument() != null) {
            target.setDataSourceBindingsDocument(
                    source.getDataSourceBindingsDocument());
        }
        target.setViewConfig(source.getViewConfig());
    }

    /**
     * 应用表单{@code defaults}，并将结果传给后续步骤。
     *
     * @param form 表单，供本方法应用表单{@code defaults}时使用
     */
    private void applyFormDefaults(EntityForm form) {
        if (!StringUtils.hasText(form.getLayoutType())) {
            form.setLayoutType("vertical");
        }
        if (form.getStatus() == null) {
            form.setStatus(1);
        }
        if (form.getIsDefault() == null) {
            form.setIsDefault(false);
        }
    }

    /**
     * 校验表单键；不满足约束时阻止后续处理。
     *
     * @param form 表单，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void validateFormKey(EntityForm form) {
        if (!StringUtils.hasText(form.getFormKey())) {
            return;
        }
        String excludeId = StringUtils.hasText(form.getId())
                ? form.getId()
                : "";
        if (formMapper.existsFormKey(
                form.getEntityId(),
                form.getFormKey(),
                excludeId)) {
            throw new IllegalArgumentException(
                    "表单标识已存在：" + form.getFormKey());
        }
    }

    /**
     * 设置可变表单列集合；后续读取或执行将使用更新后的状态。
     *
     * @param wrapper {@code wrapper}，供本方法设置可变表单列集合时使用
     * @param form 表单，作为 {@code wrapper.set} 的输入影响后续处理
     */
    private void setMutableFormColumns(
            UpdateWrapper<EntityForm> wrapper,
            EntityForm form) {
        wrapper.set("form_name", form.getFormName())
                .set("description", form.getDescription())
                .set("layout_type", form.getLayoutType())
                .set("is_default", form.getIsDefault())
                .set("status", form.getStatus())
                .set("custom_component", form.getCustomComponent())
                .set("custom_component_version",
                        form.getCustomComponentVersion())
                .set("custom_component_snapshot_version",
                        form.getCustomComponentSnapshotVersion())
                .set("data_source_bindings_document",
                        form.getDataSourceBindingsDocument())
                .set("view_config", form.getViewConfig());
    }

    /**
     * 处理表单修订版本条件，并将结果传给后续步骤。
     *
     * @param formId 表单ID，后续用于处理表单修订版本条件时定位或关联目标
     * @param current 当前，作为 {@code wrapper.eq} 的输入影响后续处理
     * @return 处理后的表单修订版本条件结果，供调用方继续处理
     */
    private UpdateWrapper<EntityForm> formRevisionCondition(
            String formId,
            EntityForm current) {
        UpdateWrapper<EntityForm> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", formId).eq("deleted", 0);
        if (current.getRevision() == null) {
            wrapper.isNull("revision");
        } else {
            wrapper.eq("revision", current.getRevision());
        }
        return wrapper;
    }

    /**
     * 锁定表单；避免后续并发处理覆盖状态。
     *
     * @param formId 表单ID，后续用于锁定表单时定位或关联目标
     * @return 锁定后的表单结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private EntityForm lockForm(String formId) {
        EntityForm current = formMapper.selectByIdForUpdate(formId);
        if (current == null) {
            throw new IllegalArgumentException("表单不存在");
        }
        return current;
    }

    /**
     * 校验并获取预期修订版本；不满足约束时阻止后续处理。
     *
     * @param expectedRevision 预期修订版本，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @param current 当前，作为 {@code RevisionConflictException} 的输入影响后续处理
     * @param message 消息，作为 {@code RevisionConflictException} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void requireExpectedRevision(
            Integer expectedRevision,
            EntityForm current,
            String message) {
        if (expectedRevision == null) {
            throw new IllegalArgumentException("expectedRevision 不能为空");
        }
        if (!expectedRevision.equals(revisionOf(current))) {
            throw new RevisionConflictException(
                    message,
                    getById(current.getId()));
        }
    }

    /**
     * 处理修订版本，并将结果传给后续步骤。
     *
     * @param form 表单，供本方法处理修订版本时使用
     * @return 处理后的修订版本结果，供调用方继续处理
     */
    private int revisionOf(EntityForm form) {
        return form.getRevision() == null ? 0 : form.getRevision();
    }

    /**
     * 构造表单冲突异常，供调用方区分失败原因。
     *
     * @param formId 表单ID，后续用于处理表单冲突时定位或关联目标
     * @param message 消息，作为 {@code RevisionConflictException} 的输入影响后续处理
     * @return 处理后的表单冲突结果，供调用方继续处理
     */
    private RevisionConflictException formConflict(
            String formId,
            String message) {
        return new RevisionConflictException(message, getById(formId));
    }

    /**
     * 获取表单字段
     *
     * @param formId 表单ID，后续用于读取表单字段时定位或关联目标
     * @return 实体表单字段集合，供调用方遍历或展示
     */
    public List<EntityFormField> getFormFields(String formId) {
        return projectFields(formId, formNodeMapper.findByFormId(formId));
    }

    /**
     * 同一次节点读取同时用于节点树和字段视图，避免并发修改时两份配置不一致。
     *
     * @param form 表单，作为 {@code fillFormDetails} 的输入影响后续处理
     * @return 处理后的{@code populate}{@code design}结果，供调用方继续处理
     */
    private EntityForm populateDesign(EntityForm form) {
        if (form == null) return null;
        fillFormDetails(form);
        List<EntityFormNode> nodes = formNodeMapper.findByFormId(form.getId());
        form.setNodes(nodes);
        form.setFields(projectFields(form.getId(), nodes));
        return form;
    }

    /**
     * 整理项目字段数据，供调用方遍历或继续处理。
     *
     * @param formId 表单ID，后续用于处理项目字段时定位或关联目标
     * @param nodes 节点集合，供本方法处理项目字段时使用
     * @return 实体表单字段集合，供调用方遍历或展示
     */
    private List<EntityFormField> projectFields(String formId, List<EntityFormNode> nodes) {
        List<EntityFormField> fields = new EntityFormFieldProjection(jsonDocumentCodec)
                .derive(formId, nodes);
        for (EntityFormField field : fields) {
            if (field.getFieldId() != null) {
                com.workflow.entity.definition.infrastructure.persistence.record.EntityField entityField = fieldMapper
                        .findByIdString(field.getFieldId());
                enrichFormField(field, entityField);
            }
        }
        return fields;
    }

    /**
     * 根据实体ID和表单Key查询表单
     *
     * @param entityId 实体ID，后续用于读取实体ID与表单键时定位或关联目标
     * @param formKey 表单键，后续用于授权校验、关联或幂等去重
     * @return 符合条件的实体表单结果，供调用方继续处理
     */
    public EntityForm getByEntityIdAndFormKey(String entityId, String formKey) {
        return populateDesign(formMapper.selectByEntityIdAndFormKey(entityId, formKey));
    }

    /**
     * 根据实体编码获取实体定义
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 符合条件的{@code com.workflow.entity.definition.infrastructure.persistence.record.entity}定义结果，供调用方继续处理
     */
    public com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition getEntityByCode(
            String entityCode) {
        return entityMapper.findByEntityCode(entityCode).orElse(null);
    }

    /**
     * 根据实体 ID 解析编码，供运行态元数据权限校验使用。
     *
     * @param entityId 实体定义 ID
     * @return 实体编码
     */
    public String requireEntityCode(String entityId) {
        EntityDefinition entity = entityMapper.selectById(entityId);
        if (entity == null || !StringUtils.hasText(entity.getEntityCode())) {
            throw new IllegalArgumentException("实体不存在: " + entityId);
        }
        return entity.getEntityCode();
    }

    /**
     * 获取实体的字段列表（用于创建表单时选择）
     *
     * @param entityId 实体ID，后续用于读取实体字段时定位或关联目标
     * @return 实体字段集合，供调用方遍历或展示
     */
    public List<EntityField> getEntityFields(String entityId) {
        EntityDefinition entity = entityMapper.selectById(entityId);
        List<EntityField> fields = fieldMapper.findByEntityId(entityId);
        fields.forEach(field -> {
            if (StringUtils.hasText(field.getRefEntityId())) {
                EntityDefinition target = entityMapper.selectById(
                        field.getRefEntityId());
                field.setRefEntityCode(target == null
                        ? null : target.getEntityCode());
            }
            field.setUiConfigurable(
                    systemEntityFieldPolicy.isUiConfigurable(entity, field));
            field.setRuntimeReadable(
                    systemEntityFieldPolicy.isRuntimeReadable(entity, field));
            if (field.getRefEntityType() == null) {
                field.setRefEntityType(
                        systemEntityFieldPolicy.referenceType(
                                entity == null
                                        ? null
                                        : entity.getEntityCode(),
                                field.getFieldCode()));
            }
        });
        return fields;
    }

    /**
     * 补充表单字段的元数据（从 entity_field 查询）
     *
     * @param field 字段，作为 {@code enrichRelationMetadata} 的输入影响后续处理
     * @param entityField 实体字段，作为 {@code field.setFieldCode} 的输入影响后续处理
     */
    private void enrichFormField(EntityFormField field, EntityField entityField) {
        if (entityField == null) {
            return;
        }
        // 优先使用节点指定的 fieldCode，避免关联实体字段变更后回退到 fieldId
        if (!StringUtils.hasText(field.getFieldCode())) {
            field.setFieldCode(entityField.getFieldCode());
        }
        if (entityField.getFieldType() != null) {
            field.setFieldType(entityField.getFieldType().name());
        }
        if (entityField.getRefEntityId() != null) {
            field.setRefEntityId(entityField.getRefEntityId());
        }
        if (entityField.getRefEntityType() != null) {
            field.setRefEntityType(entityField.getRefEntityType().name());
        }
        if (entityField.getRefFieldCode() != null) {
            field.setRefFieldCode(entityField.getRefFieldCode());
        }
        if (entityField.getRefListKey() != null) {
            field.setRefListKey(entityField.getRefListKey());
        }
        enrichRelationMetadata(field, entityField);
        // 系统维护字段即使被节点配置为可编辑也必须只读，避免误导用户修改主键或审计信息。
        if (Boolean.TRUE.equals(entityField.getIsSystem())) {
            field.setIsReadonly(Boolean.TRUE.equals(entityField.getEditable()) ? 0 : 1);
        }
    }

    /**
     * 补充关系元数据；结果供调用方的后续步骤使用。
     *
     * @param field 字段，供本方法补充关系元数据时使用
     * @param entityField 实体字段，作为 {@code relationMapper.selectByParentField} 的输入影响后续处理
     */
    private void enrichRelationMetadata(EntityFormField field, EntityField entityField) {
        if (entityField.getEntityId() == null || entityField.getFieldCode() == null) {
            return;
        }
        EntityRelation relation = relationMapper.selectByParentField(entityField.getEntityId(),
                entityField.getFieldCode());
        if (relation == null) {
            return;
        }
        field.setRelationCode(relation.getRelationCode());
        field.setRelationName(relation.getRelationName());
        field.setChildEntityId(relation.getChildEntityId());
        field.setChildEntityCode(relation.getChildEntityCode());
        field.setChildRefFieldCode(relation.getChildRefFieldCode());
        field.setRelationType(relation.getRelationType() != null ? relation.getRelationType().name() : null);
        field.setCascadeDelete(relation.getCascadeDelete());
        field.setRelationRequired(relation.getRequired());
        field.setRefEntityId(relation.getChildEntityId());
        field.setRefFieldCode(relation.getChildRefFieldCode());
    }

    /**
     * 填充表单详情
     *
     * @param form 表单，作为 {@code entityMapper.selectById} 的输入影响后续处理
     */
    private void fillFormDetails(EntityForm form) {
        if (form.getEntityId() != null) {
            EntityDefinition entity = entityMapper.selectById(form.getEntityId());
            form.setEntity(entity);
        }
    }

    /**
     * 设置默认表单
     * 
     * @param formId 表单ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void setDefaultForm(String formId) {
        EntityForm form = formMapper.selectById(formId);
        if (form == null) {
            throw new RuntimeException("表单不存在");
        }
        // 设置为默认表单
        form.setIsDefault(true);
        form.setUpdateTime(LocalDateTime.now());
        formMapper.updateById(form);
        // 将同一实体下的其他表单设为非默认
        clearOtherDefaultForm(form.getEntityId(), formId);
        log.info("设置默认表单：{} (entityId={})", form.getFormName(), form.getEntityId());
    }

    /**
     * 复制表单
     * 
     * @param sourceFormId 源表单ID
     * @return 新表单
     */
    @Transactional(rollbackFor = Exception.class)
    public EntityForm copyForm(String sourceFormId) {
        return copyForm(sourceFormId, null, null);
    }

    /**
     * 复制表单，并允许调用方在创建副本前确定名称和稳定标识。
     *
     * @param sourceFormId 源表单ID
     * @param targetFormName 新表单名称，为空时生成默认名称
     * @param targetFormKey 新表单标识，为空时生成不冲突的默认标识
     * @return 新表单
     */
    @Transactional(rollbackFor = Exception.class)
    public EntityForm copyForm(
            String sourceFormId,
            String targetFormName,
            String targetFormKey) {
        // 查询源表单
        EntityForm sourceForm = formMapper.selectById(sourceFormId);
        if (sourceForm == null) {
            throw new RuntimeException("表单不存在");
        }
        // 创建新表单
        EntityForm newForm = new EntityForm();
        newForm.setEntityId(sourceForm.getEntityId());
        newForm.setFormName(StringUtils.hasText(targetFormName)
                ? targetFormName.trim()
                : sourceForm.getFormName() + " copy");
        newForm.setFormKey(StringUtils.hasText(targetFormKey)
                ? targetFormKey.trim()
                : nextCopyFormKey(sourceForm));
        newForm.setDescription(sourceForm.getDescription());
        newForm.setLayoutType(sourceForm.getLayoutType());
        newForm.setCustomComponent(sourceForm.getCustomComponent());
        newForm.setCustomComponentVersion(
                sourceForm.getCustomComponentVersion());
        newForm.setCustomComponentSnapshotVersion(
                sourceForm.getCustomComponentSnapshotVersion());
        // 生命周期数据处理属于表单定义的一部分，复制时必须保持步骤、顺序和映射完整。
        newForm.setDataSourceBindingsDocument(
                sourceForm.getDataSourceBindingsDocument());
        newForm.setViewConfig(sourceForm.getViewConfig());
        newForm.setStatus(1);
        newForm.setRevision(1);
        newForm.setCreateTime(LocalDateTime.now());
        newForm.setUpdateTime(LocalDateTime.now());
        configurationValidator.validateFormIdentity(newForm);
        validateFormKey(newForm);
        // 保存新表单
        formMapper.insert(newForm);
        List<com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode> sourceNodes = formNodeMapper
                .findByFormId(sourceFormId);
        Map<String, String> copiedIds = new HashMap<>();
        for (com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode sourceNode : sourceNodes) {
            copiedIds.put(sourceNode.getId(),
                    java.util.UUID.randomUUID().toString().replace("-", ""));
        }
        for (com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode sourceNode : sourceNodes) {
            com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode newNode = new com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode();
            BeanUtils.copyProperties(sourceNode, newNode);
            newNode.setId(copiedIds.get(sourceNode.getId()));
            newNode.setFormId(newForm.getId());
            newNode.setParentId(copiedIds.get(sourceNode.getParentId()));
            newNode.setRevision(1);
            newNode.setCreatedAt(LocalDateTime.now());
            newNode.setUpdatedAt(LocalDateTime.now());
            newNode.setDeleted(0);
            formNodeMapper.insert(newNode);
        }
        // 关联内容是表单设计的一部分；节点挂载必须改为副本节点 ID。
        viewCompositionService.copyForOwner(
                "FORM", sourceFormId, newForm.getId(), copiedIds);
        log.info("复制表单：{} -> {}", sourceForm.getFormName(), newForm.getFormName());
        // 填充详情返回
        fillFormDetails(newForm);
        newForm.setFields(getFormFields(newForm.getId()));
        newForm.setNodes(formNodeMapper.findByFormId(newForm.getId()));
        return newForm;
    }

    /**
     * 生成下一步副本表单键文本，供后续匹配或展示。
     *
     * @param sourceForm 来源表单，供本方法处理下一步副本表单键时使用
     * @return 处理后的下一步副本表单键文本，供调用方比较或展示
     */
    private String nextCopyFormKey(EntityForm sourceForm) {
        String sourceKey = StringUtils.hasText(sourceForm.getFormKey())
                ? sourceForm.getFormKey().trim()
                : "form";
        String baseKey = appendCopyKeySuffix(sourceKey, "_copy");
        if (!formMapper.existsFormKey(
                sourceForm.getEntityId(), baseKey, "")) {
            return baseKey;
        }
        for (int sequence = 2; ; sequence++) {
            String candidate = appendCopyKeySuffix(
                    sourceKey, "_copy_" + sequence);
            if (!formMapper.existsFormKey(
                    sourceForm.getEntityId(), candidate, "")) {
                return candidate;
            }
        }
    }

    /**
     * 追加副本键后缀；结果供后续流程传递或持久化。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param suffix 后缀，供本方法追加副本键后缀时使用
     * @return 追加后的副本键后缀文本，供调用方比较或展示
     */
    private String appendCopyKeySuffix(String key, String suffix) {
        int prefixLength = Math.min(
                key.length(),
                EntityFormConfigurationValidator.FORM_KEY_MAX_LENGTH
                        - suffix.length());
        return key.substring(0, prefixLength) + suffix;
    }
}
