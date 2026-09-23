package com.workflow.entity.ui.application;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.workflow.contracts.entity.ui.model.UiDataSourceUsages;
import com.workflow.core.logging.LogValue;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.form.application.EntityFormFieldProjection;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiExtensionDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiEventBindingMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiExtensionDefinition;
import com.workflow.entity.ui.infrastructure.persistence.record.UiEventBinding;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.time.LocalDateTime;

/**
 * Builds the published snapshot representation of UI event bindings.
 */
@Service
@Slf4j
public class UiEventBindingSnapshotService {

    private static final int OPERATION_SNAPSHOT_VERSION = 2;
    private static final List<String> LEGACY_PINNED_OPERATION_FIELDS = List.of(
            "operationSnapshotVersion",
            "sourceCode",
            "serviceRevision",
            "executableSnapshot",
            "definitionHash",
            "bindingOwnerType",
            "bindingOwnerId",
            "bindingTargetType",
            "bindingTargetKey");
    private static final List<String> EXTENSION_PINNED_OPERATION_FIELDS = List.of(
            "operationSnapshotVersion",
            "extensionKey",
            "extensionRevision",
            "executableSnapshot",
            "definitionHash",
            "bindingOwnerType",
            "bindingOwnerId",
            "bindingTargetType",
            "bindingTargetKey");
    private static final Set<String> ALL_PINNED_OPERATION_FIELDS = Set.of(
            "operationSnapshotVersion",
            "sourceCode",
            "serviceRevision",
            "extensionKey",
            "extensionRevision",
            "executableSnapshot",
            "definitionHash",
            "bindingOwnerType",
            "bindingOwnerId",
            "bindingTargetType",
            "bindingTargetKey");
    private static final Set<String> MUTABLE_ONLY_IDENTITY_FIELDS = Set.of(
            "serviceId", "operationCode", "serviceName", "operationName",
            "interfaceName", "providerOperationCode", "legacyServiceId");

    /**
     * 为激活期的通用接口引用校验构造副本。
     *
     * <p>完整的 FORM_BUTTON_CLICK v1 步骤已经携带独立验哈希的可执行定义，
     * 应按该不可变定义校验并从副本中剥离可变 {@code serviceId} 引用；否则旧发布
     * 会因当前服务改名、移除操作或变更作用域而无法重新激活。完全未钉版的历史
     * 步骤继续保留引用，仍由通用校验器回读当前定义。部分钉版、身份不一致或
     * 定义损坏一律拒绝，绝不降级到历史兼容路径。</p>
     *
     * @param snapshot 已通过发布内容哈希校验的 FORM 快照
     * @return 仅供通用接口引用校验使用的深拷贝
     */
    public Map<String, Object> activationReferenceSnapshot(
            Map<String, Object> snapshot) {
        Map<String, Object> copy = codec.readObject(
                codec.write(snapshot, "表单发布激活接口引用快照"),
                "表单发布激活接口引用快照");
        Object rawBindings = copy.get("eventBindings");
        if (!(rawBindings instanceof List<?> bindings)) {
            return copy;
        }
        for (Object rawBinding : bindings) {
            if (!(rawBinding instanceof Map<?, ?> binding)
                    || !UiDataSourceUsages.FORM_BUTTON_CLICK.equals(
                            normalize(text(binding.get("eventCode"))))) {
                continue;
            }
            Object rawSteps = binding.get("steps");
            if (!(rawSteps instanceof List<?> steps)) {
                continue;
            }
            for (Object rawStep : steps) {
                if (rawStep instanceof Map<?, ?> step) {
                    validateAndDetachPinnedActivationReference(
                            step, binding);
                }
            }
        }
        return copy;
    }

    private final UiEventBindingMapper bindingMapper;
    private final UiExtensionDefinitionMapper dataSourceMapper;
    private final UiInterfaceExtensionService dataSourceService;
    private final JsonDocumentCodec codec;

