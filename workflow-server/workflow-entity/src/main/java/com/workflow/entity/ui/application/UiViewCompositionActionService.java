package com.workflow.entity.ui.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.ui.application.validation.UiViewCompositionExtensionPolicy;
import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.contracts.audit.model.AuditAction;
import com.workflow.contracts.audit.model.AuditModule;
import com.workflow.contracts.audit.model.AuditRiskLevel;
import com.workflow.contracts.audit.annotation.SystemAudit;
import com.workflow.contracts.entity.mutation.model.EntityMutationBatchCommand;
import com.workflow.contracts.entity.mutation.model.EntityMutationBatchResult;
import com.workflow.contracts.entity.mutation.model.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.model.EntityMutationContext;
import com.workflow.contracts.entity.mutation.model.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.port.EntityMutationPort;
import com.workflow.contracts.entity.mutation.model.EntityMutationResult;
import com.workflow.contracts.entity.mutation.model.EntityMutationSourceType;
import com.workflow.contracts.entity.ui.model.UiActionCommandPlan;
import com.workflow.contracts.entity.ui.model.UiActionMutationCommand;
import com.workflow.contracts.entity.ui.model.UiDataSourceUsages;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.core.result.PageResult;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityAggregateWriter;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.application.SystemEntityReadService;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityPermissionAction;
import com.workflow.entity.ui.api.request.UiViewCompositionActionCapabilitiesRequest;
import com.workflow.entity.ui.api.request.UiViewCompositionActionRequest;
import com.workflow.entity.ui.api.request.UiViewCompositionResolveRequest;
import com.workflow.entity.ui.api.request.UiExtensionExecuteRequest;
import com.workflow.entity.ui.api.response.UiViewCompositionActionCapabilitiesResponse;
import com.workflow.entity.ui.api.response.UiViewCompositionActionCapabilityDTO;
import com.workflow.entity.ui.api.response.UiViewCompositionActionResponse;
import com.workflow.entity.ui.api.response.UiViewCompositionResolveResponse;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 已发布“关联内容”的权威动作执行服务。
 *
 * <p>动作上下文从业务入口和已发布关联路径恢复，随后重新执行宿主、目标发布、实体和数据
 * 权限校验。新增、编辑沿用目标表单的保存流程；接口扩展动作只读取已发布
 * 快照，客户端行对象、实体编码、字段名和筛选条件不会进入权威执行链。</p>
 */
@Service
@RequiredArgsConstructor
public class UiViewCompositionActionService {

    private static final String FORM = "FORM";
    private static final String LIST = "LIST";
    private static final Set<String> KNOWN_ACTIONS = Set.of("VIEW", "CREATE", "EDIT");
    private static final int MAX_TARGET_RECORDS = 200;
    private static final int MAX_ACTION_MUTATIONS = 100;

    private final UiViewCompositionRuntimeService runtimeService;
    private final UiConfigReleaseMapper releaseMapper;
    private final UiConfigReleaseService releaseService;
    private final EntityPublishedSnapshotService entitySnapshotService;
    private final EntityDefinitionMapper entityMapper;
    private final EntityDataDynamicService dynamicDataService;
    private final SystemEntityReadService systemEntityReadService;
    private final EntityActionCapabilityService capabilityService;
    private final EntityMutationPort mutationPort;
    private final EntityAggregateWriter aggregateWriter;
    private final UiViewCompositionActionReceiptService actionReceiptService;
    private final UiInterfaceExtensionService dataSourceService;
    private final ObjectMapper objectMapper;

    /**
     * 计算当前业务上下文下的权威动作能力。
     *
     * <p>该结果同时考虑发布声明、关系类型、已固定字段以及当前用户标准权限。
     * CREATE/EDIT 仅在精确发布表单提交桥可用时开放；接口扩展须通过已发布
     * 操作校验。任何校验失败都会关闭对应动作。</p>
     *
     * @param request 本次请求，后续经校验后用于处理能力集合
     * @return 处理后的能力集合结果，供调用方继续处理
     */
    @Transactional(readOnly = true)
    public UiViewCompositionActionCapabilitiesResponse capabilities(
            UiViewCompositionActionCapabilitiesRequest request) {
        if (request == null
                || request.getCompositionContext() == null) {
            throw new IllegalArgumentException("关联内容动作上下文不能为空");
        }
        ActionContext context = resolveContext(
                request.getCompositionContext());
        return UiViewCompositionActionCapabilitiesResponse.builder()
                .sourceRecordId(context.claims().sourceRecordId())
                .actionCapabilities(capabilities(context))
                .build();
    }

    /** 为列表查询重算普通关系范围；调用方必须与用户查询、列表固定条件取交集。 */
    public ResolvedListContext resolveListContext(UiViewCompositionResolveRequest request) {
        ActionContext context = resolveContext(request);
        UiViewCompositionResolveResponse target = context.resolved();
        if (!LIST.equals(target.getTargetContentType())) throw new IllegalArgumentException("关联目标不是列表");
        Map<String,Object> filters = target.getFixedFilters();
        boolean matchNone = target.isMatchNone();
        return new ResolvedListContext(target.getOwnerType(),target.getOwnerId(),target.getReleaseId(),target.getReleaseVersion(),
                target.getCompositionKey(),target.getSourceEntityCode(),target.getSourceRecordId(),target.getTargetEntityCode(),
                target.getTargetContentId(),target.getTargetReleaseId(),target.getTargetReleaseVersion(),filters,matchNone);
    }

    /** 本次请求重新校验后的内部结果，不可从客户端反序列化后直接使用。 */
    public record ResolvedListContext(String ownerType,String ownerId,String releaseId,Integer releaseVersion,
            String compositionKey,String sourceEntityCode,String sourceRecordId,String targetEntityCode,
            String targetContentId,String targetReleaseId,Integer targetReleaseVersion,Map<String,Object> fixedFilters,boolean matchNone) {}

    private record ResolvedCoordinates(String ownerType,String ownerId,String releaseId,Integer releaseVersion,
            String compositionKey,String sourceEntityCode,String sourceRecordId) {}

