package com.workflow.entity.ui.application;

import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.core.logging.LogValue;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.definition.application.EntityDefinitionAccessPolicy;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.ui.api.response.UiDataSourceReferenceContextDTO;
import com.workflow.entity.ui.api.response.UiDataSourceReferenceDTO;
import com.workflow.entity.ui.api.response.UiDataSourceReferenceEffectiveStepDTO;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiDataSourceDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiEventBindingMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import com.workflow.entity.ui.infrastructure.persistence.record.UiDataSourceDefinition;
import com.workflow.entity.ui.infrastructure.persistence.record.UiEventBinding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 反查接口服务在事件绑定草稿及激活发布版本中的引用，并解释发布运行态。
 *
 * <p>引用集合取 {@code ui_event_binding} 草稿与经过完整性校验的 FORM/LIST
 * 激活发布快照并集；是否进入最终执行链只按发布快照计算。这样既不会把
 * 草稿误报为线上配置，也不会漏掉已从草稿删除但仍在线上的旧引用。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UiDataSourceReferenceService {

    private static final String FORM = "FORM";
    private static final String LIST = "LIST";
    private static final String ENTITY = "ENTITY";

    private final UiDataSourceDefinitionMapper dataSourceMapper;
    private final UiEventBindingMapper bindingMapper;
    private final EntityDefinitionMapper entityMapper;
    private final EntityFormMapper formMapper;
    private final EntityListConfigMapper listMapper;
    private final UiConfigReleaseMapper releaseMapper;
    private final UiConfigReleaseService releaseService;
    private final UiConfigurationAccessService accessService;
    private final EntityDefinitionAccessPolicy entityAccessPolicy;
    private final JsonDocumentCodec codec;
    private final UiConfigSnapshotSupport snapshotSupport;

    /**
     * 查询一个接口服务的全部草稿及激活发布事件步骤引用。
     *
     * <p>结果按绑定和步骤拆行；实体默认绑定可能对应多个发布页面，逐页面
     * 状态保存在 contexts 中。无权访问或已经不存在的 owner 会被过滤。</p>
     *
     * @param serviceId 接口服务主键
     * @return 可访问的事件步骤引用
     */
    public List<UiDataSourceReferenceDTO> references(String serviceId) {
        if (!StringUtils.hasText(serviceId)) {
            throw new IllegalArgumentException("接口服务ID不能为空");
        }
        String normalizedServiceId = serviceId.trim();
        UiDataSourceDefinition service = dataSourceMapper.selectById(
                normalizedServiceId);
        if (service == null || Objects.equals(service.getDeleted(), 1)) {
            throw new IllegalArgumentException(
                    "接口服务不存在: " + normalizedServiceId);
        }

        Map<String, UiDataSourceDefinition> sourceCache =
                new LinkedHashMap<>();
        sourceCache.put(normalizedServiceId, service);
        Map<String, Map<String, Map<String, Object>>> operationCache =
                new LinkedHashMap<>();
        Map<String, Optional<OwnerMetadata>> ownerCache =
                new LinkedHashMap<>();
        Map<String, Boolean> accessCache = new LinkedHashMap<>();
        Map<String, Optional<UiConfigRelease>> releaseCache =
                new LinkedHashMap<>();
        Map<String, Optional<Map<String, Object>>> snapshotCache =
                new LinkedHashMap<>();

        String encodedServiceId = codec.write(
                normalizedServiceId, "接口服务引用ID");
        List<UiDataSourceReferenceDTO> result = new ArrayList<>();
        Set<String> matchedPublishedReferenceKeys = new LinkedHashSet<>();
        for (UiEventBinding binding :
                bindingMapper.findDraftReferenceCandidates(
                        "\"serviceId\":" + encodedServiceId,
                        "\"serviceId\": " + encodedServiceId)) {
            List<Map<String, Object>> steps = readSteps(binding);
            if (steps.stream().noneMatch(step -> Objects.equals(
                    normalizedServiceId,
                    text(step.get("serviceId"))))) {
                continue;
            }
            if (!canReadOwner(binding, accessCache)) {
                continue;
            }
            Optional<OwnerMetadata> owner = ownerCache.computeIfAbsent(
                    ownerKey(binding.getOwnerType(), binding.getOwnerId()),
                    ignored -> ownerMetadata(binding));
            if (owner.isEmpty()) {
                continue;
            }
            for (int index = 0; index < steps.size(); index++) {
                Map<String, Object> step = steps.get(index);
                if (!Objects.equals(
                        normalizedServiceId,
                        text(step.get("serviceId")))) {
                    continue;
                }
                String operationCode = text(step.get("operationCode"));
                Map<String, Object> operation = operation(
                        service,
                        operationCode,
                        operationCache);
                List<ConsumerContext> consumers = consumerContexts(
                        binding,
                        owner.get(),
                        step,
                        sourceCache,
                        operationCache,
                        accessCache);
                List<UiDataSourceReferenceContextDTO> contexts =
                        resolveContexts(
                                binding,
                                steps,
                                step,
                                index,
                                consumers,
                                sourceCache,
                                operationCache,
                                releaseCache,
                                snapshotCache,
                                matchedPublishedReferenceKeys);
                AggregateStatus aggregate = aggregate(contexts, true);
                result.add(reference(
                        service,
                        operation,
                        binding,
                        owner.get(),
                        step,
                        index,
                        contexts,
                        aggregate,
                        true,
                        null));
            }
        }
        result.addAll(publishedOnlyReferences(
                service,
                encodedServiceId,
                matchedPublishedReferenceKeys,
                sourceCache,
                operationCache,
                ownerCache,
                accessCache,
                snapshotCache));
        result.sort(Comparator
                .comparing(UiDataSourceReferenceDTO::getOwnerType)
                .thenComparing(UiDataSourceReferenceDTO::getOwnerName)
                .thenComparing(UiDataSourceReferenceDTO::getEventCode)
                .thenComparingInt(UiDataSourceReferenceDTO::getStepIndex));
        return List.copyOf(result);
    }

    private UiDataSourceReferenceDTO reference(
            UiDataSourceDefinition service,
            Map<String, Object> operation,
            UiEventBinding binding,
            OwnerMetadata owner,
            Map<String, Object> step,
            int stepIndex,
            List<UiDataSourceReferenceContextDTO> contexts,
            AggregateStatus aggregate,
            boolean draftPresent,
            String publishedOnlyIdentity) {
        String stepCode = blankToNull(text(step.get("stepCode")));
        // 无 stepCode 的 ENTITY 步骤在 FORM/LIST 投影后可能都位于索引 0。
        // 发布态专用逻辑身份包含操作与出现序号，避免前端 row-key 冲突。
        String referenceId = draftPresent
                ? binding.getId()
                        + ":"
                        + (StringUtils.hasText(stepCode)
                                ? stepCode : stepIndex)
                : "published:" + publishedOnlyIdentity;
        String ownerType = normalize(binding.getOwnerType());
        String targetType = normalizeDefault(
                binding.getTargetType(), "OWNER");
        String inheritanceMode = normalizeDefault(
                binding.getInheritanceMode(), "INHERIT");
        String configName = owner.ownerName();
        return UiDataSourceReferenceDTO.builder()
                .referenceId(referenceId)
                .referenceType("EVENT_BINDING")
                .usageCode(normalize(binding.getEventCode()))
                .bindingId(binding.getId())
                .draftPresent(draftPresent)
                .draftRevision(draftPresent
                        ? binding.getRevision() : null)
                .bindingEnabled(!Boolean.FALSE.equals(binding.getEnabled()))
                .serviceId(service.getId())
                .serviceCode(service.getSourceCode())
                .serviceName(service.getSourceName())
                .operationCode(text(step.get("operationCode")))
                .operationName(firstText(
                        operation.get("name"),
                        step.get("operationCode")))
                .operationKind(normalize(text(operation.get("kind"))))
                .operationContextType(normalize(
                        text(operation.get("contextType"))))
                .scopeType(ownerType)
                .scopeId(binding.getOwnerId())
                .ownerType(ownerType)
                .ownerId(binding.getOwnerId())
                .ownerName(owner.ownerName())
                .configType(ownerType)
                .configId(binding.getOwnerId())
                .configName(configName)
                .entityId(owner.entity().getId())
                .entityCode(owner.entity().getEntityCode())
                .entityName(owner.entity().getEntityName())
                .formId(owner.form() == null
                        ? null : owner.form().getId())
                .formKey(owner.form() == null
                        ? null : owner.form().getFormKey())
                .formName(owner.form() == null
                        ? null : owner.form().getFormName())
                .listId(owner.list() == null
                        ? null : owner.list().getId())
                .listKey(owner.list() == null
                        ? null : owner.list().getListKey())
                .listName(owner.list() == null
                        ? null : owner.list().getListName())
                .targetType(targetType)
                .targetKey(normalizedTargetKey(binding.getTargetKey()))
                .targetName("OWNER".equals(targetType)
                        ? owner.ownerName()
                        : blankToNull(binding.getTargetKey()))
                .eventCode(normalize(binding.getEventCode()))
                .inheritanceMode(inheritanceMode)
                .inheritanceSource(inheritanceSource(binding))
                .stepIndex(stepIndex)
                .stepCode(stepCode)
                .stepName(blankToNull(text(step.get("name"))))
                .stepOrder(integer(step.get("order"), stepIndex * 10))
                .stepStrategy(normalizeDefault(
                        text(step.get("strategy")), "BEFORE"))
                .failurePolicy(normalizeDefault(
                        text(step.get("failurePolicy")), "STOP"))
                .publicationStatus(aggregate.publicationStatus())
                .publicationReason(aggregate.publicationReason())
                .lifecycleStatus(aggregate.lifecycleStatus())
                .published(aggregate.publishedContextCount() > 0)
                .draftMatchesPublished(
                        aggregate.draftMatchesPublished())
                .effectiveStatus(aggregate.effectiveStatus())
                .effectiveReason(aggregate.effectiveReason())
                .effective(aggregate.effective())
                .effectiveContexts(aggregate.effectiveContexts())
                .activeReleaseId(aggregate.activeReleaseId())
                .activeReleaseVersion(aggregate.activeReleaseVersion())
                .relevantContextCount(contexts.size())
                .publishedContextCount(
                        aggregate.publishedContextCount())
                .effectiveContextCount(
                        aggregate.effectiveContextCount())
                .contexts(contexts)
                .build();
    }

    /**
     * 补充只存在于 ACTIVE 发布快照、已经从当前草稿删除或改绑的引用。
     *
     * <p>发布记录先以 JSON 属性片段缩小候选，再做快照完整性校验和
     * serviceId 精确匹配。实体默认绑定会把多个 FORM/LIST 发布上下文合并
     * 为一条 PUBLISHED_ONLY 引用。</p>
     */
    private List<UiDataSourceReferenceDTO> publishedOnlyReferences(
            UiDataSourceDefinition service,
            String encodedServiceId,
            Set<String> matchedPublishedReferenceKeys,
            Map<String, UiDataSourceDefinition> sourceCache,
            Map<String, Map<String, Map<String, Object>>> operationCache,
            Map<String, Optional<OwnerMetadata>> ownerCache,
            Map<String, Boolean> accessCache,
            Map<String, Optional<Map<String, Object>>> snapshotCache) {
        List<UiConfigRelease> candidateRows =
                releaseMapper.findActiveReferenceCandidates(
                        "\"serviceId\":" + encodedServiceId,
                        "\"serviceId\": " + encodedServiceId);
        if (candidateRows == null || candidateRows.isEmpty()) {
            return List.of();
        }

        // 历史异常数据可能同时存在多条 ACTIVE；与 findActive 一致，仅取
        // 每个配置版本号最大的第一条，避免把失效快照误报为线上引用。
        Map<String, UiConfigRelease> releases = new LinkedHashMap<>();
        for (UiConfigRelease release : candidateRows) {
            if (release != null
                    && Set.of(FORM, LIST).contains(normalize(
                            release.getConfigType()))) {
                releases.putIfAbsent(
                        ownerKey(
                                release.getConfigType(),
                                release.getConfigId()),
                        release);
            }
        }

        Map<String, PublishedOnlyReference> references =
                new LinkedHashMap<>();
        for (UiConfigRelease release : releases.values()) {
            String type = normalize(release.getConfigType());
            if (!canReadConsumer(
                    type, release.getConfigId(), accessCache)) {
                continue;
            }
            ConsumerContext consumer = publishedConsumerContext(release);
            if (consumer == null) {
                continue;
            }
            Optional<Map<String, Object>> snapshot =
                    snapshotCache.computeIfAbsent(
                            release.getId(),
                            ignored -> verifiedSnapshot(release));
            if (snapshot.isEmpty()) {
                continue;
            }
            List<Map<String, Object>> bindings = mapList(
                    snapshot.get().get("eventBindings"));
            for (Map<String, Object> bindingValue : bindings) {
                UiEventBinding binding = publishedBinding(bindingValue);
                if (!canReadOwner(binding, accessCache)) {
                    continue;
                }
                Optional<OwnerMetadata> owner = ownerCache.computeIfAbsent(
                        ownerKey(
                                binding.getOwnerType(),
                                binding.getOwnerId()),
                        ignored -> ownerMetadata(binding));
                if (owner.isEmpty()) {
                    continue;
                }
                List<Map<String, Object>> steps = mapList(
                        bindingValue.get("steps"));
                for (int index = 0; index < steps.size(); index++) {
                    Map<String, Object> step = steps.get(index);
                    if (!Objects.equals(
                            service.getId(), text(step.get("serviceId")))) {
                        continue;
                    }
                    String identity = referenceIdentity(
                            binding, steps, step, index);
                    String publishedIdentity = publishedReferenceIdentity(
                            release, bindingValue, index);
                    if (matchedPublishedReferenceKeys.contains(
                            publishedIdentity)) {
                        continue;
                    }
                    PublishedOnlyReference reference = references.get(identity);
                    if (reference == null) {
                        reference = new PublishedOnlyReference(
                                identity,
                                binding,
                                owner.get(),
                                step,
                                index,
                                new ArrayList<>());
                        references.put(identity, reference);
                    }
                    reference.contexts().add(publishedOnlyContext(
                            bindingValue,
                            step,
                            index,
                            consumer,
                            release,
                            bindings));
                }
            }
        }

        List<UiDataSourceReferenceDTO> result = new ArrayList<>();
        for (PublishedOnlyReference item : references.values()) {
            Map<String, Object> operation = operation(
                    sourceCache.get(service.getId()),
                    text(item.step().get("operationCode")),
                    operationCache);
            List<UiDataSourceReferenceContextDTO> contexts = List.copyOf(
                    item.contexts());
            AggregateStatus aggregate = aggregate(contexts, false);
            result.add(reference(
                    service,
                    operation,
                    item.binding(),
                    item.owner(),
                    item.step(),
                    item.stepIndex(),
                    contexts,
                    aggregate,
                    false,
                    item.identity()));
        }
        return List.copyOf(result);
    }

    private ConsumerContext publishedConsumerContext(
            UiConfigRelease release) {
        if (FORM.equals(normalize(release.getConfigType()))) {
            EntityForm form = formMapper.selectById(release.getConfigId());
            if (form == null) {
                return null;
            }
            EntityDefinition entity = entityMapper.selectById(
                    form.getEntityId());
            return entity == null ? null : context(form, entity);
        }
        EntityListConfig list = listMapper.selectById(
                release.getConfigId());
        if (list == null) {
            return null;
        }
        EntityDefinition entity = entityMapper.selectById(
                list.getEntityId());
        return entity == null ? null : context(list, entity);
    }

    private UiDataSourceReferenceContextDTO publishedOnlyContext(
            Map<String, Object> binding,
            Map<String, Object> step,
            int stepIndex,
            ConsumerContext context,
            UiConfigRelease release,
            List<Map<String, Object>> bindings) {
        String stateConflict = !Objects.equals(
                        context.activeReleaseId(), release.getId())
                ? context.type()
                        + " 草稿的 activeReleaseId 与 ACTIVE 发布记录不一致"
                : null;
        PublishedStepMatch match = new PublishedStepMatch(
                binding, step, stepIndex, "PUBLISHED_ONLY");
        EffectiveEvaluation evaluation = evaluateEffective(
                bindings, context, publishedBinding(binding), match);
        Boolean publishedEffective = stateConflict != null
                ? null : evaluation.effective();
        String effectiveStatus;
        String effectiveReason;
        if (stateConflict != null) {
            effectiveStatus = "UNKNOWN";
            effectiveReason = stateConflict;
        } else if (!evaluation.valid()) {
            effectiveStatus = "INVALID_CHAIN";
            effectiveReason =
                    "最终执行链包含多个 REPLACE 步骤，运行时会拒绝执行";
        } else if (evaluation.targetDependent()) {
            effectiveStatus = "PARTIAL";
            effectiveReason = targetDependentReason(
                    evaluation, false);
        } else if (Boolean.TRUE.equals(publishedEffective)) {
            effectiveStatus = "ACTIVE";
            effectiveReason = "该步骤仅存在于线上版本，并已进入最终生效链";
        } else {
            effectiveStatus = "SHADOWED";
            effectiveReason =
                    "该步骤仅存在于线上版本，但被下级继承规则遮蔽";
        }
        return UiDataSourceReferenceContextDTO.builder()
                .configType(context.type())
                .configId(context.id())
                .configKey(context.key())
                .configName(context.name())
                .releaseId(release.getId())
                .releaseVersion(release.getVersion())
                .activeReleasePresent(true)
                .publicationStatus("PUBLISHED_ONLY")
                .publicationReason("该步骤仅存在于激活发布快照，当前草稿已删除或改绑")
                .published(true)
                .draftMatchesPublished(null)
                .draftStepPublished(false)
                .draftStepEffective(null)
                .publishedStepEffective(publishedEffective)
                .publishedMatchBasis("PUBLISHED_ONLY")
                .effectiveStatus(effectiveStatus)
                .effectiveReason(effectiveReason)
                .effectiveChain(effectiveSteps(
                        evaluation.representative().steps()))
                .build();
    }

    private UiEventBinding publishedBinding(
            Map<String, Object> value) {
        UiEventBinding binding = new UiEventBinding();
        binding.setId(text(value.get("id")));
        binding.setOwnerType(normalize(text(value.get("ownerType"))));
        binding.setOwnerId(text(value.get("ownerId")));
        binding.setTargetType(normalizeDefault(
                text(value.get("targetType")), "OWNER"));
        binding.setTargetKey(normalizedTargetKey(
                text(value.get("targetKey"))));
        binding.setEventCode(normalize(text(value.get("eventCode"))));
        binding.setInheritanceMode(normalizeDefault(
                text(value.get("inheritanceMode")), "INHERIT"));
        binding.setStepsDocument(codec.write(
                mapList(value.get("steps")), "发布事件绑定步骤"));
        binding.setEnabled(true);
        binding.setDeleted(0);
        return binding;
    }

    /**
     * owner 访问校验与事件绑定写入口保持一致；查询时过滤不可访问对象，
     * 不通过错误信息泄露其它实体、表单或列表的存在性。
     */
    private boolean canReadOwner(
            UiEventBinding binding,
            Map<String, Boolean> cache) {
        String key = ownerKey(
                binding.getOwnerType(), binding.getOwnerId());
        return cache.computeIfAbsent(key, ignored -> {
            try {
                switch (normalize(binding.getOwnerType())) {
                    case FORM -> accessService.requireFormAccess(
                            binding.getOwnerId());
                    case LIST -> accessService.requireListAccess(
                            binding.getOwnerId());
                    case ENTITY -> {
                        accessService.requireGlobalConfigurationAccess();
                        entityAccessPolicy.requireDynamicById(
                                binding.getOwnerId());
                    }
                    default -> {
                        return false;
                    }
                }
                return true;
            } catch (BusinessForbiddenException
                    | BusinessConflictException
                    | IllegalArgumentException exception) {
                return false;
            }
        });
    }

    private boolean canReadConsumer(
            String type,
            String id,
            Map<String, Boolean> cache) {
        String key = ownerKey(type, id);
        return cache.computeIfAbsent(key, ignored -> {
            try {
                if (FORM.equals(type)) {
                    accessService.requireFormAccess(id);
                } else if (LIST.equals(type)) {
                    accessService.requireListAccess(id);
                } else {
                    return false;
                }
                return true;
            } catch (BusinessForbiddenException
                    | BusinessConflictException
                    | IllegalArgumentException exception) {
                return false;
            }
        });
    }

    private Optional<OwnerMetadata> ownerMetadata(
            UiEventBinding binding) {
        String ownerType = normalize(binding.getOwnerType());
        if (ENTITY.equals(ownerType)) {
            EntityDefinition entity = entityMapper.selectById(
                    binding.getOwnerId());
            return entity == null
                    ? Optional.empty()
                    : Optional.of(new OwnerMetadata(
                            entity,
                            null,
                            null,
                            display(entity.getEntityName(), entity.getId())));
        }
        if (FORM.equals(ownerType)) {
            EntityForm form = formMapper.selectById(binding.getOwnerId());
            if (form == null) {
                return Optional.empty();
            }
            EntityDefinition entity = entityMapper.selectById(
                    form.getEntityId());
            return entity == null
                    ? Optional.empty()
                    : Optional.of(new OwnerMetadata(
                            entity,
                            form,
                            null,
                            display(form.getFormName(), form.getId())));
        }
        if (LIST.equals(ownerType)) {
            EntityListConfig list = listMapper.selectById(
                    binding.getOwnerId());
            if (list == null) {
                return Optional.empty();
            }
            EntityDefinition entity = entityMapper.selectById(
                    list.getEntityId());
            return entity == null
                    ? Optional.empty()
                    : Optional.of(new OwnerMetadata(
                            entity,
                            null,
                            list,
                            display(list.getListName(), list.getId())));
        }
        return Optional.empty();
    }

    private List<ConsumerContext> consumerContexts(
            UiEventBinding binding,
            OwnerMetadata owner,
            Map<String, Object> referenceStep,
            Map<String, UiDataSourceDefinition> sourceCache,
            Map<String, Map<String, Map<String, Object>>> operationCache,
            Map<String, Boolean> accessCache) {
        String ownerType = normalize(binding.getOwnerType());
        if (FORM.equals(ownerType)) {
            return List.of(context(owner.form(), owner.entity()));
        }
        if (LIST.equals(ownerType)) {
            return List.of(context(owner.list(), owner.entity()));
        }

        // 与发布快照构建逻辑一致：实体默认绑定按整条链中引用操作的
        // contextType 决定进入 FORM、LIST 或两类页面快照。
        Set<String> eventContexts =
                UiEventBindingApplicability.contextsForEvent(
                        normalize(binding.getEventCode()));
        String referenceContext = referenceOperationContext(
                referenceStep, sourceCache, operationCache);
        // 旧数据可能保存了 ENTITY 操作；它不会进入现有页面发布快照，但仍按
        // 事件消费域列出页面，以明确展示“草稿存在、线上未发布”的真实状态。
        Set<String> applicableContexts = !Set.of(FORM, LIST)
                .contains(referenceContext)
                ? eventContexts
                : eventContexts.stream()
                        .filter(referenceContext::equals)
                        .collect(java.util.stream.Collectors.toCollection(
                                LinkedHashSet::new));
        boolean includeForms = applicableContexts.contains(FORM);
        boolean includeLists = applicableContexts.contains(LIST);
        List<ConsumerContext> result = new ArrayList<>();
        if (includeForms) {
            for (EntityForm form : formMapper.selectByEntityId(
                    owner.entity().getId())) {
                if (canReadConsumer(FORM, form.getId(), accessCache)) {
                    result.add(context(form, owner.entity()));
                }
            }
        }
        if (includeLists) {
            for (EntityListConfig list : listMapper.findByEntityId(
                    owner.entity().getId())) {
                if (canReadConsumer(LIST, list.getId(), accessCache)) {
                    result.add(context(list, owner.entity()));
                }
            }
        }
        result.sort(Comparator
                .comparing(ConsumerContext::type)
                .thenComparing(ConsumerContext::name)
                .thenComparing(ConsumerContext::id));
        return List.copyOf(result);
    }

    private String referenceOperationContext(
            Map<String, Object> step,
            Map<String, UiDataSourceDefinition> sourceCache,
            Map<String, Map<String, Map<String, Object>>> operationCache) {
        String sourceId = text(step.get("serviceId"));
        String operationCode = text(step.get("operationCode"));
        if (!StringUtils.hasText(sourceId)
                || !StringUtils.hasText(operationCode)) {
            return "";
        }
        UiDataSourceDefinition definition = sourceCache.computeIfAbsent(
                sourceId,
                dataSourceMapper::selectById);
        return normalize(text(operation(
                definition,
                operationCode,
                operationCache).get("contextType")));
    }

    private ConsumerContext context(
            EntityForm form,
            EntityDefinition entity) {
        return new ConsumerContext(
                FORM,
                form.getId(),
                form.getFormKey(),
                display(form.getFormName(), form.getId()),
                entity.getId(),
                form.getActiveReleaseId());
    }

    private ConsumerContext context(
            EntityListConfig list,
            EntityDefinition entity) {
        return new ConsumerContext(
                LIST,
                list.getId(),
                list.getListKey(),
                display(list.getListName(), list.getId()),
                entity.getId(),
                list.getActiveReleaseId());
    }

    private List<UiDataSourceReferenceContextDTO> resolveContexts(
            UiEventBinding binding,
            List<Map<String, Object>> draftSteps,
            Map<String, Object> draftStep,
            int draftStepIndex,
            List<ConsumerContext> consumers,
            Map<String, UiDataSourceDefinition> sourceCache,
            Map<String, Map<String, Map<String, Object>>> operationCache,
            Map<String, Optional<UiConfigRelease>> releaseCache,
            Map<String, Optional<Map<String, Object>>> snapshotCache,
            Set<String> matchedPublishedReferenceKeys) {
        return consumers.stream()
                .map(context -> resolveContext(
                        binding,
                        draftSteps,
                        draftStep,
                        draftStepIndex,
                        context,
                        sourceCache,
                        operationCache,
                        releaseCache,
                        snapshotCache,
                        matchedPublishedReferenceKeys))
                .toList();
    }

    /**
     * 只根据已校验发布快照判定线上状态。FORM/LIST 都校验 owner 的激活
     * 指针，与运行态对发布记录和草稿指针一致性的要求保持相同。
     */
    private UiDataSourceReferenceContextDTO resolveContext(
            UiEventBinding binding,
            List<Map<String, Object>> draftSteps,
            Map<String, Object> draftStep,
            int draftStepIndex,
            ConsumerContext context,
            Map<String, UiDataSourceDefinition> sourceCache,
            Map<String, Map<String, Map<String, Object>>> operationCache,
            Map<String, Optional<UiConfigRelease>> releaseCache,
            Map<String, Optional<Map<String, Object>>> snapshotCache,
            Set<String> matchedPublishedReferenceKeys) {
        String releaseKey = ownerKey(context.type(), context.id());
        UiConfigRelease release = releaseCache.computeIfAbsent(
                releaseKey,
                ignored -> Optional.ofNullable(releaseMapper.findActive(
                        context.type(), context.id())))
                .orElse(null);
        if (release == null) {
            return emptyContext(
                    context,
                    "NOT_PUBLISHED",
                    "该配置尚无激活发布版本",
                    "NOT_PUBLISHED");
        }
        if (!Objects.equals(
                        context.activeReleaseId(), release.getId())) {
            return unavailableContext(
                    context,
                    release,
                    context.type()
                            + " 草稿的 activeReleaseId 与 ACTIVE 发布记录不一致");
        }

        Optional<Map<String, Object>> snapshot = snapshotCache.computeIfAbsent(
                release.getId(),
                ignored -> verifiedSnapshot(release));
        if (snapshot.isEmpty()) {
            return unavailableContext(
                    context,
                    release,
                    "发布快照缺失或完整性校验失败");
        }
        List<Map<String, Object>> bindings = mapList(
                snapshot.get().get("eventBindings"));
        Map<String, Object> publishedBinding = findPublishedBinding(
                bindings, binding);
        if (Boolean.FALSE.equals(binding.getEnabled())
                && publishedBinding == null) {
            return disabledPublishedContext(
                    binding, context, release, bindings);
        }
        List<Map<String, Object>> projectedDraftSteps = projectDraftSteps(
                binding,
                draftSteps,
                context.type(),
                sourceCache,
                operationCache);
        int projectedStepIndex = projectedStepIndex(
                binding,
                draftSteps,
                draftStepIndex,
                context.type(),
                sourceCache,
                operationCache);
        // 旧数据可能保存 ENTITY context 的接口步骤。它虽然需要展示引用，
        // 但不会进入 FORM/LIST 快照；不能让投影后的下一个步骤借用它的索引。
        boolean referenceStepApplies = projectedStepIndex >= 0;
        boolean semanticMatch = referenceStepApplies
                && publishedBinding != null
                && !Boolean.FALSE.equals(binding.getEnabled())
                && snapshotSupport.equivalent(
                        semanticBinding(binding, projectedDraftSteps),
                        semanticBinding(publishedBinding));
        PublishedStepMatch stepMatch = matchPublishedStep(
                publishedBinding,
                draftStep,
                projectedStepIndex,
                semanticMatch);
        if (stepMatch != null) {
            // 只有经过当前上下文实际匹配的发布步骤才用于去重；无稳定
            // stepCode 且草稿已变化时，线上旧步骤必须另列 PUBLISHED_ONLY。
            matchedPublishedReferenceKeys.add(publishedReferenceIdentity(
                    release,
                    stepMatch.binding(),
                    stepMatch.stepIndex()));
        }
        EffectiveEvaluation evaluation = evaluateEffective(
                bindings, context, binding, stepMatch);
        Boolean publishedStepEffective = evaluation.effective();
        boolean draftStepPublished = stepMatch != null;
        Boolean draftStepEffective = !evaluation.valid()
                || evaluation.targetDependent()
                ? null
                : semanticMatch
                        && stepMatch != null
                        && Boolean.TRUE.equals(publishedStepEffective);

        String publicationStatus = semanticMatch && stepMatch != null
                ? "PUBLISHED_MATCH" : "PUBLISHED_CHANGED";
        String publicationReason = publicationReason(
                binding,
                publishedBinding,
                stepMatch,
                semanticMatch);
        String effectiveStatus = effectiveStatus(
                semanticMatch,
                stepMatch,
                evaluation,
                publishedStepEffective);
        String effectiveReason = effectiveReason(
                semanticMatch,
                stepMatch,
                evaluation,
                publishedStepEffective);
        return UiDataSourceReferenceContextDTO.builder()
                .configType(context.type())
                .configId(context.id())
                .configKey(context.key())
                .configName(context.name())
                .releaseId(release.getId())
                .releaseVersion(release.getVersion())
                .activeReleasePresent(true)
                .publicationStatus(publicationStatus)
                .publicationReason(publicationReason)
                .published(draftStepPublished)
                .draftMatchesPublished(semanticMatch)
                .draftStepPublished(draftStepPublished)
                .draftStepEffective(draftStepEffective)
                .publishedStepEffective(publishedStepEffective)
                .publishedMatchBasis(stepMatch == null
                        ? "UNMATCHED" : stepMatch.basis())
                .effectiveStatus(effectiveStatus)
                .effectiveReason(effectiveReason)
                .effectiveChain(effectiveSteps(
                        evaluation.representative().steps()))
                .build();
    }

    /**
     * 发布快照只保存启用绑定，因此“停用草稿 + 快照中不存在该绑定”就是
     * 运行语义上的一致状态；无需虚构一个并不存在的发布步骤。
     */
    private UiDataSourceReferenceContextDTO disabledPublishedContext(
            UiEventBinding binding,
            ConsumerContext context,
            UiConfigRelease release,
            List<Map<String, Object>> bindings) {
        EffectiveChain chain = resolveEffectiveChain(
                bindings, context, binding);
        return UiDataSourceReferenceContextDTO.builder()
                .configType(context.type())
                .configId(context.id())
                .configKey(context.key())
                .configName(context.name())
                .releaseId(release.getId())
                .releaseVersion(release.getVersion())
                .activeReleasePresent(true)
                .publicationStatus("PUBLISHED_MATCH")
                .publicationReason(
                        "草稿绑定已停用，激活发布快照同样不包含该绑定")
                .published(false)
                .draftMatchesPublished(true)
                .draftStepPublished(false)
                .draftStepEffective(false)
                .publishedStepEffective(false)
                .publishedMatchBasis("DISABLED_ABSENCE")
                .effectiveStatus("DISABLED")
                .effectiveReason("该绑定已停用，线上最终执行链不包含此步骤")
                .effectiveChain(effectiveSteps(chain.steps()))
                .build();
    }

    private Optional<Map<String, Object>> verifiedSnapshot(
            UiConfigRelease release) {
        try {
            Map<String, Object> snapshot =
                    releaseService.verifiedReleaseSnapshot(release);
            return Optional.ofNullable(snapshot);
        } catch (IllegalArgumentException exception) {
            log.warn(
                    "接口服务引用查询跳过无效发布快照: releaseId={}, reason={}",
                    LogValue.safe(release.getId()),
                    exception.getMessage());
            return Optional.empty();
        }
    }

    private UiDataSourceReferenceContextDTO emptyContext(
            ConsumerContext context,
            String publicationStatus,
            String reason,
            String effectiveStatus) {
        return UiDataSourceReferenceContextDTO.builder()
                .configType(context.type())
                .configId(context.id())
                .configKey(context.key())
                .configName(context.name())
                .activeReleasePresent(false)
                .publicationStatus(publicationStatus)
                .publicationReason(reason)
                .published(false)
                .draftMatchesPublished(null)
                .draftStepPublished(false)
                .draftStepEffective(false)
                .publishedStepEffective(null)
                .publishedMatchBasis("UNMATCHED")
                .effectiveStatus(effectiveStatus)
                .effectiveReason(reason)
                .effectiveChain(List.of())
                .build();
    }

    private UiDataSourceReferenceContextDTO unavailableContext(
            ConsumerContext context,
            UiConfigRelease release,
            String reason) {
        return UiDataSourceReferenceContextDTO.builder()
                .configType(context.type())
                .configId(context.id())
                .configKey(context.key())
                .configName(context.name())
                .releaseId(release.getId())
                .releaseVersion(release.getVersion())
                .activeReleasePresent(true)
                .publicationStatus("PUBLISH_STATE_UNAVAILABLE")
                .publicationReason(reason)
                .published(false)
                .draftMatchesPublished(null)
                .draftStepPublished(false)
                .draftStepEffective(null)
                .publishedStepEffective(null)
                .publishedMatchBasis("UNAVAILABLE")
                .effectiveStatus("UNKNOWN")
                .effectiveReason(reason)
                .effectiveChain(List.of())
                .build();
    }

    private String publicationReason(
            UiEventBinding draft,
            Map<String, Object> publishedBinding,
            PublishedStepMatch stepMatch,
            boolean semanticMatch) {
        if (semanticMatch && stepMatch != null) {
            return "草稿绑定及步骤与激活发布快照一致";
        }
        if (publishedBinding == null) {
            return "激活发布快照中不存在该事件绑定";
        }
        if (Boolean.FALSE.equals(draft.getEnabled())) {
            return "草稿绑定已停用，激活发布快照仍保留旧版本";
        }
        if (stepMatch == null) {
            return StringUtils.hasText(text(draft.getStepsDocument()))
                    ? "草稿已变化，且当前步骤无法可靠对应激活发布步骤"
                    : "激活发布快照中不存在该步骤";
        }
        return "草稿绑定内容与激活发布快照不同";
    }

    private String effectiveStatus(
            boolean semanticMatch,
            PublishedStepMatch stepMatch,
            EffectiveEvaluation evaluation,
            Boolean publishedStepEffective) {
        if (!evaluation.valid()) {
            return "INVALID_CHAIN";
        }
        if (stepMatch == null) {
            return "DRAFT_STEP_NOT_PUBLISHED";
        }
        if (evaluation.targetDependent()) {
            return "PARTIAL";
        }
        if (semanticMatch) {
            return Boolean.TRUE.equals(publishedStepEffective)
                    ? "ACTIVE" : "SHADOWED";
        }
        return Boolean.TRUE.equals(publishedStepEffective)
                ? "PUBLISHED_VERSION_ACTIVE"
                : "PUBLISHED_VERSION_SHADOWED";
    }

    private String effectiveReason(
            boolean semanticMatch,
            PublishedStepMatch stepMatch,
            EffectiveEvaluation evaluation,
            Boolean publishedStepEffective) {
        if (!evaluation.valid()) {
            return "最终执行链包含多个 REPLACE 步骤，运行时会拒绝执行";
        }
        if (stepMatch == null) {
            return "当前草稿步骤未可靠匹配到激活发布步骤";
        }
        if (evaluation.targetDependent()) {
            return targetDependentReason(evaluation, semanticMatch);
        }
        if (semanticMatch && Boolean.TRUE.equals(publishedStepEffective)) {
            return "当前草稿步骤已进入最终生效链";
        }
        if (semanticMatch) {
            return "该步骤已发布，但被下级 REPLACE 或 DISABLE 继承规则遮蔽";
        }
        if (Boolean.TRUE.equals(publishedStepEffective)) {
            return "线上旧版本步骤仍生效，当前草稿尚未发布";
        }
        return "线上旧版本步骤被下级 REPLACE 或 DISABLE 继承规则遮蔽";
    }

    private String targetDependentReason(
            EffectiveEvaluation evaluation,
            boolean draftMatchesPublished) {
        String subject = draftMatchesPublished
                ? "当前草稿步骤" : "线上发布步骤";
        return subject + "的生效结果取决于字段或按钮目标：在 "
                + evaluation.effectiveVariantCount() + "/"
                + evaluation.variantCount() + " 条目标执行链中生效";
    }

    private AggregateStatus aggregate(
            List<UiDataSourceReferenceContextDTO> contexts,
            boolean draftPresent) {
        if (contexts.isEmpty()) {
            return new AggregateStatus(
                    "NOT_APPLICABLE",
                    "该实体默认绑定当前没有适用且可访问的发布页面",
                    "DRAFT_ONLY",
                    null,
                    "NOT_APPLICABLE",
                    "没有可计算的 FORM/LIST 发布上下文",
                    null,
                    List.of(),
                    null,
                    null,
                    0,
                    0);
        }
        int publishedCount = (int) contexts.stream()
                .filter(UiDataSourceReferenceContextDTO::isPublished)
                .count();
        int effectiveCount = (int) contexts.stream()
                .filter(item -> referenceEffective(item) == Boolean.TRUE)
                .count();
        long matchCount = contexts.stream()
                .filter(item -> "PUBLISHED_MATCH".equals(
                        item.getPublicationStatus()))
                .count();
        long noReleaseCount = contexts.stream()
                .filter(item -> "NOT_PUBLISHED".equals(
                        item.getPublicationStatus()))
                .count();
        long unavailableCount = contexts.stream()
                .filter(item -> "PUBLISH_STATE_UNAVAILABLE".equals(
                        item.getPublicationStatus()))
                .count();
        long publishedOnlyCount = contexts.stream()
                .filter(item -> "PUBLISHED_ONLY".equals(
                        item.getPublicationStatus()))
                .count();

        String publicationStatus;
        String lifecycleStatus;
        if (publishedOnlyCount == contexts.size()) {
            publicationStatus = "PUBLISHED_ONLY";
            lifecycleStatus = "PUBLISHED_ONLY";
        } else if (matchCount == contexts.size()) {
            publicationStatus = "PUBLISHED_MATCH";
            lifecycleStatus = "PUBLISHED_MATCH";
        } else if (noReleaseCount == contexts.size()) {
            publicationStatus = "NOT_PUBLISHED";
            lifecycleStatus = "DRAFT_ONLY";
        } else if (unavailableCount == contexts.size()) {
            publicationStatus = "PUBLISH_STATE_UNAVAILABLE";
            lifecycleStatus = "UNPUBLISHED";
        } else if (contexts.size() == 1) {
            publicationStatus = contexts.get(0).getPublicationStatus();
            lifecycleStatus = "PUBLISHED_CHANGED";
        } else {
            publicationStatus = "PARTIALLY_PUBLISHED";
            lifecycleStatus = "PUBLISHED_CHANGED";
        }
        Boolean draftMatchesPublished = !draftPresent
                || unavailableCount == contexts.size()
                ? null : matchCount == contexts.size();

        long unknownCount = contexts.stream()
                .filter(item -> referenceEffective(item) == null)
                .count();
        long partialCount = contexts.stream()
                .filter(item -> "PARTIAL".equals(
                        item.getEffectiveStatus()))
                .count();
        String effectiveStatus;
        Boolean effective;
        if (contexts.size() == 1) {
            UiDataSourceReferenceContextDTO context = contexts.get(0);
            effectiveStatus = context.getEffectiveStatus();
            effective = "NOT_PUBLISHED".equals(effectiveStatus)
                    ? Boolean.FALSE
                    : context.getPublishedStepEffective();
        } else if (effectiveCount == contexts.size()) {
            effectiveStatus = "ACTIVE";
            effective = true;
        } else if (effectiveCount > 0 || partialCount > 0) {
            effectiveStatus = "PARTIAL";
            effective = null;
        } else if (contexts.stream().allMatch(item ->
                "NOT_PUBLISHED".equals(item.getEffectiveStatus()))) {
            effectiveStatus = "NOT_PUBLISHED";
            effective = false;
        } else if (contexts.stream().allMatch(item ->
                "DISABLED".equals(item.getEffectiveStatus()))) {
            effectiveStatus = "DISABLED";
            effective = false;
        } else if (contexts.stream().anyMatch(item ->
                "INVALID_CHAIN".equals(item.getEffectiveStatus()))) {
            effectiveStatus = "INVALID_CHAIN";
            effective = false;
        } else if (unknownCount > 0) {
            effectiveStatus = "UNKNOWN";
            effective = null;
        } else {
            effectiveStatus = "SHADOWED";
            effective = false;
        }
        List<String> effectiveContexts = contexts.stream()
                .filter(item -> referenceEffective(item) == Boolean.TRUE)
                .map(item -> item.getConfigName()
                        + " (" + item.getConfigType() + ")")
                .toList();
        String publicationReason = contexts.size() == 1
                ? contexts.get(0).getPublicationReason()
                : "共 " + contexts.size() + " 个发布上下文："
                        + matchCount + " 个与草稿一致，"
                        + publishedCount + " 个包含对应发布步骤";
        String effectiveSubject = draftPresent
                ? "当前草稿步骤" : "线上发布步骤";
        String effectiveReason;
        if (contexts.size() == 1) {
            effectiveReason = contexts.get(0).getEffectiveReason();
        } else if ("DISABLED".equals(effectiveStatus)) {
            effectiveReason = "该绑定已停用，所有发布上下文均不包含此步骤";
        } else {
            effectiveReason = effectiveSubject + "在 " + effectiveCount
                    + "/" + contexts.size() + " 个上下文中完全生效"
                    + (partialCount > 0
                            ? "，另有 " + partialCount
                                    + " 个上下文按字段或按钮目标部分生效"
                            : "");
        }
        String activeReleaseId = contexts.size() == 1
                ? contexts.get(0).getReleaseId() : null;
        Integer activeReleaseVersion = contexts.size() == 1
                ? contexts.get(0).getReleaseVersion() : null;
        return new AggregateStatus(
                publicationStatus,
                publicationReason,
                lifecycleStatus,
                draftMatchesPublished,
                effectiveStatus,
                effectiveReason,
                effective,
                effectiveContexts,
                activeReleaseId,
                activeReleaseVersion,
                publishedCount,
                effectiveCount);
    }

    private Boolean referenceEffective(
            UiDataSourceReferenceContextDTO context) {
        return context.getPublishedStepEffective();
    }

    private Map<String, Object> findPublishedBinding(
            List<Map<String, Object>> bindings,
            UiEventBinding draft) {
        return bindings.stream()
                .filter(item -> sameCoordinate(item, draft))
                .findFirst()
                .orElse(null);
    }

    private boolean sameCoordinate(
            Map<String, Object> published,
            UiEventBinding draft) {
        return Objects.equals(
                normalize(text(published.get("ownerType"))),
                normalize(draft.getOwnerType()))
                && Objects.equals(
                        text(published.get("ownerId")),
                        draft.getOwnerId())
                && Objects.equals(
                        normalizeDefault(
                                text(published.get("targetType")),
                                "OWNER"),
                        normalizeDefault(draft.getTargetType(), "OWNER"))
                && Objects.equals(
                        normalizedTargetKey(text(
                                published.get("targetKey"))),
                        normalizedTargetKey(draft.getTargetKey()))
                && Objects.equals(
                        normalize(text(published.get("eventCode"))),
                        normalize(draft.getEventCode()));
    }

    private Map<String, Object> semanticBinding(
            UiEventBinding binding,
            List<Map<String, Object>> steps) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ownerType", normalize(binding.getOwnerType()));
        result.put("ownerId", binding.getOwnerId());
        result.put("targetType", normalizeDefault(
                binding.getTargetType(), "OWNER"));
        result.put("targetKey", normalizedTargetKey(
                binding.getTargetKey()));
        result.put("eventCode", normalize(binding.getEventCode()));
        result.put("inheritanceMode", normalizeDefault(
                binding.getInheritanceMode(), "INHERIT"));
        result.put("steps", steps);
        return result;
    }

    private List<Map<String, Object>> projectDraftSteps(
            UiEventBinding binding,
            List<Map<String, Object>> steps,
            String configType,
            Map<String, UiDataSourceDefinition> sourceCache,
            Map<String, Map<String, Map<String, Object>>> operationCache) {
        if (!ENTITY.equals(normalize(binding.getOwnerType()))) {
            return steps;
        }
        return steps.stream()
                .filter(step -> stepAppliesToConfig(
                        step,
                        configType,
                        sourceCache,
                        operationCache))
                .toList();
    }

    private int projectedStepIndex(
            UiEventBinding binding,
            List<Map<String, Object>> steps,
            int rawIndex,
            String configType,
            Map<String, UiDataSourceDefinition> sourceCache,
            Map<String, Map<String, Map<String, Object>>> operationCache) {
        if (!ENTITY.equals(normalize(binding.getOwnerType()))) {
            return rawIndex;
        }
        if (!stepAppliesToConfig(
                steps.get(rawIndex),
                configType,
                sourceCache,
                operationCache)) {
            return -1;
        }
        int projectedIndex = 0;
        for (int index = 0; index < rawIndex; index++) {
            if (stepAppliesToConfig(
                    steps.get(index),
                    configType,
                    sourceCache,
                    operationCache)) {
                projectedIndex++;
            }
        }
        return projectedIndex;
    }

    private boolean stepAppliesToConfig(
            Map<String, Object> step,
            String configType,
            Map<String, UiDataSourceDefinition> sourceCache,
            Map<String, Map<String, Map<String, Object>>> operationCache) {
        if (!StringUtils.hasText(text(step.get("serviceId")))) {
            return true;
        }
        String context = referenceOperationContext(
                step, sourceCache, operationCache);
        // 与快照构建一致：损坏引用继续保留并由发布校验器报告。
        return !StringUtils.hasText(context)
                || Objects.equals(configType, context);
    }

    private Map<String, Object> semanticBinding(
            Map<String, Object> binding) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ownerType", normalize(text(
                binding.get("ownerType"))));
        result.put("ownerId", text(binding.get("ownerId")));
        result.put("targetType", normalizeDefault(
                text(binding.get("targetType")), "OWNER"));
        result.put("targetKey", normalizedTargetKey(text(
                binding.get("targetKey"))));
        result.put("eventCode", normalize(text(
                binding.get("eventCode"))));
        result.put("inheritanceMode", normalizeDefault(
                text(binding.get("inheritanceMode")), "INHERIT"));
        result.put("steps", mapList(binding.get("steps")));
        return result;
    }

    private PublishedStepMatch matchPublishedStep(
            Map<String, Object> publishedBinding,
            Map<String, Object> draftStep,
            int draftStepIndex,
            boolean semanticMatch) {
        if (publishedBinding == null) {
            return null;
        }
        List<Map<String, Object>> publishedSteps = mapList(
                publishedBinding.get("steps"));
        if (semanticMatch
                && draftStepIndex >= 0
                && draftStepIndex < publishedSteps.size()) {
            return new PublishedStepMatch(
                    publishedBinding,
                    publishedSteps.get(draftStepIndex),
                    draftStepIndex,
                    "EXACT_BINDING_INDEX");
        }
        String stepCode = blankToNull(text(draftStep.get("stepCode")));
        if (!StringUtils.hasText(stepCode)) {
            return null;
        }
        List<Integer> matches = new ArrayList<>();
        for (int index = 0; index < publishedSteps.size(); index++) {
            Map<String, Object> candidate = publishedSteps.get(index);
            if (Objects.equals(stepCode, text(candidate.get("stepCode")))
                    && Objects.equals(
                            text(draftStep.get("serviceId")),
                            text(candidate.get("serviceId")))
                    && Objects.equals(
                            text(draftStep.get("operationCode")),
                            text(candidate.get("operationCode")))) {
                matches.add(index);
            }
        }
        if (matches.size() != 1) {
            return null;
        }
        int index = matches.get(0);
        return new PublishedStepMatch(
                publishedBinding,
                publishedSteps.get(index),
                index,
                "STEP_CODE");
    }

    /**
     * 复现事件运行态的三级继承顺序：实体 OWNER、FORM/LIST OWNER、精确
     * FIELD/BUTTON；REPLACE 与 DISABLE 会清空此前步骤，最后按 order 排序。
     */
    private EffectiveEvaluation evaluateEffective(
            List<Map<String, Object>> bindings,
            ConsumerContext context,
            UiEventBinding reference,
            PublishedStepMatch match) {
        EffectiveChain representative = resolveEffectiveChain(
                bindings, context, reference);
        if (match == null) {
            return new EffectiveEvaluation(
                    representative,
                    null,
                    representative.valid(),
                    false,
                    0,
                    1);
        }
        String targetType = normalizeDefault(
                reference.getTargetType(), "OWNER");
        if (!"OWNER".equals(targetType)) {
            Boolean effective = representative.valid()
                    ? representative.contains(match) : null;
            return new EffectiveEvaluation(
                    representative,
                    effective,
                    representative.valid(),
                    false,
                    Boolean.TRUE.equals(effective) ? 1 : 0,
                    1);
        }

        // OWNER 是字段/按钮事件的默认层。除无精确覆盖的基线外，还必须逐个
        // 枚举快照里的 FIELD/BUTTON 覆盖，才能区分“全局生效”和“部分生效”。
        List<EffectiveChain> variants = new ArrayList<>();
        variants.add(representative);
        for (TargetCoordinate target : exactTargets(
                bindings, context, normalize(reference.getEventCode()))) {
            variants.add(resolveEffectiveChain(
                    bindings,
                    context,
                    normalize(reference.getEventCode()),
                    target.type(),
                    target.key()));
        }
        boolean valid = variants.stream().allMatch(EffectiveChain::valid);
        int effectiveCount = (int) variants.stream()
                .filter(chain -> chain.valid() && chain.contains(match))
                .count();
        boolean targetDependent = valid
                && effectiveCount > 0
                && effectiveCount < variants.size();
        Boolean effective = !valid || targetDependent
                ? null : effectiveCount == variants.size();
        return new EffectiveEvaluation(
                representative,
                effective,
                valid,
                targetDependent,
                effectiveCount,
                variants.size());
    }

    private EffectiveChain resolveEffectiveChain(
            List<Map<String, Object>> bindings,
            ConsumerContext context,
            UiEventBinding reference) {
        String targetType = normalizeDefault(
                reference.getTargetType(), "OWNER");
        return resolveEffectiveChain(
                bindings,
                context,
                normalize(reference.getEventCode()),
                "OWNER".equals(targetType) ? null : targetType,
                "OWNER".equals(targetType)
                        ? null : reference.getTargetKey());
    }

    private EffectiveChain resolveEffectiveChain(
            List<Map<String, Object>> bindings,
            ConsumerContext context,
            String eventCode,
            String exactTargetType,
            String exactTargetKey) {
        List<ResolvedStep> steps = new ArrayList<>();
        applyLevel(
                steps,
                findBinding(
                        bindings,
                        ENTITY,
                        context.entityId(),
                        "OWNER",
                        null,
                        eventCode));
        applyLevel(
                steps,
                findBinding(
                        bindings,
                        context.type(),
                        context.id(),
                        "OWNER",
                        null,
                        eventCode));
        if (StringUtils.hasText(exactTargetType)) {
            applyLevel(
                    steps,
                    findBinding(
                            bindings,
                            context.type(),
                            context.id(),
                            exactTargetType,
                            exactTargetKey,
                            eventCode));
        }
        steps.sort(Comparator.comparingInt(item ->
                integer(item.step().get("order"), 0)));
        long replacements = steps.stream()
                .filter(item -> "REPLACE".equals(normalize(
                        text(item.step().get("strategy")))))
                .count();
        return new EffectiveChain(
                List.copyOf(steps), replacements <= 1);
    }

    private List<TargetCoordinate> exactTargets(
            List<Map<String, Object>> bindings,
            ConsumerContext context,
            String eventCode) {
        return bindings.stream()
                .filter(item -> Objects.equals(
                        context.type(),
                        normalize(text(item.get("ownerType")))))
                .filter(item -> Objects.equals(
                        context.id(), text(item.get("ownerId"))))
                .filter(item -> Set.of("FIELD", "BUTTON").contains(
                        normalize(text(item.get("targetType")))))
                .filter(item -> Objects.equals(
                        eventCode,
                        normalize(text(item.get("eventCode")))))
                .map(item -> new TargetCoordinate(
                        normalize(text(item.get("targetType"))),
                        normalizedTargetKey(text(item.get("targetKey")))))
                .distinct()
                .sorted(Comparator
                        .comparing(TargetCoordinate::type)
                        .thenComparing(TargetCoordinate::key,
                                Comparator.nullsFirst(String::compareTo)))
                .toList();
    }

    private void applyLevel(
            List<ResolvedStep> effective,
            Map<String, Object> binding) {
        if (binding == null) {
            return;
        }
        String mode = normalizeDefault(
                text(binding.get("inheritanceMode")), "INHERIT");
        if ("DISABLE".equals(mode)) {
            effective.clear();
            return;
        }
        if ("REPLACE".equals(mode)) {
            effective.clear();
        }
        List<Map<String, Object>> steps = mapList(binding.get("steps"));
        for (int index = 0; index < steps.size(); index++) {
            effective.add(new ResolvedStep(
                    binding, steps.get(index), index));
        }
    }

    private Map<String, Object> findBinding(
            List<Map<String, Object>> bindings,
            String ownerType,
            String ownerId,
            String targetType,
            String targetKey,
            String eventCode) {
        return bindings.stream()
                .filter(item -> Objects.equals(
                        normalize(text(item.get("ownerType"))), ownerType))
                .filter(item -> Objects.equals(
                        text(item.get("ownerId")), ownerId))
                .filter(item -> Objects.equals(
                        normalizeDefault(
                                text(item.get("targetType")), "OWNER"),
                        targetType))
                .filter(item -> Objects.equals(
                        normalizedTargetKey(text(item.get("targetKey"))),
                        normalizedTargetKey(targetKey)))
                .filter(item -> Objects.equals(
                        normalize(text(item.get("eventCode"))), eventCode))
                .findFirst()
                .orElse(null);
    }

    private List<UiDataSourceReferenceEffectiveStepDTO> effectiveSteps(
            List<ResolvedStep> steps) {
        return steps.stream().map(item -> {
            Map<String, Object> binding = item.binding();
            Map<String, Object> step = item.step();
            return UiDataSourceReferenceEffectiveStepDTO.builder()
                    .bindingId(text(binding.get("id")))
                    .ownerType(normalize(text(binding.get("ownerType"))))
                    .ownerId(text(binding.get("ownerId")))
                    .targetType(normalizeDefault(
                            text(binding.get("targetType")), "OWNER"))
                    .targetKey(normalizedTargetKey(
                            text(binding.get("targetKey"))))
                    .inheritanceSource(inheritanceSource(binding))
                    .stepIndex(item.stepIndex())
                    .stepCode(blankToNull(text(step.get("stepCode"))))
                    .stepName(blankToNull(text(step.get("name"))))
                    .stepOrder(integer(step.get("order"),
                            item.stepIndex() * 10))
                    .stepStrategy(normalizeDefault(
                            text(step.get("strategy")), "BEFORE"))
                    .serviceId(text(step.get("serviceId")))
                    .operationCode(text(step.get("operationCode")))
                    .build();
        }).toList();
    }

    private Map<String, Object> operation(
            UiDataSourceDefinition definition,
            String operationCode,
            Map<String, Map<String, Map<String, Object>>> cache) {
        if (definition == null || !StringUtils.hasText(operationCode)) {
            return Map.of();
        }
        Map<String, Map<String, Object>> operations = cache.computeIfAbsent(
                definition.getId(),
                ignored -> operationCatalog(definition));
        return operations.getOrDefault(operationCode, Map.of());
    }

    private Map<String, Map<String, Object>> operationCatalog(
            UiDataSourceDefinition definition) {
        if (!StringUtils.hasText(definition.getOperationsDocument())) {
            return Map.of();
        }
        try {
            Map<String, Map<String, Object>> result = new LinkedHashMap<>();
            for (Map<String, Object> operation : mapList(codec.readArray(
                    definition.getOperationsDocument(),
                    "接口服务操作定义"))) {
                String code = text(operation.get("code"));
                if (StringUtils.hasText(code)) {
                    result.put(code, operation);
                }
            }
            return Map.copyOf(result);
        } catch (IllegalArgumentException exception) {
            log.warn(
                    "接口服务引用查询无法解析操作目录: serviceId={}, reason={}",
                    LogValue.safe(definition.getId()),
                    exception.getMessage());
            return Map.of();
        }
    }

    private List<Map<String, Object>> readSteps(
            UiEventBinding binding) {
        if (!StringUtils.hasText(binding.getStepsDocument())) {
            return List.of();
        }
        try {
            return mapList(codec.readArray(
                    binding.getStepsDocument(), "UI事件绑定步骤"));
        } catch (IllegalArgumentException exception) {
            log.warn(
                    "接口服务引用查询跳过无效事件绑定: bindingId={}, reason={}",
                    LogValue.safe(binding.getId()),
                    exception.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> stringMap((Map<?, ?>) item))
                .toList();
    }

    private Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) ->
                result.put(String.valueOf(key), value));
        return result;
    }

    private String inheritanceSource(UiEventBinding binding) {
        return inheritanceSource(Map.of(
                "ownerType", binding.getOwnerType(),
                "targetType", binding.getTargetType() == null
                        ? "OWNER" : binding.getTargetType()));
    }

    private String inheritanceSource(Map<String, Object> binding) {
        String ownerType = normalize(text(binding.get("ownerType")));
        String targetType = normalizeDefault(
                text(binding.get("targetType")), "OWNER");
        if (ENTITY.equals(ownerType)) {
            return "ENTITY_DEFAULT";
        }
        return ownerType + "_" + targetType;
    }

    /**
     * 跨 FORM/LIST 发布上下文聚合 PUBLISHED_ONLY 引用的逻辑身份。
     * stepCode 存在时优先使用稳定编码；历史步骤没有编码时使用同一
     * 服务/操作在绑定内的出现序号，兼容实体混合步骤投影造成的索引变化。
     */
    private String referenceIdentity(
            UiEventBinding binding,
            List<Map<String, Object>> steps,
            Map<String, Object> step,
            int stepIndex) {
        String stepCode = blankToNull(text(step.get("stepCode")));
        int occurrence = 0;
        for (int index = 0; index < stepIndex; index++) {
            Map<String, Object> candidate = steps.get(index);
            if (Objects.equals(
                    text(step.get("serviceId")),
                    text(candidate.get("serviceId")))
                    && Objects.equals(
                            text(step.get("operationCode")),
                            text(candidate.get("operationCode")))) {
                occurrence++;
            }
        }
        return ownerKey(binding.getOwnerType(), binding.getOwnerId())
                + ":" + normalizeDefault(
                        binding.getTargetType(), "OWNER")
                + ":" + normalizedTargetKey(binding.getTargetKey())
                + ":" + normalize(binding.getEventCode())
                + ":" + (StringUtils.hasText(stepCode)
                        ? "CODE:" + stepCode
                        : "OCCURRENCE:" + occurrence)
                + ":" + text(step.get("serviceId"))
                + ":" + text(step.get("operationCode"));
    }

    /**
     * 一个已校验发布快照内步骤的精确身份，只用于可靠草稿匹配后的去重。
     * releaseId 与发布数组索引共同避免“相似但未可靠匹配”的线上旧步骤
     * 被草稿行吞掉。
     */
    private String publishedReferenceIdentity(
            UiConfigRelease release,
            Map<String, Object> binding,
            int stepIndex) {
        return release.getId()
                + ":" + EffectiveChain.bindingIdentity(binding)
                + ":" + stepIndex;
    }

    private String ownerKey(String type, String id) {
        return normalize(type) + ":" + id;
    }

    private String display(String name, String id) {
        return StringUtils.hasText(name) ? name : id;
    }

    private String normalizedTargetKey(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String normalizeDefault(String value, String fallback) {
        String normalized = normalize(value);
        return StringUtils.hasText(normalized) ? normalized : fallback;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String text = text(value);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return null;
    }

    private int integer(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private record OwnerMetadata(
            EntityDefinition entity,
            EntityForm form,
            EntityListConfig list,
            String ownerName) {
    }

    private record ConsumerContext(
            String type,
            String id,
            String key,
            String name,
            String entityId,
            String activeReleaseId) {
    }

    private record PublishedStepMatch(
            Map<String, Object> binding,
            Map<String, Object> step,
            int stepIndex,
            String basis) {
    }

    private record ResolvedStep(
            Map<String, Object> binding,
            Map<String, Object> step,
            int stepIndex) {
    }

    private record TargetCoordinate(String type, String key) {
    }

    private record EffectiveEvaluation(
            EffectiveChain representative,
            Boolean effective,
            boolean valid,
            boolean targetDependent,
            int effectiveVariantCount,
            int variantCount) {
    }

    private record PublishedOnlyReference(
            String identity,
            UiEventBinding binding,
            OwnerMetadata owner,
            Map<String, Object> step,
            int stepIndex,
            List<UiDataSourceReferenceContextDTO> contexts) {
    }

    private record EffectiveChain(
            List<ResolvedStep> steps,
            boolean valid) {

        private boolean contains(PublishedStepMatch match) {
            String expectedBinding = bindingIdentity(match.binding());
            return steps.stream().anyMatch(item ->
                    Objects.equals(
                            expectedBinding,
                            bindingIdentity(item.binding()))
                            && item.stepIndex() == match.stepIndex());
        }

        private static String bindingIdentity(
                Map<String, Object> binding) {
            Object id = binding.get("id");
            if (id != null && StringUtils.hasText(String.valueOf(id))) {
                return "ID:" + id;
            }
            return "COORD:"
                    + binding.get("ownerType") + ":"
                    + binding.get("ownerId") + ":"
                    + binding.get("targetType") + ":"
                    + binding.get("targetKey") + ":"
                    + binding.get("eventCode");
        }
    }

    private record AggregateStatus(
            String publicationStatus,
            String publicationReason,
            String lifecycleStatus,
            Boolean draftMatchesPublished,
            String effectiveStatus,
            String effectiveReason,
            Boolean effective,
            List<String> effectiveContexts,
            String activeReleaseId,
            Integer activeReleaseVersion,
            int publishedContextCount,
            int effectiveContextCount) {
    }
}