    /**
     *
     * @param bindingMapper 绑定映射器依赖，保存到当前对象供后续业务方法调用
     * @param dataSourceMapper 数据来源映射器依赖，保存到当前对象供后续业务方法调用
     * @param dataSourceService 延迟代理用于打断发布服务与执行授权服务之间的构造环；
     *                          只有真正构建发布快照时才解析该依赖
     * @param codec 编解码器依赖，保存到当前对象供后续业务方法调用
     */
    public UiEventBindingSnapshotService(
            UiEventBindingMapper bindingMapper,
            UiExtensionDefinitionMapper dataSourceMapper,
            @Lazy UiInterfaceExtensionService dataSourceService,
            JsonDocumentCodec codec) {
        this.bindingMapper = bindingMapper;
        this.dataSourceMapper = dataSourceMapper;
        this.dataSourceService = dataSourceService;
        this.codec = codec;
    }

    /**
     * 整理快照数据，供调用方遍历或继续处理。
     *
     * @param configType 配置类型标识，决定后续快照采用的处理分支
     * @param configId 配置ID，后续用于处理快照时定位或关联目标
     * @param entityId 实体ID，后续用于处理快照时定位或关联目标
     * @return 界面事件绑定快照集合，供调用方遍历或展示
     */
    public List<Map<String, Object>> snapshot(
            String configType,
            String configId,
            String entityId) {
        return snapshot(configType, configId, entityId, false);
    }

    /**
     * 构建事件绑定快照，并按发布阶段选择是否固定接口操作定义。
     *
     * <p>设计态快照保留可编辑引用；发布态仅把 {@code FORM_BUTTON_CLICK}
     * 的有效接口步骤转换为带独立哈希的完整可执行定义，使表单按钮的历史发布
     * 不再回读可变的接口服务表。其他事件继续沿用既有发布与执行契约。</p>
     *
     * @param configType 配置类型标识，决定后续快照采用的处理分支
     * @param configId 配置ID，后续用于处理快照时定位或关联目标
     * @param entityId 实体ID，后续用于处理快照时定位或关联目标
     * @param pinOperationReferences 固定操作引用，供本方法处理快照时使用
     * @return 界面事件绑定快照集合，供调用方遍历或展示
     */
    public List<Map<String, Object>> snapshot(
            String configType,
            String configId,
            String entityId,
            boolean pinOperationReferences) {
        return snapshot(configType, configId, entityId, pinOperationReferences, null);
    }

    /**
     * 按本次表单节点树生成草稿/发布事件快照，过滤历史遗留的孤立 FIELD 绑定。
     * 必须在解析接口和固定版本之前过滤，否则已删除字段引用的失效接口仍会阻断发布。
     * 只排除本表单的字段目标，不影响公共默认链、按钮或不可变历史快照的激活。
     *
     * @param formId 表单ID，后续用于处理快照表单时定位或关联目标
     * @param entityId 实体ID，后续用于处理快照表单时定位或关联目标
     * @param nodes 与本次表单快照相同的节点树；空列表表示全部字段已移除
     * @param pinOperationReferences 是否固定发布接口版本
     * @return 界面事件绑定快照集合，供调用方遍历或展示
     */
    public List<Map<String, Object>> snapshotForm(
            String formId,
            String entityId,
            List<EntityFormNode> nodes,
            boolean pinOperationReferences) {
        return snapshot("FORM", formId, entityId, pinOperationReferences,
                new EntityFormFieldProjection(codec).fieldEventTargetKeys(nodes));
    }