    /**
     * 校验关联内容打开的目标表单提交，并返回服务端必须覆盖的新增初值。
     *
     * <p>普通表单提交不会调用本方法。携带关联内容业务上下文时，目标实体、表单、
     * 精确发布版本以及 EDIT 记录 ID 均从不可变宿主快照重新解析；来源行和目标行
     * 会先加锁再复核，避免关系在校验与写入之间变化。CREATE 的初值同样在锁后
     * 从来源记录重新计算，浏览器提交的同名字段不能覆盖它们。</p>
     *
     * @param compositionContext 业务入口和关联路径，服务端重新校验后才能执行动作
     * @param action 动作标识，决定后续授权表单提交采用的处理分支
     * @param targetEntityCode 目标实体编码，后续用于处理授权表单提交时定位或关联目标
     * @param targetRecordId 目标记录ID，后续用于处理授权表单提交时定位或关联目标
     * @param targetFormId 目标表单ID，后续用于处理授权表单提交时定位或关联目标
     * @param targetConfigHash 目标表单页面配置摘要，变更时拒绝写入并保留输入
     * @return CREATE 时必须合并到目标数据的可信初值；EDIT 时为空
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> authorizeFormSubmission(
            UiViewCompositionResolveRequest compositionContext,
            String action,
            String targetEntityCode,
            String targetRecordId,
            String targetFormId,
            String targetConfigHash) {
        if (compositionContext == null) {
            throw new IllegalArgumentException("关联内容动作上下文不能为空");
        }
        String normalizedAction = normalize(action);
        if (!Set.of("CREATE", "EDIT").contains(normalizedAction)) {
            throw new IllegalArgumentException(
                    "关联内容表单提交只支持新增或编辑");
        }

        ActionContext initial = resolveContext(compositionContext);
        EntityDataDTO source = readAccessible(
                initial.sourceEntity(), initial.claims().sourceRecordId(), null);
        List<EntityDataDTO> targets = "EDIT".equals(normalizedAction)
                && StringUtils.hasText(initial.resolved().getTargetRecordId())
                ? List.of(readAccessible(
                        initial.targetEntity(),
                        initial.resolved().getTargetRecordId(),
                        null))
                : List.of();
        lockMutationRows(initial, source, targets);

        // 锁等待期间来源关系可能已经变化，因此必须重新解析一次，而不是继续使用
        // 加锁前得到的 targetRecordId 或初值。
        ActionContext context = resolveContext(compositionContext);
        Map<String, UiViewCompositionActionCapabilityDTO> actionCapabilities =
                capabilities(context);
        requireAvailable(actionCapabilities, normalizedAction);
        requireExactTargetForm(
                context,
                normalizedAction,
                targetEntityCode,
                targetRecordId,
                targetFormId,
                targetConfigHash);
        return "CREATE".equals(normalizedAction)
                ? actionCapabilities.get("CREATE").getInitialValues()
                : Map.of();
    }

    /**
     * 执行已发布动作。写动作使用实体变更管道的原子批次与持久化幂等回执。
     *
     * @param request 本次请求，后续经校验后用于执行界面视图组合动作
     * @return 执行后的界面视图组合动作结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.ENTITY,
            action = AuditAction.OTHER,
            operation = "执行关联内容动作",
            risk = AuditRiskLevel.MEDIUM,
            targetType = "UI_VIEW_COMPOSITION_ACTION")
    public UiViewCompositionActionResponse execute(
            UiViewCompositionActionRequest request) {
        ValidatedAction action = validateRequest(request);
        ActionContext context = resolveContext(
                action.compositionContext());
        Map<String, UiViewCompositionActionCapabilityDTO> capabilities =
                capabilities(context);
        requireAvailable(capabilities, action.action());
        return executeInterfaceAction(
                action,
                context,
                requireActionService(context, action.action()),
                capabilities);
    }

    /**
     * 执行宿主发布快照中显式钉定的接口动作。
     *
     * <p>客户端只提交 actionKey、记录 ID 和 operationId。服务、操作、映射与
     * 可执行快照都从已发布宿主配置恢复；READ 只返回映射后的界面结果，WRITE
     * 则先取得持久批次回执，再通过受控计划和 EntityMutationPort 原子执行。</p>
     *
     * @param action 动作标识，决定后续接口动作采用的处理分支
     * @param context 执行上下文，向后续接口动作步骤传递身份、配置或状态
     * @param binding 绑定，作为 {@code validateActionBinding} 的输入影响后续处理
     * @param capabilities 能力集合，作为 {@code actionResponse} 的输入影响后续处理
     * @return 执行后的接口动作结果，供调用方继续处理
     */
    private UiViewCompositionActionResponse executeInterfaceAction(
            ValidatedAction action,
            ActionContext context,
            Map<String, Object> binding,
            Map<String, UiViewCompositionActionCapabilityDTO> capabilities) {
        UiInterfaceExtensionService.ActionOperationDescriptor descriptor =
                validateActionBinding(context, binding);
        EntityDataDTO source = readAccessible(
                context.sourceEntity(),
                context.claims().sourceRecordId(),
                null);
        List<EntityDataDTO> targets = action.targetRecordIds().isEmpty()
                ? List.of()
                : readTargets(context, action.targetRecordIds());
        Map<String, Object> input = actionInput(
                context, binding, source, targets);
        UiExtensionExecuteRequest executeRequest = actionExecuteRequest(
                action, context, binding, input);

        if ("READ".equals(descriptor.interfaceKind())) {
            Object raw = dataSourceService.executePinnedOperation(
                    requiredField(
                            binding,
                            "executableSnapshot",
                            "动作接口操作快照"),
                    requiredField(
                            binding,
                            "definitionHash",
                            "动作接口操作哈希"),
                    executeRequest);
            return actionResponse(
                    action,
                    context,
                    capabilities,
                    mapActionOutput(binding, raw),
                    false);
        }

        UiViewCompositionActionReceiptService.AcquireResult receipt =
                actionReceiptService.acquire(
                        context.claims().ownerType(),
                        context.claims().ownerId(),
                        context.claims().releaseId(),
                        context.claims().releaseVersion(),
                        context.claims().compositionKey(),
                        context.sourceEntity().getEntityCode(),
                        context.claims().sourceRecordId(),
                        action.action(),
                        action.targetRecordIds(),
                        action.operationId());
        if (receipt.replayedResponse() != null) {
            return receipt.replayedResponse();
        }

        // WRITE 计划可能依赖来源字段及当前关联集合。必须先锁定来源和本次已授权
        // 目标，再重新解析固定筛选、重读输入并生成计划；否则等待业务行锁期间
        // 关系变化后，Provider 可能依据锁前的陈旧上下文修改已脱离关联的记录。
        lockMutationRows(context, source, targets);
        context = resolveContext(action.compositionContext());
        binding = requireActionService(context, action.action());
        validateActionBinding(context, binding);
        source = readAccessible(
                context.sourceEntity(),
                context.claims().sourceRecordId(),
                null);
        targets = action.targetRecordIds().isEmpty()
                ? List.of()
                : readTargets(context, action.targetRecordIds());
        input = actionInput(context, binding, source, targets);
        executeRequest = actionExecuteRequest(
                action, context, binding, input);
        UiActionCommandPlan plan = dataSourceService
                .planPinnedActionOperation(
                        requiredField(
                                binding,
                                "executableSnapshot",
                                "动作接口操作快照"),
                        requiredField(
                                binding,
                                "definitionHash",
                                "动作接口操作哈希"),
                        executeRequest);
        List<EntityMutationCommand> commands = actionMutationCommands(
                action,
                context,
                plan,
                receipt.receiptCommand().context().idempotencyKey());
        EntityMutationBatchResult batch = mutationPort.executeBatch(
                new EntityMutationBatchCommand(
                        action.operationId(), commands, true));
        boolean replayed = !batch.results().isEmpty()
                && batch.results().stream().allMatch(
                EntityMutationResult::replayed);
        UiViewCompositionActionResponse response = actionResponse(
                action,
                context,
                capabilities,
                mapActionOutput(binding, plan.result()),
                replayed);
        actionReceiptService.complete(receipt.receiptCommand(), response);
        return response;
    }

    /**
     * 处理动作响应，并将结果传给后续步骤。
     *
     * @param action 动作标识，决定后续动作响应采用的处理分支
     * @param context 执行上下文，向后续动作响应步骤传递身份、配置或状态
     * @param capabilities 能力集合，作为 {@code actionCapabilities} 的输入影响后续处理
     * @param result 结果，供本方法处理动作响应时使用
     * @param replayed {@code replayed}，供本方法处理动作响应时使用
     * @return 处理后的动作响应结果，供调用方继续处理
     */
    private UiViewCompositionActionResponse actionResponse(
            ValidatedAction action,
            ActionContext context,
            Map<String, UiViewCompositionActionCapabilityDTO> capabilities,
            Map<String, Object> result,
            boolean replayed) {
        return UiViewCompositionActionResponse.builder()
                .operationId(action.operationId())
                .action(action.action())
                .sourceRecordId(context.claims().sourceRecordId())
                .actionCapabilities(capabilities)
                .actionResult(result)
                .replayed(replayed)
                .build();
    }

    /**
     * 处理动作{@code execute}请求，并将结果传给后续步骤。
     *
     * @param action 动作标识，决定后续动作{@code execute}请求采用的处理分支
     * @param context 执行上下文，向后续动作{@code execute}请求步骤传递身份、配置或状态
     * @param binding 绑定，作为 {@code request.setOperationCode} 的输入影响后续处理
     * @param input 待处理动作{@code execute}请求的原始输入，结果供调用方继续使用
     * @return 处理后的动作{@code execute}请求结果，供调用方继续处理
     */
    private UiExtensionExecuteRequest actionExecuteRequest(
            ValidatedAction action,
            ActionContext context,
            Map<String, Object> binding,
            Map<String, Object> input) {
        UiExtensionExecuteRequest request = new UiExtensionExecuteRequest();
        request.setUsage(UiDataSourceUsages.RELATED_CONTENT_ACTION);
        request.setOperationCode(text(binding.get("operationCode")));
        request.setConfigType(context.claims().ownerType());
        request.setConfigId(context.claims().ownerId());
        request.setReleaseId(context.claims().releaseId());
        request.setReleaseVersion(context.claims().releaseVersion());
        request.setEntityCode(context.sourceEntity().getEntityCode());
        request.setTargetType("COMPOSITION_ACTION");
        request.setTargetKey(context.claims().compositionKey()
                + "::" + action.action());
        request.setInput(input);
        request.setServerPinnedRelease(true);
        request.setServerIdempotencyKey("view-composition-action:"
                + context.claims().releaseId()
                + ":" + context.claims().compositionKey()
                + ":" + action.operationId());
        return request;
    }

    /**
     * 校验动作绑定；不满足约束时阻止后续处理。
     *
     * @param context 执行上下文，向后续动作绑定步骤传递身份、配置或状态
     * @param binding 绑定，作为 {@code requiredField} 的输入影响后续处理
     * @return 校验后的动作绑定结果，供调用方继续处理
     */
    private UiInterfaceExtensionService.ActionOperationDescriptor
            validateActionBinding(
            ActionContext context,
            Map<String, Object> binding) {
        String snapshot = requiredField(
                binding, "executableSnapshot", "动作接口快照");
        String hash = requiredField(
                binding, "definitionHash", "动作接口哈希");
        boolean extensionReference = StringUtils.hasText(
                text(binding.get("extensionId")));
        String sourceCode = requiredField(
                binding,
                extensionReference ? "extensionKey" : "sourceCode",
                extensionReference ? "接口扩展编码" : "历史接口服务编码");
        Integer revision = integer(binding.get(extensionReference
                ? "extensionRevision" : "serviceRevision"));
        if (extensionReference) {
            return dataSourceService.validatePinnedActionExtension(
                    snapshot,
                    hash,
                    requiredField(binding, "extensionId", "接口扩展"),
                    sourceCode,
                    revision,
                    context.claims().ownerType());
        }
        return dataSourceService.validatePinnedActionOperation(
                snapshot,
                hash,
                requiredField(binding, "serviceId", "历史接口服务"),
                sourceCode,
                revision,
                requiredField(binding, "operationCode", "历史接口操作"),
                context.claims().ownerType());
    }

    /**
     * 校验并获取动作服务；不满足约束时阻止后续处理。
     *
     * @param context 执行上下文，向后续动作服务步骤传递身份、配置或状态
     * @param actionKey 动作键，后续用于授权校验、关联或幂等去重
     * @return 动作服务键值结果，供调用方继续处理
     */
    private Map<String, Object> requireActionService(
            ActionContext context,
            String actionKey) {
        return actionServices(context).stream()
                .filter(item -> normalize(text(item.get("actionKey")))
                        .equals(normalize(actionKey)))
                .findFirst()
                .orElseThrow(() -> forbidden(
                        "VIEW_COMPOSITION_ACTION_NOT_BOUND",
                        "已发布关联内容未显式绑定该接口动作"));
    }

    /**
     * 整理动作{@code services}数据，供调用方遍历或继续处理。
     *
     * @param context 执行上下文，向后续动作{@code services}步骤传递身份、配置或状态
     * @return 界面视图组合动作集合，供调用方遍历或展示
     */
    private List<Map<String, Object>> actionServices(
            ActionContext context) {
        // 能力签发和执行都从此处读取绑定；普通关系的旧发布即使残留动作接口也不再开放。
        if (!UiViewCompositionExtensionPolicy.allowsInterfaceExtensions(context.config().get("relation"))) {
            return List.of();
        }
        return mapList(map(context.config().get("specialHandling"))
                .get("actionServices"));
    }