    /**
     * 整理快照数据，供调用方遍历或继续处理。
     *
     * @param configType 配置类型标识，决定后续快照采用的处理分支
     * @param configId 配置ID，后续用于处理快照时定位或关联目标
     * @param entityId 实体ID，后续用于处理快照时定位或关联目标
     * @param pinOperationReferences 固定操作引用，供本方法处理快照时使用
     * @param fieldTargetKeys 字段目标键集合，供本方法处理快照时使用
     * @return 界面事件绑定快照集合，供调用方遍历或展示
     */
    private List<Map<String, Object>> snapshot(
            String configType,
            String configId,
            String entityId,
            boolean pinOperationReferences,
            Set<String> fieldTargetKeys) {
        String normalizedConfigType = normalize(configType);
        Map<String, UiExtensionDefinition> sourceCache =
                new LinkedHashMap<>();
        return bindingMapper.findForSnapshot(
                        configType,
                        configId,
                        entityId)
                .stream()
                .filter(binding -> fieldTargetKeys == null
                        || !"FORM".equals(normalize(binding.getOwnerType()))
                        || !Objects.equals(configId, binding.getOwnerId())
                        || !"FIELD".equals(normalize(binding.getTargetType()))
                        || fieldTargetKeys.contains(normalizedTargetKey(binding.getTargetKey())))
                .filter(binding -> appliesToSnapshot(
                        binding,
                        normalizedConfigType))
                .map(binding -> snapshotValue(
                        binding,
                        normalizedConfigType,
                        sourceCache,
                        pinOperationReferences))
                .toList();
    }

    /**
     * 只快照指定所有者的本地事件绑定，供实体级配置迁移使用。
     *
     * <p>与 {@link #snapshot(String, String, String)} 不同，本方法不会混入继承绑定，
     * 避免实体绑定被重复写入每个表单或列表。</p>
     *
     * @param ownerType 归属方类型标识，决定后续快照归属方采用的处理分支
     * @param ownerId 归属方ID，后续用于处理快照归属方时定位或关联目标
     * @return 界面事件绑定快照集合，供调用方遍历或展示
     */
    public List<Map<String, Object>> snapshotOwner(
            String ownerType,
            String ownerId) {
        return bindingMapper.findByOwner(
                        normalize(ownerType), ownerId)
                .stream()
                .map(this::snapshotValue)
                .toList();
    }

    /**
     * 锁定指定所有者的全部事件绑定草稿，包含已逻辑删除记录。
     *
     * <p>FORM/LIST 的 revision 不会随事件绑定独立保存而变化，撤销草稿必须先
     * 获取此范围锁，再在同一事务内重算 canonical hash。</p>
     *
     * @param ownerType 归属方类型标识，决定后续归属方绑定集合采用的处理分支
     * @param ownerId 归属方ID，后续用于锁定归属方绑定集合时定位或关联目标
     */
    public void lockOwnerBindings(String ownerType, String ownerId) {
        if (!StringUtils.hasText(ownerId)) {
            return;
        }
        bindingMapper.findByOwnerForUpdate(
                normalize(ownerType), ownerId);
    }

    /**
     * 将发布快照中的本地绑定精确物化为当前草稿。
     *
     * <p>先物理清理 owner 范围内的草稿行，再使用发布快照中的稳定 ID 重建，
     * 避免逻辑删除后重新插入产生主键冲突或 ID 漂移。发布快照存储在独立表中，
     * 本操作不会修改任何历史发布记录。</p>
     *
     * @param configType 配置类型标识，决定后续本地绑定集合发布版本采用的处理分支
     * @param configId 配置ID，后续用于恢复本地绑定集合发布版本时定位或关联目标
     * @param snapshotBindings 快照绑定集合，供本方法恢复本地绑定集合发布版本时使用
     */
    public void restoreLocalBindingsForRelease(
            String configType,
            String configId,
            List<Map<String, Object>> snapshotBindings) {
        String normalizedType = normalize(configType);
        lockOwnerBindings(normalizedType, configId);
        bindingMapper.deleteByOwner(normalizedType, configId);
        for (Map<String, Object> value : snapshotBindings == null
                ? List.<Map<String, Object>>of()
                : snapshotBindings) {
            if (!normalizedType.equals(normalize(text(
                    value.get("ownerType"))))
                    || !configId.equals(text(value.get("ownerId")))) {
                continue;
            }
            UiEventBinding created = new UiEventBinding();
            String publishedId = text(value.get("id"));
            if (StringUtils.hasText(publishedId)) {
                created.setId(publishedId.trim());
            }
            created.setOwnerType(normalizedType);
            created.setOwnerId(configId);
            created.setTargetType(normalize(text(
                    value.get("targetType"))));
            created.setTargetKey(normalizedTargetKey(text(
                    value.get("targetKey"))));
            created.setEventCode(normalize(text(
                    value.get("eventCode"))));
            String inheritanceMode = normalize(text(
                    value.get("inheritanceMode")));
            created.setInheritanceMode(
                    StringUtils.hasText(inheritanceMode)
                            ? inheritanceMode : "INHERIT");
            created.setStepsDocument(codec.write(
                    draftSteps(
                            value.get("steps"),
                            created.getEventCode()),
                    "恢复UI事件绑定步骤"));
            created.setRevision(1);
            created.setEnabled(true);
            created.setDeleted(0);
            created.setCreatedAt(LocalDateTime.now());
            created.setUpdatedAt(LocalDateTime.now());
            bindingMapper.insert(created);
        }
    }

    /**
     * 用不可变发布快照恢复配置自身的事件绑定草稿；实体级继承绑定不受影响。
     *
     * @param configType 配置类型标识，决定后续本地绑定集合采用的处理分支
     * @param configId 配置ID，后续用于恢复本地绑定集合时定位或关联目标
     * @param snapshotBindings 快照绑定集合，供本方法恢复本地绑定集合时使用
     */
    public void restoreLocalBindings(
            String configType,
            String configId,
            List<Map<String, Object>> snapshotBindings) {
        String normalizedType = normalize(configType);
        Map<String, UiEventBinding> current =
                new LinkedHashMap<>();
        for (UiEventBinding binding : bindingMapper.findByOwner(
                normalizedType,
                configId)) {
            current.put(bindingKey(
                    binding.getTargetType(),
                    binding.getTargetKey(),
                    binding.getEventCode()), binding);
        }

        for (Map<String, Object> value : snapshotBindings == null
                ? List.<Map<String, Object>>of()
                : snapshotBindings) {
            if (!normalizedType.equals(normalize(text(
                    value.get("ownerType"))))
                    || !configId.equals(text(
                    value.get("ownerId")))) {
                continue;
            }
            String targetType = normalize(text(
                    value.get("targetType")));
            String targetKey = normalizedTargetKey(
                    text(value.get("targetKey")));
            String eventCode = normalize(text(
                    value.get("eventCode")));
            String key = bindingKey(
                    targetType,
                    targetKey,
                    eventCode);
            UiEventBinding existing = current.remove(key);
            String stepsDocument = codec.write(
                    draftSteps(value.get("steps"), eventCode),
                    "恢复UI事件绑定步骤");
            String inheritanceMode = normalize(text(
                    value.get("inheritanceMode")));
            if (existing == null) {
                UiEventBinding created = new UiEventBinding();
                created.setOwnerType(normalizedType);
                created.setOwnerId(configId);
                created.setTargetType(targetType);
                created.setTargetKey(targetKey);
                created.setEventCode(eventCode);
                created.setInheritanceMode(
                        StringUtils.hasText(inheritanceMode)
                                ? inheritanceMode : "INHERIT");
                created.setStepsDocument(stepsDocument);
                created.setRevision(1);
                created.setEnabled(true);
                created.setDeleted(0);
                created.setCreatedAt(LocalDateTime.now());
                created.setUpdatedAt(LocalDateTime.now());
                bindingMapper.insert(created);
                continue;
            }
            UpdateWrapper<UiEventBinding> update = new UpdateWrapper<>();
            update.eq("id", existing.getId())
                    .eq("deleted", 0)
                    .set("inheritance_mode",
                            StringUtils.hasText(inheritanceMode)
                                    ? inheritanceMode : "INHERIT")
                    .set("steps_document", stepsDocument)
                    .set("enabled", 1)
                    .setSql("revision = revision + 1")
                    .set("update_time", LocalDateTime.now());
            bindingMapper.update(null, update);
        }

        for (UiEventBinding stale : current.values()) {
            UpdateWrapper<UiEventBinding> update = new UpdateWrapper<>();
            update.eq("id", stale.getId())
                    .eq("deleted", 0)
                    .set("enabled", 0)
                    .setSql("revision = revision + 1")
                    .set("update_time", LocalDateTime.now());
            bindingMapper.update(null, update);
        }
    }