    /**
     * 只按发布映射读取来源或已授权目标字段，不传递整行 data。
     *
     * @param context 执行上下文，向后续动作输入步骤传递身份、配置或状态
     * @param binding 绑定，作为 {@code mapList} 的输入影响后续处理
     * @param source 待处理动作输入的原始输入，结果供调用方继续使用
     * @param targets 目标集合，供本方法处理动作输入时使用
     * @return 动作输入键值结果，供调用方继续处理
     */
    private Map<String, Object> actionInput(
            ActionContext context,
            Map<String, Object> binding,
            EntityDataDTO source,
            List<EntityDataDTO> targets) {
        List<Map<String, Object>> mappings = mapList(
                binding.get("inputMappings"));
        Map<String, Object> input = new LinkedHashMap<>();
        if (mappings.isEmpty()) {
            input.put("recordId", source.getId());
            return Collections.unmodifiableMap(input);
        }
        for (Map<String, Object> mapping : mappings) {
            String targetPath = firstText(
                    mapping.get("target"), mapping.get("targetField"));
            if (!safeActionPath(targetPath)) {
                throw conflict(
                        "VIEW_COMPOSITION_ACTION_MAPPING_INVALID",
                        "动作接口输入目标路径不合法");
            }
            Object value = mapping.containsKey("literal")
                    ? mapping.get("literal")
                    : actionSourceValue(
                    context,
                    source,
                    targets,
                    firstText(
                            mapping.get("source"),
                            mapping.get("sourceField")));
            if (!Boolean.FALSE.equals(mapping.get("required"))
                    && value == null) {
                throw conflict(
                        "VIEW_COMPOSITION_ACTION_MAPPING_VALUE_MISSING",
                        "动作接口输入必填值为空: " + targetPath);
            }
            putActionPath(input, targetPath, value);
        }
        return Collections.unmodifiableMap(input);
    }

    /**
     * 处理动作来源值，并将结果传给后续步骤。
     *
     * @param context 执行上下文，向后续动作来源值步骤传递身份、配置或状态
     * @param source 待处理动作来源值的原始输入，结果供调用方继续使用
     * @param targets 目标集合，作为 {@code recordValue} 的输入影响后续处理
     * @param path 路径，供本方法处理动作来源值时使用
     * @return 处理后的动作来源值结果，供调用方继续处理
     */
    private Object actionSourceValue(
            ActionContext context,
            EntityDataDTO source,
            List<EntityDataDTO> targets,
            String path) {
        if ("targetIds".equals(path)) {
            return targets.stream().map(EntityDataDTO::getId).toList();
        }
        String[] segments = StringUtils.hasText(path)
                ? path.split("\\.") : new String[0];
        if (segments.length != 2) {
            throw conflict(
                    "VIEW_COMPOSITION_ACTION_MAPPING_INVALID",
                    "动作接口输入来源只支持 source.字段、target.字段或 targetIds");
        }
        String fieldCode = segments[1];
        if ("source".equals(segments[0])) {
            requireReadableSourcePublishedField(context, fieldCode);
            return recordValue(source, fieldCode);
        }
        if (!"target".equals(segments[0]) || targets.size() != 1) {
            throw conflict(
                    "VIEW_COMPOSITION_ACTION_MAPPING_INVALID",
                    "读取 target.字段时必须且只能选择一条目标记录");
        }
        requireReadableTargetPublishedField(context, fieldCode);
        return recordValue(targets.get(0), fieldCode);
    }

    /**
     * 校验并获取可读目标已发布字段；不满足约束时阻止后续处理。
     *
     * @param context 执行上下文，向后续可读目标已发布字段步骤传递身份、配置或状态
     * @param fieldCode 字段编码，后续用于校验并获取可读目标已发布字段时定位或关联目标
     */
    private void requireReadableTargetPublishedField(
            ActionContext context,
            String fieldCode) {
        EntityField field = requirePinnedField(
                context.targetSchema(), fieldCode, "目标");
        if (Boolean.FALSE.equals(field.getRuntimeReadable())) {
            throw forbidden(
                    "VIEW_COMPOSITION_ACTION_MAPPING_FIELD_FORBIDDEN",
                    "目标字段不可用于接口动作: " + fieldCode);
        }
        if (LIST.equals(context.resolved().getTargetContentType())) {
            requireReadableTargetListField(context, fieldCode);
            return;
        }
        Map<String, Object> publishedField = mapList(
                context.targetSnapshot().get("legacyFields")).stream()
                .filter(item -> Objects.equals(
                        fieldCode, text(item.get("fieldCode"))))
                .findFirst()
                .orElse(null);
        if (publishedField == null || truthy(publishedField.get("isHidden"))) {
            throw forbidden(
                    "VIEW_COMPOSITION_ACTION_MAPPING_FIELD_FORBIDDEN",
                    "目标字段未在固定表单中显示: " + fieldCode);
        }
    }