    /**
     * 生成绑定键文本，供后续匹配或展示。
     *
     * @param targetType 目标类型标识，决定后续绑定键采用的处理分支
     * @param targetKey 目标键，后续用于授权校验、关联或幂等去重
     * @param eventCode 事件编码，后续用于处理绑定键时定位或关联目标
     * @return 处理后的绑定键文本，供调用方比较或展示
     */
    private String bindingKey(
            String targetType,
            String targetKey,
            String eventCode) {
        return normalize(targetType)
                + "\u0000"
                + normalizedTargetKey(targetKey)
                + "\u0000"
                + normalize(eventCode);
    }

    /**
     * 生成规范化目标键文本，供后续匹配或展示。
     *
     * @param value 待处理规范化目标键的原始输入，结果供调用方继续使用
     * @return 处理后的规范化目标键文本，供调用方比较或展示
     */
    private String normalizedTargetKey(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    /**
     * 实体级事件会被表单和列表共同查询，整条绑定先按事件消费域判断是否适用。
     * 接口步骤的 FORM/LIST 投影由 snapshotValue 逐步完成，纯映射步骤因此能在
     * 两类适用快照中保留；仍有目标的本地绑定必须继续校验，不能按接口上下文静默过滤。
     *
     * @param binding 绑定，供本方法处理{@code applies}截止快照时使用
     * @param configType 配置类型标识，决定后续{@code applies}截止快照采用的处理分支
     * @return {@code applies}截止快照条件成立时为 true，否则为 false
     */
    private boolean appliesToSnapshot(
            UiEventBinding binding,
            String configType) {
        if (!"ENTITY".equals(normalize(binding.getOwnerType()))) {
            return true;
        }
        if (!UiEventBindingApplicability.appliesTo(
                normalize(binding.getEventCode()), configType)) {
            return false;
        }
        return true;
    }

    /**
     * 生成操作上下文文本，供后续匹配或展示。
     *
     * @param definition 定义，作为 {@code equals} 的输入影响后续处理
     * @param operationCode 操作编码，后续用于处理操作上下文时定位或关联目标
     * @return 处理后的操作上下文文本，供调用方比较或展示
     */
    private String operationContext(
            UiExtensionDefinition definition,
            String operationCode) {
        if (definition == null) {
            return null;
        }
        if (StringUtils.hasText(operationCode)
                && StringUtils.hasText(
                        definition.getProviderOperationCode())
                && !operationCode.equals(
                        definition.getProviderOperationCode())) {
            return null;
        }
        return normalize(definition.getInterfaceContextType());
    }

    /**
     * 整理快照值数据，供调用方遍历或继续处理。
     *
     * @param binding 绑定，作为 {@code value.put} 的输入影响后续处理
     * @return 快照值键值结果，供调用方继续处理
     */
    private Map<String, Object> snapshotValue(UiEventBinding binding) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", binding.getId());
        value.put("ownerType", binding.getOwnerType());
        value.put("ownerId", binding.getOwnerId());
        value.put("targetType", binding.getTargetType());
        value.put("targetKey", binding.getTargetKey());
        value.put("eventCode", binding.getEventCode());
        value.put("inheritanceMode", binding.getInheritanceMode());
        value.put(
                "steps",
                StringUtils.hasText(binding.getStepsDocument())
                        ? draftSteps(
                                codec.readArray(
                                        binding.getStepsDocument(),
                                        "UI事件绑定步骤"),
                                binding.getEventCode())
                        : List.of());
        value.put("revision", binding.getRevision());
        return value;
    }

    /**
     * 共享实体事件可以同时配置 FORM 与 LIST 操作；发布某一页面时只保留
     * 与该页面上下文一致的接口步骤，纯映射步骤两边保留。否则另一上下文
     * 的步骤会被发布引用校验器正确拒绝，导致共享默认事件无法发布。
     *
     * @param binding 绑定，作为 {@code pinOperation} 的输入影响后续处理
     * @param configType 配置类型标识，决定后续快照值采用的处理分支
     * @param sourceCache 来源缓存，供本方法处理快照值时使用
     * @param pinOperationReferences 固定操作引用，供本方法处理快照值时使用
     * @return 快照值键值结果，供调用方继续处理
     */
    private Map<String, Object> snapshotValue(
            UiEventBinding binding,
            String configType,
            Map<String, UiExtensionDefinition> sourceCache,
            boolean pinOperationReferences) {
        Map<String, Object> value = snapshotValue(binding);
        List<Map<String, Object>> steps = new ArrayList<>();
        if (value.get("steps") instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> step)
                        || "ENTITY".equals(normalize(binding.getOwnerType()))
                        && !appliesToConfig(
                                step, configType, sourceCache)) {
                    continue;
                }
                Map<String, Object> copy = new LinkedHashMap<>();
                step.forEach((key, child) ->
                        copy.put(String.valueOf(key), child));
                if (pinOperationReferences
                        && UiDataSourceUsages.FORM_BUTTON_CLICK.equals(
                                normalize(binding.getEventCode()))) {
                    pinOperation(copy, binding, configType);
                }
                steps.add(copy);
            }
        }
        value.put("steps", steps);
        return value;
    }

    /**
     * 表单按钮发布快照只信任当前权威服务记录和 Provider 制品身份。
     *
     * @param step 步骤，作为 {@code firstText} 的输入影响后续处理
     * @param binding 绑定，作为 {@code step.put} 的输入影响后续处理
     * @param configType 配置类型标识，决定后续固定操作采用的处理分支
     */
    private void pinOperation(
            Map<String, Object> step,
            UiEventBinding binding,
            String configType) {
        String extensionId = firstText(
                step.get("extensionId"), step.get("serviceId"));
        if (!StringUtils.hasText(extensionId)) {
            return;
        }
        UiInterfaceExtensionService.PublishedOperationSnapshot operation =
                dataSourceService.freezeExtension(extensionId);
        // 表单按钮 Provider 只能做无副作用的读取、校验和结果映射；实体写入必须
        // 继续走平台默认处理或受控命令计划，外部投递则由业务事务写 Outbox。
        dataSourceService.validatePinnedReadExtension(
                operation.document(),
                operation.hash(),
                operation.extensionId(),
                operation.extensionKey(),
                operation.extensionRevision(),
                configType);
        step.put("operationSnapshotVersion", OPERATION_SNAPSHOT_VERSION);
        step.put("extensionId", operation.extensionId());
        // v2 的宿主引用只保存 extensionId，Provider 内部路由在验哈希快照中。
        step.remove("serviceId");
        step.remove("operationCode");
        step.remove("sourceCode");
        step.remove("serviceRevision");
        step.put("extensionKey", operation.extensionKey());
        step.put("extensionRevision", operation.extensionRevision());
        step.put("executableSnapshot", operation.document());
        step.put("definitionHash", operation.hash());
        step.put("bindingOwnerType", normalize(binding.getOwnerType()));
        step.put("bindingOwnerId", binding.getOwnerId());
        step.put("bindingTargetType", normalize(binding.getTargetType()));
        step.put("bindingTargetKey", normalizedTargetKey(
                binding.getTargetKey()));
    }

    /**
     * 校验不可变操作定义和来源绑定身份后，只在临时副本中移除当前服务引用。
     *
     * @param rawStep 原始步骤，供本方法校验与{@code detach}固定{@code activation}引用时使用
     * @param binding 绑定，作为 {@code requirePinnedBindingIdentity} 的输入影响后续处理
     */
    @SuppressWarnings("unchecked")
    private void validateAndDetachPinnedActivationReference(
            Map<?, ?> rawStep,
            Map<?, ?> binding) {
        Map<String, Object> step = (Map<String, Object>) rawStep;
        boolean hasVersion = step.containsKey("operationSnapshotVersion");
        boolean hasPinnedField = ALL_PINNED_OPERATION_FIELDS.stream()
                .filter(field -> !"operationSnapshotVersion".equals(field))
                .anyMatch(step::containsKey);
        if (!hasVersion) {
            if (hasPinnedField) {
                throw new IllegalArgumentException(
                        "表单按钮发布步骤包含不完整的钉版操作字段");
            }
            return;
        }
        Integer version = positiveInteger(
                step.get("operationSnapshotVersion"));
        List<String> expectedFields = Integer.valueOf(1).equals(version)
                ? LEGACY_PINNED_OPERATION_FIELDS
                : EXTENSION_PINNED_OPERATION_FIELDS;
        if (!Set.of(1, OPERATION_SNAPSHOT_VERSION).contains(version)
                || !expectedFields.stream().allMatch(step::containsKey)) {
            throw new IllegalArgumentException(
                    "表单按钮发布步骤的钉版操作版本或字段不完整");
        }
        String document = requiredText(step, "executableSnapshot");
        String hash = requiredText(step, "definitionHash");
        requirePinnedBindingIdentity(step, binding);
        if (Integer.valueOf(1).equals(version)) {
            String sourceCode = requiredText(step, "sourceCode");
            Integer serviceRevision = positiveInteger(
                    step.get("serviceRevision"));
            if (serviceRevision == null) {
                throw new IllegalArgumentException(
                        "表单按钮发布步骤的历史接口服务修订号无效");
            }
            String serviceId = requiredText(step, "serviceId");
            String operationCode = requiredText(step, "operationCode");
            dataSourceService.validatePinnedReadOperation(
                    document,
                    hash,
                    serviceId,
                    sourceCode,
                    serviceRevision,
                    operationCode,
                    "FORM");
            step.remove("serviceId");
        } else {
            String extensionKey = requiredText(step, "extensionKey");
            Integer extensionRevision = positiveInteger(
                    step.get("extensionRevision"));
            if (extensionRevision == null) {
                throw new IllegalArgumentException(
                        "表单按钮发布步骤的接口扩展修订号无效");
            }
            String extensionId = requiredText(step, "extensionId");
            dataSourceService.validatePinnedReadExtension(
                    document,
                    hash,
                    extensionId,
                    extensionKey,
                    extensionRevision,
                    "FORM");
            step.remove("extensionId");
        }
    }

    /**
     * 校验并获取固定绑定身份；不满足约束时阻止后续处理。
     *
     * @param step 步骤，作为 {@code equals} 的输入影响后续处理
     * @param binding 绑定，供本方法校验并获取固定绑定身份时使用
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void requirePinnedBindingIdentity(
            Map<String, Object> step,
            Map<?, ?> binding) {
        boolean matches = Objects.equals(
                        normalize(text(step.get("bindingOwnerType"))),
                        normalize(text(binding.get("ownerType"))))
                && Objects.equals(
                        text(step.get("bindingOwnerId")),
                        text(binding.get("ownerId")))
                && Objects.equals(
                        normalize(text(step.get("bindingTargetType"))),
                        normalize(text(binding.get("targetType"))))
                && Objects.equals(
                        normalizedTargetKey(text(
                                step.get("bindingTargetKey"))),
                        normalizedTargetKey(text(
                                binding.get("targetKey"))));
        if (!matches) {
            throw new IllegalArgumentException(
                    "表单按钮发布步骤的来源绑定身份不完整或不匹配");
        }
    }

    /**
     * 生成必填文本文本，供后续匹配或展示。
     *
     * @param value 待处理必填文本的原始输入，结果供调用方继续使用
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @return 处理后的必填文本文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private String requiredText(
            Map<String, Object> value,
            String key) {
        String result = text(value.get(key));
        if (!StringUtils.hasText(result)) {
            throw new IllegalArgumentException(
                    "表单按钮发布步骤缺少钉版字段: " + key);
        }
        return result;
    }

    /**
     * 处理正数整数，并将结果传给后续步骤。
     *
     * @param value 待处理正数整数的原始输入，结果供调用方继续使用
     * @return 处理后的正数整数结果，供调用方继续处理
     */
    private Integer positiveInteger(Object value) {
        if (value instanceof Number number
                && number.doubleValue() == number.intValue()
                && number.intValue() > 0) {
            return number.intValue();
        }
        try {
            int result = Integer.parseInt(text(value));
            return result > 0 ? result : null;
        } catch (NumberFormatException | NullPointerException ignored) {
            return null;
        }
    }

    /**
     * 所有事件恢复为可编辑草稿时都只保留 extensionId 和业务映射/策略。
     *
     * @param rawSteps 原始步骤集合，供本方法处理草稿步骤集合时使用
     * @param eventCode 事件编码，后续用于处理草稿步骤集合时定位或关联目标
     * @return 界面事件绑定快照集合，供调用方遍历或展示
     */
    private List<Map<String, Object>> draftSteps(
            Object rawSteps,
            String eventCode) {
        if (!(rawSteps instanceof List<?> steps)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object raw : steps) {
            if (!(raw instanceof Map<?, ?> step)) {
                continue;
            }
            Map<String, Object> copy = new LinkedHashMap<>();
            step.forEach((key, value) ->
                    copy.put(String.valueOf(key), value));
            // 可编辑绑定不保存任何发布钉定字段；FORM_BUTTON_CLICK 发布时会
            // 再从权威扩展定义生成 v2 快照，其他事件也不能透传旧服务身份。
            normalizeDraftInterfaceReference(copy);
            ALL_PINNED_OPERATION_FIELDS.forEach(copy::remove);
            MUTABLE_ONLY_IDENTITY_FIELDS.forEach(copy::remove);
            result.add(copy);
        }
        return List.copyOf(result);
    }

    /**
     * 判断{@code applies}截止配置条件是否成立，供调用方选择后续分支。
     *
     * @param step 步骤，作为 {@code firstText} 的输入影响后续处理
     * @param configType 配置类型标识，决定后续{@code applies}截止配置采用的处理分支
     * @param sourceCache 来源缓存，供本方法处理{@code applies}截止配置时使用
     * @return {@code applies}截止配置条件成立时为 true，否则为 false
     */
    private boolean appliesToConfig(
            Map<?, ?> step,
            String configType,
            Map<String, UiExtensionDefinition> sourceCache) {
        String serviceId = firstText(
                step.get("extensionId"), step.get("serviceId"));
        String operationCode = text(step.get("operationCode"));
        if (!StringUtils.hasText(serviceId)) {
            return true;
        }
        String cacheKey = serviceId + "\u0000" + text(operationCode);
        UiExtensionDefinition definition = sourceCache.computeIfAbsent(
                cacheKey,
                ignored -> {
                    try {
                        return dataSourceService.requireExecutableDefinition(
                                serviceId, operationCode);
                    } catch (RuntimeException exception) {
                        return null;
                    }
                });
        String context = operationContext(definition, operationCode);
        // 缺失或损坏的引用继续进入快照，由发布校验器给出精确错误。
        return !StringUtils.hasText(context)
                || Objects.equals(configType, context);
    }

    /**
     * 恢复或导出为可编辑草稿时，历史 pair 也立即规范化为 extensionId。
     *
     * @param step 步骤，作为 {@code firstText} 的输入影响后续处理
     */
    private void normalizeDraftInterfaceReference(
            Map<String, Object> step) {
        String referenceId = firstText(
                step.get("extensionId"), step.get("serviceId"));
        if (!StringUtils.hasText(referenceId)) {
            return;
        }
        UiExtensionDefinition definition = dataSourceService
                .resolveDefinitionReference(
                        referenceId, text(step.get("operationCode")));
        step.put("extensionId", definition.getId());
        step.remove("serviceId");
        step.remove("operationCode");
    }

    /**
     * 按候选顺序取首个非空文本，供后续匹配或展示使用。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的首个文本文本，供调用方比较或展示
     */
    private String firstText(Object... values) {
        for (Object value : values) {
            if (StringUtils.hasText(text(value))) {
                return text(value);
            }
        }
        return null;
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 规范化输入值，确保后续比较和持久化使用一致格式。
     *
     * @param value 待规范化界面事件绑定快照的原始输入，结果供调用方继续使用
     * @return 规范化后的界面事件绑定快照文本，供调用方比较或展示
     */
    private String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT)
                : "";
    }
}