    /**
     * 整理映射动作输出数据，供调用方遍历或继续处理。
     *
     * @param binding 绑定，作为 {@code mapList} 的输入影响后续处理
     * @param raw 待处理映射动作输出的原始输入，结果供调用方继续使用
     * @return 映射动作输出键值结果，供调用方继续处理
     */
    private Map<String, Object> mapActionOutput(
            Map<String, Object> binding,
            Object raw) {
        Map<String, Object> source = map(raw);
        List<Map<String, Object>> mappings = mapList(
                binding.get("outputMappings"));
        if (mappings.isEmpty()) {
            return Collections.unmodifiableMap(source);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map<String, Object> mapping : mappings) {
            String sourcePath = firstText(
                    mapping.get("source"), mapping.get("sourceField"));
            String targetPath = firstText(
                    mapping.get("target"), mapping.get("targetField"));
            if (!safeActionPath(sourcePath) || !safeActionPath(targetPath)) {
                throw conflict(
                        "VIEW_COMPOSITION_ACTION_MAPPING_INVALID",
                        "动作接口输出映射路径不合法");
            }
            putActionPath(result, targetPath,
                    actionPathValue(source, sourcePath));
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * 处理动作路径值，并将结果传给后续步骤。
     *
     * @param source 待处理动作路径值的原始输入，结果供调用方继续使用
     * @param path 路径，供本方法处理动作路径值时使用
     * @return 处理后的动作路径值结果，供调用方继续处理
     */
    private Object actionPathValue(
            Map<String, Object> source,
            String path) {
        Object current = source;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> value)) {
                return null;
            }
            current = value.get(part);
        }
        return current;
    }

    /**
     * 写入动作路径；后续读取或执行将使用更新后的状态。
     *
     * @param target 目标，供本方法写入动作路径时使用
     * @param path 路径，作为 {@code conflict} 的输入影响后续处理
     * @param value 待写入动作路径的原始输入，结果供调用方继续使用
     */
    @SuppressWarnings("unchecked")
    private void putActionPath(
            Map<String, Object> target,
            String path,
            Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = target;
        for (int index = 0; index < parts.length - 1; index++) {
            Object child = current.get(parts[index]);
            if (child == null) {
                Map<String, Object> created = new LinkedHashMap<>();
                current.put(parts[index], created);
                current = created;
            } else if (child instanceof Map<?, ?> map) {
                current = (Map<String, Object>) map;
            } else {
                throw conflict(
                        "VIEW_COMPOSITION_ACTION_MAPPING_INVALID",
                        "动作接口映射目标路径冲突: " + path);
            }
        }
        current.put(parts[parts.length - 1], value);
    }

    /**
     * 判断安全动作路径条件是否成立，供调用方选择后续分支。
     *
     * @param path 路径，供本方法处理安全动作路径时使用
     * @return 安全动作路径条件成立时为 true，否则为 false
     */
    private boolean safeActionPath(String path) {
        return StringUtils.hasText(path)
                && path.matches(
                "[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*){0,3}");
    }

    /**
     * 将 Provider 意图收窄为当前关联目标实体的白名单命令。
     *
     * <p>单次最多 100 条；每条重新校验 CRUD 权限、记录数据范围和发布字段，
     * operationId、幂等键及上下文全部由平台生成。MutationPort 随后还会执行
     * 实体字段校验、唯一性和版本规则，Provider 无法跳过任一层。</p>
     *
     * @param action 动作标识，决定后续动作变更{@code commands}采用的处理分支
     * @param context 执行上下文，向后续动作变更{@code commands}步骤传递身份、配置或状态
     * @param plan 执行方案，后续决定操作步骤和校验约束
     * @param batchIdempotencyKey 批次幂等键，后续用于授权校验、关联或幂等去重
     * @return 实体变更命令集合，供调用方遍历或展示
     */
    private List<EntityMutationCommand> actionMutationCommands(
            ValidatedAction action,
            ActionContext context,
            UiActionCommandPlan plan,
            String batchIdempotencyKey) {
        List<UiActionMutationCommand> planned = plan.commands();
        if (planned.isEmpty()) {
            throw conflict(
                    "VIEW_COMPOSITION_ACTION_PLAN_EMPTY",
                    "本地写入操作未生成实体变更命令");
        }
        if (planned.size() > MAX_ACTION_MUTATIONS) {
            throw new IllegalArgumentException(
                    "单次本地接口动作最多处理 "
                            + MAX_ACTION_MUTATIONS + " 条实体变更");
        }
        requireDynamic(context.targetEntity(), "目标实体");
        Set<String> authorizedTargetIds = new LinkedHashSet<>(
                action.targetRecordIds());
        for (UiActionMutationCommand item : planned) {
            if (item == null || item.operationType() == null
                    || !StringUtils.hasText(item.entityCode())) {
                throw new IllegalArgumentException(
                        "本地接口动作返回了不完整的实体变更命令");
            }
            if (item.operationType() != EntityMutationOperationType.CREATE
                    && !StringUtils.hasText(item.recordId())) {
                throw new IllegalArgumentException(
                        "更新或删除命令必须指定目标记录ID");
            }
            // Provider 只能处理本次业务上下文已解析并由平台重读授权的目标集合。
            // 不能把 Provider 返回的 recordId 当作新的授权来源，否则它可越过
            // 关联内容固定筛选去修改同实体中其它虽可见但与当前内容无关的记录。
            if (item.operationType() != EntityMutationOperationType.CREATE
                    && !authorizedTargetIds.contains(item.recordId())) {
                throw forbidden(
                        "VIEW_COMPOSITION_ACTION_TARGET_OUT_OF_SCOPE",
                        "接口动作只能修改本次已选择且属于当前关联内容的目标记录");
            }
        }

        List<LockKey> locks = planned.stream()
                .filter(item -> item != null
                        && item.operationType()
                        != EntityMutationOperationType.CREATE)
                .map(item -> new LockKey(
                        item.entityCode(), item.recordId()))
                .distinct()
                .sorted(Comparator.comparing(LockKey::entityCode)
                        .thenComparing(LockKey::recordId))
                .toList();
        // 锁前读用于尽早拒绝明显越权，锁后仍会再次读取，防止等待期间范围变化。
        for (LockKey key : locks) {
            requireActionTarget(context, key.entityCode(), key.recordId());
        }
        locks.forEach(key -> aggregateWriter.lock(
                key.entityCode(), key.recordId()));
        for (LockKey key : locks) {
            requireActionTarget(context, key.entityCode(), key.recordId());
        }

        List<EntityMutationCommand> commands = new ArrayList<>();
        for (int index = 0; index < planned.size(); index++) {
            UiActionMutationCommand item = planned.get(index);
            commands.add(actionMutationCommand(
                    action,
                    context,
                    item,
                    index,
                    batchIdempotencyKey));
        }
        return List.copyOf(commands);
    }

    /**
     * 校验并获取动作目标；不满足约束时阻止后续处理。
     *
     * @param context 执行上下文，向后续动作目标步骤传递身份、配置或状态
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @return 校验并获取后的动作目标结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private EntityDataDTO requireActionTarget(
            ActionContext context,
            String entityCode,
            String recordId) {
        if (!Objects.equals(
                context.targetEntity().getEntityCode(), entityCode)) {
            throw forbidden(
                    "VIEW_COMPOSITION_ACTION_TARGET_FORBIDDEN",
                    "本地接口动作只能变更已发布关联内容的目标实体");
        }
        if (!StringUtils.hasText(recordId)) {
            throw new IllegalArgumentException(
                    "更新或删除命令必须指定目标记录ID");
        }
        readAccessible(
                context.targetEntity(),
                recordId,
                LIST.equals(context.resolved().getTargetContentType())
                        ? context.resolved().getTargetContentKey() : null);
        // 锁前和锁后都重新执行关联内容固定筛选，不能只依赖目标列表的一般
        // 数据范围。记录在等待锁期间脱离当前关联集合时必须整体失败。
        return requireResolvedMembership(context, recordId);
    }

    /**
     * 处理动作变更命令，并将结果传给后续步骤。
     *
     * @param action 动作标识，决定后续动作变更命令采用的处理分支
     * @param context 执行上下文，向后续动作变更命令步骤传递身份、配置或状态
     * @param item 条目，作为 {@code requireActionTarget} 的输入影响后续处理
     * @param index 索引，作为 {@code sha256} 的输入影响后续处理
     * @param batchIdempotencyKey 批次幂等键，后续用于授权校验、关联或幂等去重
     * @return 处理后的动作变更命令结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private EntityMutationCommand actionMutationCommand(
            ValidatedAction action,
            ActionContext context,
            UiActionMutationCommand item,
            int index,
            String batchIdempotencyKey) {
        if (item == null
                || !Objects.equals(
                context.targetEntity().getEntityCode(), item.entityCode())) {
            throw forbidden(
                    "VIEW_COMPOSITION_ACTION_TARGET_FORBIDDEN",
                    "本地接口动作只能变更已发布关联内容的目标实体");
        }
        EntityMutationOperationType operation = item.operationType();
        if (!Set.of(
                EntityMutationOperationType.CREATE,
                EntityMutationOperationType.UPDATE,
                EntityMutationOperationType.DELETE).contains(operation)) {
            throw forbidden(
                    "VIEW_COMPOSITION_ACTION_OPERATION_FORBIDDEN",
                    "本地接口动作只允许新增、更新或删除实体记录");
        }
        EntityPermissionAction permission = switch (operation) {
            case CREATE -> EntityPermissionAction.CREATE;
            case UPDATE -> EntityPermissionAction.UPDATE;
            case DELETE -> EntityPermissionAction.DELETE;
            default -> throw new IllegalStateException("不支持的实体操作");
        };
        capabilityService.requireStandardPermission(
                context.targetEntity().getEntityCode(), permission);
        if (operation != EntityMutationOperationType.CREATE) {
            requireActionTarget(
                    context, item.entityCode(), item.recordId());
        }
        Map<String, Object> data = item.data() == null
                ? Map.of() : item.data();
        if (operation == EntityMutationOperationType.DELETE
                && !data.isEmpty()) {
            throw new IllegalArgumentException(
                    "删除命令不能携带字段数据");
        }
        if (operation != EntityMutationOperationType.DELETE
                && data.isEmpty()) {
            throw new IllegalArgumentException(
                    "新增或更新命令必须包含至少一个字段");
        }
        for (String fieldCode : data.keySet()) {
            EntityField field = requirePinnedField(
                    context.targetSchema(), fieldCode, "目标");
            if (Boolean.TRUE.equals(field.getIsSystem())
                    || Boolean.FALSE.equals(field.getEditable())) {
                throw forbidden(
                        "VIEW_COMPOSITION_ACTION_FIELD_FORBIDDEN",
                        "目标字段不可由接口动作修改: " + fieldCode);
            }
            if (FORM.equals(context.resolved().getTargetContentType())) {
                requireEditableTargetFormField(
                        context,
                        fieldCode,
                        operation == EntityMutationOperationType.CREATE
                                ? "create" : "edit");
            }
        }
        String commandId = action.operationId() + ":svc:" + index;
        // 子命令键必须从已绑定 tenant、user、来源记录和宿主发布的批次键派生。
        // 客户端 operationId 只作为该批次命名空间内的 nonce，不能在全局
        // entity_mutation_receipt 唯一键上造成跨租户重放或稳定冲突。
        String idempotencyKey = "uivc-service:"
                + sha256(String.join("|",
                batchIdempotencyKey,
                "service",
                String.valueOf(index),
                item.entityCode(),
                String.valueOf(item.recordId()),
                item.operationType().name()));
        EntityMutationSourceType sourceType = FORM.equals(
                context.claims().ownerType())
                ? EntityMutationSourceType.FORM
                : EntityMutationSourceType.LIST;
        EntityMutationContext mutationContext = EntityMutationContext.builder(
                        sourceType,
                        "VIEW_COMPOSITION_INTERFACE_ACTION",
                        "关联内容接口动作")
                .sourceId(context.claims().ownerId())
                .sourceRecord(
                        context.sourceEntity().getEntityCode(),
                        context.claims().sourceRecordId())
                .trace(action.operationId(), idempotencyKey)
                .extraParams(Map.of(
                        "compositionKey", context.claims().compositionKey(),
                        "actionKey", action.action(),
                        "ownerReleaseId", context.claims().releaseId(),
                        "ownerReleaseVersion", context.claims().releaseVersion()))
                .build();
        Map<String, Object> payload = operation
                == EntityMutationOperationType.DELETE
                ? Map.of()
                : Map.of("data", Collections.unmodifiableMap(
                new LinkedHashMap<>(data)));
        return new EntityMutationCommand(
                commandId,
                item.entityCode(),
                operation == EntityMutationOperationType.CREATE
                        ? null : item.recordId(),
                operation,
                payload,
                mutationContext);
    }

    /**
     * 锁定变更行；避免后续并发处理覆盖状态。
     *
     * @param context 执行上下文，向后续变更行步骤传递身份、配置或状态
     * @param source 待锁定变更行的原始输入，结果供调用方继续使用
     * @param targets 目标集合，供本方法锁定变更行时使用
     */
    private void lockMutationRows(
            ActionContext context,
            EntityDataDTO source,
            List<EntityDataDTO> targets) {
        List<LockKey> keys = new ArrayList<>();
        if (context.sourceEntity().getStorageMode()
                == EntityDefinition.StorageMode.DYNAMIC) {
            keys.add(new LockKey(
                    context.sourceEntity().getEntityCode(), source.getId()));
        }
        if (context.targetEntity().getStorageMode()
                == EntityDefinition.StorageMode.DYNAMIC) {
            targets.forEach(target -> keys.add(new LockKey(
                    context.targetEntity().getEntityCode(), target.getId())));
        }
        keys.stream()
                .distinct()
                .sorted(Comparator.comparing(LockKey::entityCode)
                        .thenComparing(LockKey::recordId))
                .forEach(key -> aggregateWriter.lock(
                        key.entityCode(), key.recordId()));
    }

    /**
     * 读取目标集合；查询结果供调用方展示或继续处理。
     *
     * @param context 执行上下文，向后续目标集合步骤传递身份、配置或状态
     * @param ids ID 集合，供本方法读取目标集合时使用
     * @return 实体数据集合，供调用方遍历或展示
     */
    private List<EntityDataDTO> readTargets(
            ActionContext context,
            List<String> ids) {
        if (context.targetEntity().getStorageMode()
                == EntityDefinition.StorageMode.DYNAMIC) {
            capabilityService.requireStandardPermission(
                    context.targetEntity().getEntityCode(),
                    EntityPermissionAction.VIEW);
        }
        List<EntityDataDTO> result = new ArrayList<>();
        for (String id : ids) {
            EntityDataDTO record = readAccessible(
                    context.targetEntity(),
                    id,
                    LIST.equals(context.resolved().getTargetContentType())
                            ? context.resolved().getTargetContentKey()
                            : null);
            record = requireResolvedMembership(context, id);
            result.add(record);
        }
        return List.copyOf(result);
    }

    /**
     * 校验并获取已解析{@code membership}；不满足约束时阻止后续处理。
     *
     * @param context 执行上下文，向后续已解析{@code membership}步骤传递身份、配置或状态
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @return 校验并获取后的已解析{@code membership}结果，供调用方继续处理
     */
    private EntityDataDTO requireResolvedMembership(
            ActionContext context,
            String recordId) {
        UiViewCompositionResolveResponse resolved = context.resolved();
        if (resolved.isMatchNone()) {
            throw conflict(
                    "VIEW_COMPOSITION_ACTION_TARGET_OUT_OF_SCOPE",
                    "当前关联条件没有可操作的数据");
        }
        if (FORM.equals(resolved.getTargetContentType())) {
            if (!Objects.equals(recordId, resolved.getTargetRecordId())) {
                throw forbidden(
                        "VIEW_COMPOSITION_ACTION_TARGET_OUT_OF_SCOPE",
                        "目标记录不属于当前关联内容");
            }
            return readAccessible(context.targetEntity(), recordId, null);
        }
        Map<String, Object> condition = new LinkedHashMap<>(
                resolved.getFixedFilters() == null
                        ? Map.of() : resolved.getFixedFilters());
        Object fixedId = condition.get("id");
        String fixedOperator = normalize(text(condition.get("id_op")));
        if ((fixedId != null
                && !Objects.equals(String.valueOf(fixedId), recordId))
                || StringUtils.hasText(fixedOperator)
                && !"EQ".equals(fixedOperator)
                || condition.containsKey("id_start")
                || condition.containsKey("id_end")) {
            throw forbidden(
                    "VIEW_COMPOSITION_ACTION_TARGET_OUT_OF_SCOPE",
                    "目标记录不满足关联内容的固定条件");
        }
        condition.put("id", recordId);
        condition.put("id_op", "EQ");
        PageResult<EntityDataDTO> page = context.targetEntity().getStorageMode()
                == EntityDefinition.StorageMode.SYSTEM
                ? systemEntityReadService.findPage(
                        context.targetEntity().getEntityCode(),
                        condition, 1, 2)
                : dynamicDataService.findPage(
                        context.targetEntity().getEntityCode(),
                        resolved.getTargetContentKey(),
                        condition, 1, 2);
        if (page.getTotal() != 1
                || page.getRecords().isEmpty()
                || !Objects.equals(
                recordId, page.getRecords().get(0).getId())) {
            throw forbidden(
                    "VIEW_COMPOSITION_ACTION_TARGET_OUT_OF_SCOPE",
                    "目标记录不满足关联内容的固定条件或数据权限");
        }
        return page.getRecords().get(0);
    }

    /**
     * 读取可访问；查询结果供调用方展示或继续处理。
     *
     * @param entity 实体，作为 {@code dynamicDataService.findAccessibleById} 的输入影响后续处理
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @return 读取后的可访问结果，供调用方继续处理
     */
    private EntityDataDTO readAccessible(
            EntityDefinition entity,
            String recordId,
            String listKey) {
        return entity.getStorageMode() == EntityDefinition.StorageMode.SYSTEM
                ? systemEntityReadService.findById(
                        entity.getEntityCode(), recordId)
                : dynamicDataService.findAccessibleById(
                        entity.getEntityCode(), recordId, listKey);
    }

    /**
     * 校验并获取可读目标列表字段；不满足约束时阻止后续处理。
     *
     * @param context 执行上下文，向后续可读目标列表字段步骤传递身份、配置或状态
     * @param fieldCode 字段编码，后续用于校验并获取可读目标列表字段时定位或关联目标
     */
    private void requireReadableTargetListField(
            ActionContext context,
            String fieldCode) {
        EntityField field = requirePinnedField(
                context.targetSchema(), fieldCode, "目标");
        if (Boolean.FALSE.equals(field.getRuntimeReadable())) {
            throw forbidden(
                    "VIEW_COMPOSITION_MAPPING_FIELD_FORBIDDEN",
                    "目标字段不可用于接口动作: " + fieldCode);
        }
        Map<String, Object> list = map(
                context.targetSnapshot().get("list"));
        Map<String, Object> publishedField = mapList(list.get("fields"))
                .stream()
                .filter(item -> Objects.equals(
                        fieldCode, text(item.get("fieldCode"))))
                .findFirst()
                .orElse(null);
        if (publishedField == null
                || !truthy(publishedField.get("showInList"))) {
            throw forbidden(
                    "VIEW_COMPOSITION_MAPPING_FIELD_FORBIDDEN",
                    "目标字段未在固定列表版本中显示，不能用于接口动作: " + fieldCode);
        }
        String sourceType = normalize(text(
                publishedField.get("dataSourceType")));
        if (StringUtils.hasText(sourceType)
                && !Set.of("ENTITY_FIELD", "REFERENCE").contains(sourceType)) {
            throw forbidden(
                    "VIEW_COMPOSITION_MAPPING_FIELD_FORBIDDEN",
                    "计算列或自定义列不能作为接口动作的实体字段输入: " + fieldCode);
        }
    }

    /**
     * 判断可编辑模式条件是否成立，供调用方选择后续分支。
     *
     * @param field 字段，供本方法处理可编辑模式时使用
     * @param mode 模式标识，决定后续可编辑模式采用的处理分支
     * @return 可编辑模式条件成立时为 true，否则为 false
     */
    private boolean editableInMode(
            Map<String, Object> field,
            String mode) {
        Object raw = field.get("extensionConfig");
        if (!StringUtils.hasText(text(raw))) {
            return true;
        }
        try {
            Map<String, Object> extension = raw instanceof Map<?, ?>
                    ? map(raw)
                    : objectMapper.readValue(
                            String.valueOf(raw), new TypeReference<>() {});
            Map<String, Object> modeAccess = map(
                    map(extension.get("modes")).get(mode));
            return !Boolean.FALSE.equals(modeAccess.get("visible"))
                    && !Boolean.FALSE.equals(modeAccess.get("editable"));
        } catch (Exception exception) {
            throw conflict(
                    "VIEW_COMPOSITION_MAPPING_FIELD_CONFIG_INVALID",
                    "固定表单版本中的字段模式权限无法解析");
        }
    }

    /**
     * 整理能力集合数据，供调用方遍历或继续处理。
     *
     * @param context 执行上下文，向后续能力集合步骤传递身份、配置或状态
     * @return 能力集合键值结果，供调用方继续处理
     */
    private Map<String, UiViewCompositionActionCapabilityDTO> capabilities(
            ActionContext context) {
        Set<String> declared = new LinkedHashSet<>(
                stringList(context.config().get("actions")));
        Map<String, UiViewCompositionActionCapabilityDTO> result =
                new LinkedHashMap<>();
        for (String action : List.of(
                "VIEW", "CREATE", "EDIT")) {
            if (!declared.contains(action)) {
                result.put(action, denied("未在已发布配置中启用"));
                continue;
            }
            result.put(action, capability(context, action));
        }
        for (Map<String, Object> binding : actionServices(context)) {
            String actionKey = normalize(text(binding.get("actionKey")));
            if (!StringUtils.hasText(actionKey)
                    || result.containsKey(actionKey)) {
                continue;
            }
            try {
                validateActionBinding(context, binding);
                result.put(actionKey, allowed());
            } catch (RuntimeException exception) {
                result.put(actionKey, denied(exception.getMessage()));
            }
        }
        if (context.resolved().isReadonlyContext()) {
            // 只读流程入口或父层声明不得被子内容的动作配置重新开放。
            result.replaceAll((action, capability) -> "VIEW".equals(action) ? capability : denied("当前关联路径只允许查看"));
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * 处理能力，并将结果传给后续步骤。
     *
     * @param context 执行上下文，向后续能力步骤传递身份、配置或状态
     * @param action 动作标识，决定后续能力采用的处理分支
     * @return 处理后的能力结果，供调用方继续处理
     */
    private UiViewCompositionActionCapabilityDTO capability(
            ActionContext context,
            String action) {
        return switch (action) {
            case "VIEW" -> allowed();
            case "CREATE" -> createCapability(context);
            case "EDIT" -> editCapability(context);
            default -> denied("动作不受支持");
        };
    }

    /**
     * 创建能力；结果供后续流程传递或持久化。
     *
     * @param context 执行上下文，向后续能力步骤传递身份、配置或状态
     * @return 创建后的能力结果，供调用方继续处理
     */
    private UiViewCompositionActionCapabilityDTO createCapability(
            ActionContext context) {
        if (!FORM.equals(context.resolved().getTargetContentType())) {
            return denied("新增记录只适用于目标表单");
        }
        if (context.targetEntity().getStorageMode()
                != EntityDefinition.StorageMode.DYNAMIC) {
            return denied("系统实体不支持通过关联内容新增");
        }
        if (!hasPermission(
                context.targetEntity(), EntityPermissionAction.CREATE)) {
            return denied("没有目标实体新增权限");
        }
        if (!hasPermission(
                context.targetEntity(), EntityPermissionAction.VIEW)) {
            return denied("没有目标实体查看权限");
        }
        Map<String, Object> create = map(
                map(context.config().get("actionSettings")).get("create"));
        try {
            return allowed(resolveCreateInitialValues(context, create));
        } catch (RuntimeException exception) {
            return denied(exception.getMessage());
        }
    }

    /**
     * 处理{@code edit}能力，并将结果传给后续步骤。
     *
     * @param context 执行上下文，向后续{@code edit}能力步骤传递身份、配置或状态
     * @return 处理后的{@code edit}能力结果，供调用方继续处理
     */
    private UiViewCompositionActionCapabilityDTO editCapability(
            ActionContext context) {
        if (!FORM.equals(context.resolved().getTargetContentType())) {
            return denied("编辑记录只适用于目标表单");
        }
        if (!StringUtils.hasText(context.resolved().getTargetRecordId())
                || context.resolved().isMatchNone()) {
            return denied("当前关联条件没有解析到可编辑的目标记录");
        }
        if (context.targetEntity().getStorageMode()
                != EntityDefinition.StorageMode.DYNAMIC) {
            return denied("系统实体不支持通过关联内容编辑");
        }
        if (!hasPermission(
                context.targetEntity(), EntityPermissionAction.VIEW)) {
            return denied("没有目标实体查看权限");
        }
        if (!hasPermission(
                context.targetEntity(), EntityPermissionAction.UPDATE)) {
            return denied("没有目标实体编辑权限");
        }
        try {
            EntityDataDTO record = readAccessible(
                    context.targetEntity(),
                    context.resolved().getTargetRecordId(),
                    null);
            if (record == null) {
                return denied("目标记录不在当前用户可编辑的数据范围内");
            }
            return allowed();
        } catch (RuntimeException exception) {
            return denied("目标记录不在当前用户可编辑的数据范围内");
        }
    }

    /**
     * 解析创建初始值集合；输出作为后续校验或处理的输入。
     *
     * @param context 执行上下文，向后续创建初始值集合步骤传递身份、配置或状态
     * @param create 创建，作为 {@code mapList} 的输入影响后续处理
     * @return 创建初始值集合键值结果，供调用方继续处理
     */
    private Map<String, Object> resolveCreateInitialValues(
            ActionContext context,
            Map<String, Object> create) {
        List<Map<String, Object>> mappings = mapList(
                create.get("initialMappings"));
        if (mappings.isEmpty()) {
            return Map.of();
        }
        EntityDataDTO source = readAccessible(
                context.sourceEntity(),
                context.claims().sourceRecordId(),
                null);
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map<String, Object> mapping : mappings) {
            if (mapping.containsKey("literal")) {
                throw conflict(
                        "VIEW_COMPOSITION_CREATE_MAPPING_INVALID",
                        "新增初值只能来自当前记录字段");
            }
            String sourceField = firstText(
                    mapping.get("sourceField"), mapping.get("source"));
            String targetField = firstText(
                    mapping.get("targetField"), mapping.get("target"));
            if (!StringUtils.hasText(sourceField)
                    || !StringUtils.hasText(targetField)) {
                throw conflict(
                        "VIEW_COMPOSITION_CREATE_MAPPING_INVALID",
                        "新增初值必须同时指定当前记录字段和目标表单字段");
            }
            requireReadableSourcePublishedField(context, sourceField);
            requireEditableTargetFormField(context, targetField, "create");
            if (values.containsKey(targetField)) {
                throw conflict(
                        "VIEW_COMPOSITION_CREATE_MAPPING_INVALID",
                        "新增初值不能重复写入目标字段: " + targetField);
            }
            Object value = recordValue(source, sourceField);
            if (!Boolean.FALSE.equals(mapping.get("required"))
                    && value == null) {
                throw conflict(
                        "VIEW_COMPOSITION_CREATE_MAPPING_VALUE_MISSING",
                        "当前记录字段 “" + sourceField
                                + "” 为空，无法生成必填新增初值");
            }
            if (value instanceof Map<?, ?>) {
                throw conflict(
                        "VIEW_COMPOSITION_CREATE_MAPPING_VALUE_INVALID",
                        "当前记录字段不能以对象形式作为新增初值: "
                                + sourceField);
            }
            values.put(targetField, value);
        }
        return Collections.unmodifiableMap(values);
    }

    /**
     * 校验并获取可读来源已发布字段；不满足约束时阻止后续处理。
     *
     * @param context 执行上下文，向后续可读来源已发布字段步骤传递身份、配置或状态
     * @param fieldCode 字段编码，后续用于校验并获取可读来源已发布字段时定位或关联目标
     */
    private void requireReadableSourcePublishedField(
            ActionContext context,
            String fieldCode) {
        EntityField field = requirePinnedField(
                context.sourceSchema(), fieldCode, "来源");
        if (Boolean.FALSE.equals(field.getRuntimeReadable())) {
            throw forbidden(
                    "VIEW_COMPOSITION_CREATE_MAPPING_FIELD_FORBIDDEN",
                    "当前记录字段不可用于新增初值: " + fieldCode);
        }
        if (FORM.equals(context.claims().ownerType())) {
            Map<String, Object> publishedField = mapList(
                    context.ownerSnapshot().get("legacyFields"))
                    .stream()
                    .filter(item -> Objects.equals(
                            fieldCode, text(item.get("fieldCode"))))
                    .findFirst()
                    .orElse(null);
            if (publishedField == null
                    || truthy(publishedField.get("isHidden"))) {
                throw forbidden(
                        "VIEW_COMPOSITION_CREATE_MAPPING_FIELD_FORBIDDEN",
                        "当前字段未在固定宿主表单中显示: " + fieldCode);
            }
            return;
        }
        Map<String, Object> list = map(context.ownerSnapshot().get("list"));
        Map<String, Object> publishedField = mapList(list.get("fields"))
                .stream()
                .filter(item -> Objects.equals(
                        fieldCode, text(item.get("fieldCode"))))
                .findFirst()
                .orElse(null);
        if (publishedField == null
                || !truthy(publishedField.get("showInList"))) {
            throw forbidden(
                    "VIEW_COMPOSITION_CREATE_MAPPING_FIELD_FORBIDDEN",
                    "当前字段未在固定宿主列表中显示: " + fieldCode);
        }
    }

    /**
     * 校验并获取可编辑目标表单字段；不满足约束时阻止后续处理。
     *
     * @param context 执行上下文，向后续可编辑目标表单字段步骤传递身份、配置或状态
     * @param fieldCode 字段编码，后续用于校验并获取可编辑目标表单字段时定位或关联目标
     * @param mode 模式标识，决定后续可编辑目标表单字段采用的处理分支
     */
    private void requireEditableTargetFormField(
            ActionContext context,
            String fieldCode,
            String mode) {
        EntityField field = requirePinnedField(
                context.targetSchema(), fieldCode, "目标");
        if (Boolean.FALSE.equals(field.getEditable())) {
            throw forbidden(
                    "VIEW_COMPOSITION_CREATE_MAPPING_FIELD_FORBIDDEN",
                    "目标实体字段不可编辑: " + fieldCode);
        }
        Map<String, Object> publishedField = mapList(
                context.targetSnapshot().get("legacyFields"))
                .stream()
                .filter(item -> Objects.equals(
                        fieldCode, text(item.get("fieldCode"))))
                .findFirst()
                .orElse(null);
        if (publishedField == null
                || truthy(publishedField.get("isHidden"))
                || truthy(publishedField.get("isReadonly"))
                || !editableInMode(publishedField, mode)) {
            throw forbidden(
                    "VIEW_COMPOSITION_CREATE_MAPPING_FIELD_FORBIDDEN",
                    "目标字段未在固定表单版本中开放新增: " + fieldCode);
        }
    }

    /**
     * 校验并获取精确目标表单；不满足约束时阻止后续处理。
     *
     * @param context 执行上下文，向后续精确目标表单步骤传递身份、配置或状态
     * @param action 动作标识，决定后续精确目标表单采用的处理分支
     * @param targetEntityCode 目标实体编码，后续用于校验并获取精确目标表单时定位或关联目标
     * @param targetRecordId 目标记录ID，后续用于校验并获取精确目标表单时定位或关联目标
     * @param targetFormId 目标表单ID，后续用于校验并获取精确目标表单时定位或关联目标
     * @param targetConfigHash 目标表单页面配置摘要，变更时拒绝写入并保留输入
     */
    private void requireExactTargetForm(
            ActionContext context,
            String action,
            String targetEntityCode,
            String targetRecordId,
            String targetFormId,
            String targetConfigHash) {
        UiViewCompositionResolveResponse resolved = context.resolved();
        if (!FORM.equals(resolved.getTargetContentType())
                || !Objects.equals(
                resolved.getTargetEntityCode(), trim(targetEntityCode))
                || !Objects.equals(
                resolved.getTargetContentId(), trim(targetFormId))) {
            throw forbidden(
                    "VIEW_COMPOSITION_FORM_CONTEXT_MISMATCH",
                    "提交的目标实体或表单发布版本与关联内容不一致");
        }
        // 配置摘要只防止旧页面按新规则写入；关联权限已经从来源行和发布路径重算。
        if (!StringUtils.hasText(targetConfigHash) || !Objects.equals(targetConfigHash, resolved.getTargetContentHash()))
            throw conflict("VIEW_COMPOSITION_CONFIG_CHANGED", "目标表单配置已变化，请保留输入并刷新后重试");
        if ("CREATE".equals(action)) {
            if (StringUtils.hasText(targetRecordId)) {
                throw forbidden(
                        "VIEW_COMPOSITION_CREATE_RECORD_FORBIDDEN",
                        "关联内容新增不能携带已有目标记录");
            }
            return;
        }
        if (!StringUtils.hasText(resolved.getTargetRecordId())
                || !Objects.equals(
                resolved.getTargetRecordId(), trim(targetRecordId))) {
            throw forbidden(
                    "VIEW_COMPOSITION_EDIT_TARGET_MISMATCH",
                    "只能编辑当前关联条件解析出的目标记录");
        }
        EntityDataDTO record = readAccessible(
                context.targetEntity(), resolved.getTargetRecordId(), null);
        if (record == null) {
            throw forbidden(
                    "VIEW_COMPOSITION_EDIT_TARGET_FORBIDDEN",
                    "目标记录不在当前用户可编辑的数据范围内");
        }
    }

    /**
     * 判断是否具有权限；判断结果决定调用方的后续分支。
     *
     * @param entity 实体，供本方法判断是否具有权限时使用
     * @param action 动作标识，决定后续权限采用的处理分支
     * @return 权限条件成立时为 true，否则为 false
     */
    private boolean hasPermission(
            EntityDefinition entity,
            EntityPermissionAction action) {
        return entity.getStorageMode() == EntityDefinition.StorageMode.SYSTEM
                || PermissionUtil.hasPermission(
                        action.permissionCode(entity.getEntityCode()));
    }

    /**
     * 处理允许，并将结果传给后续步骤。
     *
     * @return 处理后的允许结果，供调用方继续处理
     */
    private UiViewCompositionActionCapabilityDTO allowed() {
        return allowed(Map.of());
    }

    /**
     * 处理允许，并将结果传给后续步骤。
     *
     * @param initialValues 缺失行首次创建时的列值；已有行的业务值不会被覆盖
     * @return 处理后的允许结果，供调用方继续处理
     */
    private UiViewCompositionActionCapabilityDTO allowed(
            Map<String, Object> initialValues) {
        return UiViewCompositionActionCapabilityDTO.builder()
                .available(true)
                .reason("")
                .initialValues(initialValues == null
                        ? Map.of() : initialValues)
                .build();
    }

    /**
     * 处理已拒绝，并将结果传给后续步骤。
     *
     * @param reason 原因，供本方法处理已拒绝时使用
     * @return 处理后的已拒绝结果，供调用方继续处理
     */
    private UiViewCompositionActionCapabilityDTO denied(String reason) {
        return UiViewCompositionActionCapabilityDTO.builder()
                .available(false)
                .reason(StringUtils.hasText(reason) ? reason : "操作不可用")
                .build();
    }

    /**
     * 校验并获取可用；不满足约束时阻止后续处理。
     *
     * @param capabilities 能力集合，供本方法校验并获取可用时使用
     * @param action 动作标识，决定后续可用采用的处理分支
     */
    private void requireAvailable(
            Map<String, UiViewCompositionActionCapabilityDTO> capabilities,
            String action) {
        UiViewCompositionActionCapabilityDTO capability =
                capabilities.get(action);
        if (capability == null || !capability.isAvailable()) {
            throw forbidden(
                    "VIEW_COMPOSITION_ACTION_NOT_ALLOWED",
                    capability == null
                            ? "关联内容动作未启用"
                            : capability.getReason());
        }
    }

    /**
     * 解析上下文；输出作为后续校验或处理的输入。
     *
     * @param compositionContext 业务入口和关联路径，服务端重新校验后才能执行动作
     * @return 解析后的上下文结果，供调用方继续处理
     */
    private ActionContext resolveContext(UiViewCompositionResolveRequest compositionContext) {
        UiViewCompositionResolveResponse resolved = runtimeService.resolve(compositionContext);
        ResolvedCoordinates claims = new ResolvedCoordinates(resolved.getOwnerType(), resolved.getOwnerId(),
                resolved.getReleaseId(), resolved.getReleaseVersion(), resolved.getCompositionKey(),
                resolved.getSourceEntityCode(), resolved.getSourceRecordId());
        UiConfigRelease ownerRelease = releaseMapper.selectById(
                claims.releaseId());
        if (ownerRelease == null
                || !Objects.equals(claims.ownerType(), ownerRelease.getConfigType())
                || !Objects.equals(claims.ownerId(), ownerRelease.getConfigId())
                || !Objects.equals(claims.releaseVersion(), ownerRelease.getVersion())) {
            throw conflict(
                    "VIEW_COMPOSITION_RELEASE_CONFLICT",
                    "动作上下文对应的宿主发布版本不存在或不一致");
        }
        Map<String, Object> ownerSnapshot =
                releaseService.verifiedReleaseSnapshot(ownerRelease);
        Map<String, Object> composition = mapList(
                ownerSnapshot.get("viewCompositions"))
                .stream()
                .filter(item -> Objects.equals(
                        claims.compositionKey(),
                        text(item.get("compositionKey"))))
                .findFirst()
                .orElseThrow(() -> conflict(
                        "VIEW_COMPOSITION_NOT_PUBLISHED",
                        "关联内容不存在于宿主发布配置"));
        Map<String, Object> config = map(composition.get("config"));
        if (Boolean.FALSE.equals(config.get("enabled"))) {
            throw conflict(
                    "VIEW_COMPOSITION_DISABLED",
                    "关联内容已停用，不能执行动作");
        }
        Map<String, Object> pins = map(config.get("entitySnapshots"));
        EntityPublishedSnapshot sourceSchema = pinnedSchema(
                map(pins.get("source")), "来源实体");
        EntityPublishedSnapshot targetSchema = pinnedSchema(
                map(pins.get("target")), "目标实体");
        EntityDefinition sourceEntity = entityMapper.selectById(
                sourceSchema.getEntityId());
        EntityDefinition targetEntity = entityMapper.selectById(
                targetSchema.getEntityId());
        if (sourceEntity == null || targetEntity == null
                || !Objects.equals(
                resolved.getTargetEntityId(), targetEntity.getId())
                || !Objects.equals(
                claims.sourceEntityCode(), sourceEntity.getEntityCode())) {
            throw conflict(
                    "VIEW_COMPOSITION_ENTITY_SNAPSHOT_CONFLICT",
                    "关联内容固定实体身份与运行时解析结果不一致");
        }
        UiConfigRelease targetRelease = releaseMapper.selectById(
                resolved.getTargetReleaseId());
        if (targetRelease == null
                || !Objects.equals(
                resolved.getTargetContentType(), targetRelease.getConfigType())
                || !Objects.equals(
                resolved.getTargetContentId(), targetRelease.getConfigId())
                || !Objects.equals(
                resolved.getTargetReleaseVersion(), targetRelease.getVersion())) {
            throw conflict(
                    "VIEW_COMPOSITION_TARGET_RELEASE_CONFLICT",
                    "关联内容固定目标发布版本不存在或不一致");
        }
        Map<String, Object> targetSnapshot =
                releaseService.verifiedReleaseSnapshot(targetRelease);
        return new ActionContext(
                claims,
                resolved,
                ownerSnapshot,
                targetSnapshot,
                config,
                sourceEntity,
                targetEntity,
                sourceSchema,
                targetSchema);
    }

    /**
     * 处理固定结构，并将结果传给后续步骤。
     *
     * @param pin 固定，作为 {@code text} 的输入影响后续处理
     * @param label 标签，后续用于处理固定结构时匹配或展示
     * @return 处理后的固定结构结果，供调用方继续处理
     */
    private EntityPublishedSnapshot pinnedSchema(
            Map<String, Object> pin,
            String label) {
        String historyId = text(pin.get("historyId"));
        String entityId = text(pin.get("entityId"));
        String entityCode = text(pin.get("entityCode"));
        Integer version = integer(pin.get("version"));
        String expectedHash = normalizeHash(text(pin.get("schemaHash")));
        if (!StringUtils.hasText(historyId)
                || !StringUtils.hasText(entityId)
                || !StringUtils.hasText(entityCode)
                || version == null || version < 1
                || expectedHash.length() != 64) {
            throw conflict(
                    "VIEW_COMPOSITION_ENTITY_SNAPSHOT_REQUIRED",
                    label + "发布快照身份不完整");
        }
        EntityPublishedSnapshotService.PinnedEntitySnapshot pinned;
        try {
            pinned = entitySnapshotService.getPinnedByHistoryId(historyId);
        } catch (RuntimeException exception) {
            throw conflict(
                    "VIEW_COMPOSITION_ENTITY_SNAPSHOT_MISSING",
                    label + "固定发布历史已缺失");
        }
        EntityPublishedSnapshot schema = pinned == null
                ? null : pinned.snapshot();
        if (schema == null
                || !Objects.equals(entityId, schema.getEntityId())
                || !Objects.equals(entityCode, schema.getEntityCode())
                || !Objects.equals(version, schema.getVersion())
                || !Objects.equals(
                expectedHash,
                normalizeHash(pinned.schemaHash()))) {
            throw conflict(
                    "VIEW_COMPOSITION_ENTITY_SNAPSHOT_CONFLICT",
                    label + "固定发布历史身份或完整性校验失败");
        }
        return schema;
    }

    /**
     * 校验请求；不满足约束时阻止后续处理。
     *
     * @param request 本次请求，后续经校验后用于校验请求
     * @return 校验后的请求结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private ValidatedAction validateRequest(
            UiViewCompositionActionRequest request) {
        if (request == null
                || request.getCompositionContext() == null) {
            throw new IllegalArgumentException("关联内容动作上下文不能为空");
        }
        String action = normalize(request.getAction());
        if (!StringUtils.hasText(action)
                || !action.matches("[A-Z][A-Z0-9_.-]{0,99}")) {
            throw new IllegalArgumentException(
                    "动作标识必须以字母开头，且只能包含字母、数字、点、横线或下划线");
        }
        if (KNOWN_ACTIONS.contains(action) && !"VIEW".equals(action)) {
            throw new IllegalArgumentException(
                    "该标准动作必须通过平台表单或页面权威入口执行");
        }
        if (request.getInput() != null && !request.getInput().isEmpty()) {
            throw new IllegalArgumentException(
                    "当前动作不接受客户端字段补丁或任意输入");
        }
        String operationId = trim(request.getOperationId());
        if (!StringUtils.hasText(operationId)
                || !operationId.matches(
                "[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException(
                    "操作ID必须为 1 到 128 位字母、数字、点、下划线、冒号或横线");
        }
        List<String> ids = request.getTargetRecordIds() == null
                || request.getTargetRecordIds().isEmpty()
                ? List.of()
                : normalizeRecordIds(request.getTargetRecordIds());
        return new ValidatedAction(
                request.getCompositionContext(),
                action,
                ids,
                operationId);
    }

    /**
     * 规范化记录ID 集合；输出作为后续校验或处理的输入。
     *
     * @param raw 待规范化记录ID 集合的原始输入，结果供调用方继续使用
     * @return 界面视图组合动作集合，供调用方遍历或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private List<String> normalizeRecordIds(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("请选择至少一条目标记录");
        }
        if (raw.size() > MAX_TARGET_RECORDS) {
            throw new IllegalArgumentException(
                    "单次关联内容动作最多处理 "
                            + MAX_TARGET_RECORDS + " 条记录");
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String value : raw) {
            String id = trim(value);
            if (!StringUtils.hasText(id) || id.length() > 64) {
                throw new IllegalArgumentException("目标记录ID格式不正确");
            }
            ids.add(id);
        }
        if (ids.size() != raw.size()) {
            throw new IllegalArgumentException("目标记录ID不能重复");
        }
        return List.copyOf(ids);
    }

    /**
     * 校验并获取动态；不满足约束时阻止后续处理。
     *
     * @param entity 实体，供本方法校验并获取动态时使用
     * @param label 标签，后续用于校验并获取动态时匹配或展示
     */
    private void requireDynamic(
            EntityDefinition entity,
            String label) {
        if (entity.getStorageMode() != EntityDefinition.StorageMode.DYNAMIC) {
            throw conflict(
                    "VIEW_COMPOSITION_SYSTEM_ENTITY_WRITE_UNSUPPORTED",
                    label + "为系统实体，不能通过关联内容直接写入");
        }
    }

    /**
     * 校验并获取固定字段；不满足约束时阻止后续处理。
     *
     * @param schema 结构，供本方法校验并获取固定字段时使用
     * @param fieldCode 字段编码，后续用于校验并获取固定字段时定位或关联目标
     * @param label 标签，后续用于校验并获取固定字段时匹配或展示
     * @return 校验并获取后的固定字段结果，供调用方继续处理
     */
    private EntityField requirePinnedField(
            EntityPublishedSnapshot schema,
            String fieldCode,
            String label) {
        EntityField field = schema.getFields() == null
                ? null
                : schema.getFields().stream()
                .filter(item -> Objects.equals(
                        fieldCode, item.getFieldCode()))
                .findFirst()
                .orElse(null);
        if (field == null) {
            throw conflict(
                    "VIEW_COMPOSITION_MAPPING_FIELD_INVALID",
                    label + "实体固定版本不存在字段: " + fieldCode);
        }
        return field;
    }

    /**
     * 生成必填字段文本，供后续匹配或展示。
     *
     * @param value 待处理必填字段的原始输入，结果供调用方继续使用
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param label 标签，后续用于处理必填字段时匹配或展示
     * @return 处理后的必填字段文本，供调用方比较或展示
     */
    private String requiredField(
            Map<String, Object> value,
            String key,
            String label) {
        String result = trim(text(value.get(key)));
        if (!StringUtils.hasText(result)) {
            throw conflict(
                    "VIEW_COMPOSITION_RELATION_INVALID",
                    label + "不能为空");
        }
        return result;
    }

    /**
     * 记录值；供后续追溯或审计使用。
     *
     * @param record 记录，作为 {@code objectMapper.convertValue} 的输入影响后续处理
     * @param fieldCode 字段编码，后续用于记录值时定位或关联目标
     * @return 记录后的值结果，供调用方继续处理
     */
    private Object recordValue(
            EntityDataDTO record,
            String fieldCode) {
        if ("id".equals(fieldCode)) {
            return record.getId();
        }
        if (record.getData() != null
                && record.getData().containsKey(fieldCode)) {
            return record.getData().get(fieldCode);
        }
        Map<String, Object> values = objectMapper.convertValue(
                record, new TypeReference<>() {});
        return values.get(fieldCode);
    }

    /**
     * 判断{@code truthy}条件是否成立，供调用方选择后续分支。
     *
     * @param value 待处理{@code truthy}的原始输入，结果供调用方继续使用
     * @return {@code truthy}条件成立时为 true，否则为 false
     */
    private boolean truthy(Object value) {
        return Boolean.TRUE.equals(value)
                || value instanceof Number number && number.intValue() != 0
                || value instanceof String text
                && ("true".equalsIgnoreCase(text) || "1".equals(text));
    }

    /**
     * 计算输入内容的 SHA-256 摘要，供配置完整性校验或幂等去重使用。
     *
     * @param value 待处理{@code sha256}的原始输入，结果供调用方继续使用
     * @return 处理后的{@code sha256}文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("关联内容幂等摘要生成失败", exception);
        }
    }

    /**
     * 整理映射数据，供调用方遍历或继续处理。
     *
     * @param value 待处理映射的原始输入，结果供调用方继续使用
     * @return 映射键值结果，供调用方继续处理
     */
    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, child) ->
                result.put(String.valueOf(key), child));
        return result;
    }

    /**
     * 整理映射列表数据，供调用方遍历或继续处理。
     *
     * @param value 待处理映射列表的原始输入，结果供调用方继续使用
     * @return 界面视图组合动作集合，供调用方遍历或展示
     */
    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> source)) {
            return List.of();
        }
        return source.stream()
                .map(this::map)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    /**
     * 整理字符串列表数据，供调用方遍历或继续处理。
     *
     * @param value 待处理字符串列表的原始输入，结果供调用方继续使用
     * @return 界面视图组合动作集合，供调用方遍历或展示
     */
    private List<String> stringList(Object value) {
        if (!(value instanceof Collection<?> source)) {
            return List.of();
        }
        return source.stream()
                .map(this::text)
                .map(this::normalize)
                .filter(StringUtils::hasText)
                .toList();
    }

    /**
     * 按候选顺序取首个非空文本，供后续匹配或展示使用。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的首个文本文本，供调用方比较或展示
     */
    private String firstText(Object... values) {
        for (Object value : values) {
            String candidate = trim(text(value));
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 规范化输入值，确保后续比较和持久化使用一致格式。
     *
     * @param value 待规范化界面视图组合动作的原始输入，结果供调用方继续使用
     * @return 规范化后的界面视图组合动作文本，供调用方比较或展示
     */
    private String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    /**
     * 规范化哈希；输出作为后续校验或处理的输入。
     *
     * @param value 待规范化哈希的原始输入，结果供调用方继续使用
     * @return 规范化后的哈希文本，供调用方比较或展示
     */
    private String normalizeHash(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    /**
     * 清理界面视图组合动作；后续读取或执行将使用更新后的状态。
     *
     * @param value 待清理界面视图组合动作的原始输入，结果供调用方继续使用
     * @return 清理后的界面视图组合动作文本，供调用方比较或展示
     */
    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
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
     * 将输入解析为整数，供后续范围校验或计算使用。
     *
     * @param value 待处理整数的原始输入，结果供调用方继续使用
     * @return 处理后的整数结果，供调用方继续处理
     */
    private Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 构造业务冲突异常，供调用方刷新或重试。
     *
     * @param code 编码，后续用于处理冲突时定位或关联目标
     * @param message 消息，作为 {@code BusinessConflictException} 的输入影响后续处理
     * @return 处理后的冲突结果，供调用方继续处理
     */
    private BusinessConflictException conflict(
            String code,
            String message) {
        return new BusinessConflictException(code, message);
    }

    /**
     * 构造权限不足异常，供调用方停止当前操作。
     *
     * @param code 编码，后续用于处理禁止时定位或关联目标
     * @param message 消息，作为 {@code BusinessForbiddenException} 的输入影响后续处理
     * @return 处理后的禁止结果，供调用方继续处理
     */
    private BusinessForbiddenException forbidden(
            String code,
            String message) {
        return new BusinessForbiddenException(code, message);
    }

    /**
     * 封装动作上下文的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param claims 声明集合，保存在对象中供后续校验、查询或展示
     * @param resolved 已解析，保存在对象中供后续校验、查询或展示
     * @param ownerSnapshot 归属方快照，保存在对象中供后续校验、查询或展示
     * @param targetSnapshot 目标快照，保存在对象中供后续校验、查询或展示
     * @param config 配置内容，决定后续动作上下文的处理规则
     * @param sourceEntity 来源实体，保存在对象中供后续校验、查询或展示
     * @param targetEntity 目标实体，保存在对象中供后续校验、查询或展示
     * @param sourceSchema 来源结构，保存在对象中供后续校验、查询或展示
     * @param targetSchema 目标结构，保存在对象中供后续校验、查询或展示
     */
    private record ActionContext(
            ResolvedCoordinates claims,
            UiViewCompositionResolveResponse resolved,
            Map<String, Object> ownerSnapshot,
            Map<String, Object> targetSnapshot,
            Map<String, Object> config,
            EntityDefinition sourceEntity,
            EntityDefinition targetEntity,
            EntityPublishedSnapshot sourceSchema,
            EntityPublishedSnapshot targetSchema) {
    }

    /**
     * 封装已校验动作的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param compositionContext 业务入口和关联路径，服务端重新校验后才能执行动作
     * @param action 动作标识，决定后续已校验动作采用的处理分支
     * @param targetRecordIds 目标记录ID 集合，保存在对象中供后续校验、查询或展示
     * @param operationId 操作ID，后续用于处理已校验动作时定位或关联目标
     */
    private record ValidatedAction(
            UiViewCompositionResolveRequest compositionContext,
            String action,
            List<String> targetRecordIds,
            String operationId) {
    }

    /**
     * 封装锁定键的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     */
    private record LockKey(String entityCode, String recordId) {
    }
}
