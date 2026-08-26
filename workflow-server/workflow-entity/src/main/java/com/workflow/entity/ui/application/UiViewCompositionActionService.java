package com.workflow.entity.ui.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditRiskLevel;
import com.workflow.contracts.audit.SystemAudit;
import com.workflow.contracts.entity.mutation.EntityMutationBatchCommand;
import com.workflow.contracts.entity.mutation.EntityMutationBatchResult;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationContext;
import com.workflow.contracts.entity.mutation.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.EntityMutationPort;
import com.workflow.contracts.entity.mutation.EntityMutationResult;
import com.workflow.contracts.entity.mutation.EntityMutationSourceType;
import com.workflow.contracts.ui.UiActionCommandPlan;
import com.workflow.contracts.ui.UiActionMutationCommand;
import com.workflow.contracts.ui.UiDataSourceUsages;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.core.result.PageResult;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityAggregateWriter;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.application.SystemEntityReadService;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityPermissionAction;
import com.workflow.entity.ui.api.request.UiViewCompositionActionCapabilitiesRequest;
import com.workflow.entity.ui.api.request.UiViewCompositionActionRequest;
import com.workflow.entity.ui.api.request.UiViewCompositionLinkCandidatesRequest;
import com.workflow.entity.ui.api.request.UiViewCompositionResolveRequest;
import com.workflow.entity.ui.api.request.UiDataSourceExecuteRequest;
import com.workflow.entity.ui.api.response.UiViewCompositionActionCapabilitiesResponse;
import com.workflow.entity.ui.api.response.UiViewCompositionActionCapabilityDTO;
import com.workflow.entity.ui.api.response.UiViewCompositionActionResponse;
import com.workflow.entity.ui.api.response.UiViewCompositionChangedReferenceDTO;
import com.workflow.entity.ui.api.response.UiViewCompositionLinkCandidatesResponse;
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
 * <p>动作上下文由短期签名令牌恢复，随后重新执行宿主、目标发布、实体和数据
 * 权限校验。SELECT 的字段映射、LINK/UNLINK 的关系字段均只读取不可变发布
 * 快照；客户端行对象、实体编码、字段名和筛选条件不会进入权威执行链。</p>
 */
@Service
@RequiredArgsConstructor
public class UiViewCompositionActionService {

    private static final String FORM = "FORM";
    private static final String LIST = "LIST";
    private static final Set<String> KNOWN_ACTIONS = Set.of(
            "VIEW", "SELECT", "CREATE", "EDIT", "LINK", "UNLINK",
            "SAVE_WITH_FORM");
    private static final Set<String> EXECUTABLE_ACTIONS = Set.of(
            "SELECT", "LINK", "UNLINK");
    private static final Set<String> MUTABLE_RELATIONS = Set.of(
            "REFERENCE_FIELD", "REVERSE_REFERENCE", "ENTITY_RELATION");
    private static final int MAX_TARGET_RECORDS = 200;
    private static final int MAX_ACTION_MUTATIONS = 100;

    private final UiViewCompositionRuntimeService runtimeService;
    private final UiViewCompositionTokenService tokenService;
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
    private final UiDataSourceService dataSourceService;
    private final ObjectMapper objectMapper;

    /**
     * 计算当前签名上下文下的权威动作能力。
     *
     * <p>该结果同时考虑发布声明、关系类型、已固定字段以及当前用户标准权限。
     * CREATE/EDIT 仅在精确发布表单提交桥可用时开放；SELECT/LINK/UNLINK 则要求
     * 已发布映射、候选范围和目标记录权限全部通过，任何校验失败都会关闭动作。</p>
     */
    @Transactional(readOnly = true)
    public UiViewCompositionActionCapabilitiesResponse capabilities(
            UiViewCompositionActionCapabilitiesRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getActionContextToken())) {
            throw new IllegalArgumentException("关联内容动作上下文不能为空");
        }
        ActionContext context = resolveContext(
                request.getActionContextToken());
        return UiViewCompositionActionCapabilitiesResponse.builder()
                .sourceRecordId(context.claims().sourceRecordId())
                .actionCapabilities(capabilities(context))
                .build();
    }

    /**
     * 签发“选择候选记录建立关联”的专用目标列表上下文。
     *
     * <p>候选目标、列表精确发布版本和关系条件均从宿主发布快照恢复。普通关联
     * 列表的“当前已关联”条件不会被复用：反向引用和普通实体关系只查询引用字段
     * 为空的记录；单值正向引用可排除当前引用。候选令牌仍由列表运行时叠加目标
     * 列表固定条件和当前用户数据范围。</p>
     */
    @Transactional(readOnly = true)
    public UiViewCompositionLinkCandidatesResponse linkCandidates(
            UiViewCompositionLinkCandidatesRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getActionContextToken())) {
            throw new IllegalArgumentException("关联内容动作上下文不能为空");
        }
        String action = normalize(request.getAction());
        if (!Set.of("LINK", "SELECT").contains(action)) {
            throw new IllegalArgumentException(
                    "关联候选只支持建立关联或选择后建立关联");
        }
        ActionContext context = resolveContext(
                request.getActionContextToken().trim());
        if (!LIST.equals(context.resolved().getTargetContentType())) {
            throw conflict(
                    "VIEW_COMPOSITION_LINK_CANDIDATE_LIST_REQUIRED",
                    "选择候选记录建立关联只适用于目标列表");
        }
        Map<String, UiViewCompositionActionCapabilityDTO> capabilities =
                capabilities(context);
        requireAvailable(capabilities, "LINK");
        if ("SELECT".equals(action)) {
            requireAvailable(capabilities, "SELECT");
            String result = normalize(text(map(
                    map(context.config().get("actionSettings"))
                            .get("select")).get("result")));
            if (!"LINK".equals(result)) {
                throw conflict(
                        "VIEW_COMPOSITION_SELECT_RESULT_INVALID",
                        "当前选择动作不是建立关联");
            }
        }

        CandidatePlan plan = candidatePlan(context);
        UiViewCompositionResolveResponse target = context.resolved();
        String token = tokenService.issueCandidateList(
                context.claims().ownerType(),
                context.claims().ownerId(),
                context.claims().releaseId(),
                context.claims().releaseVersion(),
                context.claims().compositionKey(),
                context.sourceEntity().getEntityCode(),
                context.claims().sourceRecordId(),
                context.targetEntity().getEntityCode(),
                target.getTargetContentId(),
                target.getTargetReleaseId(),
                target.getTargetReleaseVersion(),
                plan.filters(),
                plan.matchNone());
        return UiViewCompositionLinkCandidatesResponse.builder()
                .action(action)
                .targetEntityCode(target.getTargetEntityCode())
                .targetContentId(target.getTargetContentId())
                .targetContentKey(target.getTargetContentKey())
                .targetReleaseId(target.getTargetReleaseId())
                .targetReleaseVersion(target.getTargetReleaseVersion())
                .candidateListContextToken(token)
                .matchNone(plan.matchNone())
                .build();
    }

    /**
     * 校验关联内容打开的目标表单提交，并返回服务端必须覆盖的新增初值。
     *
     * <p>普通表单提交不会调用本方法。携带关联内容动作令牌时，目标实体、表单、
     * 精确发布版本以及 EDIT 记录 ID 均从不可变宿主快照重新解析；来源行和目标行
     * 会先加锁再复核，避免关系在校验与写入之间变化。CREATE 的初值同样在锁后
     * 从来源记录重新计算，浏览器提交的同名字段不能覆盖它们。</p>
     *
     * @return CREATE 时必须合并到目标数据的可信初值；EDIT 时为空
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> authorizeFormSubmission(
            String actionContextToken,
            String action,
            String targetEntityCode,
            String targetRecordId,
            String targetFormId,
            String targetReleaseId,
            Integer targetReleaseVersion,
            String targetReleaseResolutionToken) {
        if (!StringUtils.hasText(actionContextToken)) {
            throw new IllegalArgumentException("关联内容动作上下文不能为空");
        }
        String normalizedAction = normalize(action);
        if (!Set.of("CREATE", "EDIT").contains(normalizedAction)) {
            throw new IllegalArgumentException(
                    "关联内容表单提交只支持新增或编辑");
        }

        ActionContext initial = resolveContext(actionContextToken.trim());
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
        ActionContext context = resolveContext(actionContextToken.trim());
        Map<String, UiViewCompositionActionCapabilityDTO> actionCapabilities =
                capabilities(context);
        requireAvailable(actionCapabilities, normalizedAction);
        requireExactTargetForm(
                context,
                normalizedAction,
                targetEntityCode,
                targetRecordId,
                targetFormId,
                targetReleaseId,
                targetReleaseVersion,
                targetReleaseResolutionToken);
        return "CREATE".equals(normalizedAction)
                ? actionCapabilities.get("CREATE").getInitialValues()
                : Map.of();
    }

    /**
     * 执行已发布动作。写动作使用实体变更管道的原子批次与持久化幂等回执。
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
                action.actionContextToken());
        Map<String, UiViewCompositionActionCapabilityDTO> capabilities =
                capabilities(context);
        requireAvailable(capabilities, action.action());
        Map<String, Object> standardActionResult =
                EXECUTABLE_ACTIONS.contains(action.action())
                        ? executeStandardActionRead(
                        action, context, optionalActionService(
                                context, action.action()))
                        : Map.of();

        if ("SELECT".equals(action.action())) {
            return executeSelect(
                    action, context, capabilities, standardActionResult);
        }
        if (Set.of("LINK", "UNLINK").contains(action.action())) {
            return executeRelationMutation(
                    action,
                    context,
                    "LINK".equals(action.action()),
                    "UNLINK".equals(action.action()),
                    action.action(),
                    capabilities,
                    standardActionResult);
        }
        return executeInterfaceAction(
                action,
                context,
                requireActionService(context, action.action()),
                capabilities);
    }

    private UiViewCompositionActionResponse executeSelect(
            ValidatedAction action,
            ActionContext context,
            Map<String, UiViewCompositionActionCapabilityDTO> capabilities,
            Map<String, Object> actionResult) {
        Map<String, Object> select = map(
                map(context.config().get("actionSettings")).get("select"));
        String mode = normalize(text(select.get("mode")));
        requireSelectionCount(action.targetRecordIds(), mode);
        String result = normalize(text(select.get("result")));
        if ("LINK".equals(result)) {
            requireAvailable(capabilities, "LINK");
            return executeRelationMutation(
                    action, context, true, false, "SELECT", capabilities,
                    actionResult);
        }
        if (StringUtils.hasText(action.candidateListContextToken())) {
            throw new IllegalArgumentException(
                    "普通选择回填不能携带建立关联候选令牌");
        }
        if (!"FILL_FIELDS".equals(result)) {
            throw conflict(
                    "VIEW_COMPOSITION_SELECT_RESULT_INVALID",
                    "已发布配置包含不支持的选择结果处理方式");
        }
        List<EntityDataDTO> records = readTargets(
                context, action.targetRecordIds(), true);
        Map<String, Object> patch = buildSourcePatch(
                context, select, records.get(0));
        return UiViewCompositionActionResponse.builder()
                .operationId(action.operationId())
                .action("SELECT")
                .sourceRecordId(context.claims().sourceRecordId())
                .sourcePatch(Collections.unmodifiableMap(patch))
                .changedReferences(List.of())
                .actionCapabilities(capabilities)
                .actionResult(actionResult)
                .replayed(false)
                .build();
    }

    private UiViewCompositionActionResponse executeRelationMutation(
            ValidatedAction action,
            ActionContext context,
            boolean link,
            boolean requireResolvedMembership,
            String responseAction,
            Map<String, UiViewCompositionActionCapabilityDTO> capabilities,
            Map<String, Object> actionResult) {
        requireAvailable(capabilities, link ? "LINK" : "UNLINK");
        String relationType = normalize(text(
                context.relation().get("type")));
        if (!MUTABLE_RELATIONS.contains(relationType)) {
            throw conflict(
                    "VIEW_COMPOSITION_RELATION_ACTION_UNSUPPORTED",
                    "当前数据关联方式不支持建立或解除关系");
        }
        UiViewCompositionTokenService.Claims candidateContext = link
                ? requireCandidateContext(action, context)
                : requireNoCandidateContext(action);
        UiViewCompositionActionReceiptService.AcquireResult receipt =
                actionReceiptService.acquire(
                        context.claims().ownerType(),
                        context.claims().ownerId(),
                        context.claims().releaseId(),
                        context.claims().releaseVersion(),
                        context.claims().compositionKey(),
                        context.sourceEntity().getEntityCode(),
                        context.claims().sourceRecordId(),
                        responseAction,
                        action.targetRecordIds(),
                        action.operationId());
        if (receipt.replayedResponse() != null) {
            return receipt.replayedResponse();
        }

        List<EntityDataDTO> initialTargets = link
                ? readCandidateTargets(
                        context,
                        action.targetRecordIds(),
                        candidateContext)
                : readTargets(
                        context,
                        action.targetRecordIds(),
                        requireResolvedMembership);
        EntityDataDTO initialSource = readAccessible(
                context.sourceEntity(),
                context.claims().sourceRecordId(),
                null);
        lockMutationRows(context, initialSource, initialTargets);

        // 数据权限检查必须在锁后再执行一次。这样权限范围或关联值在等待锁期间
        // 发生变化时会整体失败，而不会依据锁前的陈旧行构造写命令。
        EntityDataDTO source = readAccessible(
                context.sourceEntity(),
                context.claims().sourceRecordId(),
                null);
        List<EntityDataDTO> targets = link
                ? readCandidateTargets(
                        context,
                        action.targetRecordIds(),
                        candidateContext)
                : readTargets(
                        context,
                        action.targetRecordIds(),
                        requireResolvedMembership);
        MutationPlan plan = mutationPlan(
                context,
                source,
                targets,
                link,
                action.operationId(),
                receipt.receiptCommand().context().idempotencyKey());
        EntityMutationBatchResult batch = mutationPort.executeBatch(
                new EntityMutationBatchCommand(
                        action.operationId(), plan.commands(), true));
        Map<String, EntityMutationResult> resultsByOperation =
                new LinkedHashMap<>();
        for (EntityMutationResult result : batch.results()) {
            resultsByOperation.put(result.operationId(), result);
        }
        List<EntityMutationResult> primaryResults = plan.commands().stream()
                .map(command -> {
                    EntityMutationResult result = resultsByOperation.get(
                            command.operationId());
                    if (result == null) {
                        throw new IllegalStateException(
                                "关联内容实体变更缺少主命令结果: "
                                        + command.operationId());
                    }
                    return result;
                })
                .toList();
        List<UiViewCompositionChangedReferenceDTO> changed =
                new ArrayList<>();
        for (int index = 0; index < plan.targetRecordIds().size(); index++) {
            EntityMutationResult result = primaryResults.get(index);
            changed.add(UiViewCompositionChangedReferenceDTO.builder()
                    .sourceRecordId(context.claims().sourceRecordId())
                    .targetRecordId(plan.targetRecordIds().get(index))
                    .relationType(relationType)
                    .linked(link)
                    .changed(result.changed())
                    .replayed(result.replayed())
                    .build());
        }
        boolean replayed = !primaryResults.isEmpty()
                && primaryResults.stream()
                .allMatch(EntityMutationResult::replayed);
        Map<String, Object> sourcePatch = relationSourcePatch(
                context, plan, link);
        UiViewCompositionActionResponse response =
                UiViewCompositionActionResponse.builder()
                .operationId(action.operationId())
                .action(responseAction)
                .sourceRecordId(context.claims().sourceRecordId())
                .sourcePatch(sourcePatch)
                .changedReferences(List.copyOf(changed))
                .actionCapabilities(capabilities)
                .actionResult(actionResult)
                .replayed(replayed)
                .build();
        actionReceiptService.complete(
                receipt.receiptCommand(), response);
        return response;
    }

    /**
     * 执行宿主发布快照中显式钉定的接口动作。
     *
     * <p>客户端只提交 actionKey、记录 ID 和 operationId。服务、操作、映射与
     * 可执行快照都从签名宿主版本恢复；READ 只返回映射后的界面结果，WRITE
     * 则先取得持久批次回执，再通过受控计划和 EntityMutationPort 原子执行。</p>
     */
    private UiViewCompositionActionResponse executeInterfaceAction(
            ValidatedAction action,
            ActionContext context,
            Map<String, Object> binding,
            Map<String, UiViewCompositionActionCapabilityDTO> capabilities) {
        UiDataSourceService.ActionOperationDescriptor descriptor =
                validateActionBinding(context, binding);
        EntityDataDTO source = readAccessible(
                context.sourceEntity(),
                context.claims().sourceRecordId(),
                null);
        List<EntityDataDTO> targets = action.targetRecordIds().isEmpty()
                ? List.of()
                : readTargets(context, action.targetRecordIds(), true);
        Map<String, Object> input = actionInput(
                context, binding, source, targets);
        UiDataSourceExecuteRequest executeRequest = actionExecuteRequest(
                action, context, binding, input);

        if ("READ".equals(descriptor.operationKind())) {
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
        context = resolveContext(action.actionContextToken());
        binding = requireActionService(context, action.action());
        validateActionBinding(context, binding);
        source = readAccessible(
                context.sourceEntity(),
                context.claims().sourceRecordId(),
                null);
        targets = action.targetRecordIds().isEmpty()
                ? List.of()
                : readTargets(context, action.targetRecordIds(), true);
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
     * 标准选择/关系动作始终由平台权威链执行；同名 READ 绑定只作为前置校验、
     * 计算或界面结果补充，失败会使整个事务终止，绝不能替代关系写入。
     */
    private Map<String, Object> executeStandardActionRead(
            ValidatedAction action,
            ActionContext context,
            Map<String, Object> binding) {
        if (binding.isEmpty()) {
            return Map.of();
        }
        UiDataSourceService.ActionOperationDescriptor descriptor =
                validateActionBinding(context, binding);
        if (!"READ".equals(descriptor.operationKind())) {
            throw forbidden(
                    "VIEW_COMPOSITION_STANDARD_ACTION_WRITE_FORBIDDEN",
                    "标准选择、建立关联和解除关联必须由平台权威链执行；同名接口只能用于只读校验、计算或返回界面结果");
        }
        List<EntityDataDTO> targets;
        boolean candidateSelection = "LINK".equals(action.action())
                || "SELECT".equals(action.action())
                && "LINK".equals(normalize(text(map(
                map(context.config().get("actionSettings"))
                        .get("select")).get("result"))));
        if (candidateSelection) {
            UiViewCompositionTokenService.Claims candidate =
                    requireCandidateContext(action, context);
            targets = readCandidateTargets(
                    context, action.targetRecordIds(), candidate);
        } else {
            targets = readTargets(
                    context, action.targetRecordIds(), true);
        }
        EntityDataDTO source = readAccessible(
                context.sourceEntity(),
                context.claims().sourceRecordId(),
                null);
        Object raw = dataSourceService.executePinnedOperation(
                requiredField(
                        binding,
                        "executableSnapshot",
                        "动作接口操作快照"),
                requiredField(
                        binding,
                        "definitionHash",
                        "动作接口操作哈希"),
                actionExecuteRequest(
                        action,
                        context,
                        binding,
                        actionInput(context, binding, source, targets)));
        return mapActionOutput(binding, raw);
    }

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

    private UiDataSourceExecuteRequest actionExecuteRequest(
            ValidatedAction action,
            ActionContext context,
            Map<String, Object> binding,
            Map<String, Object> input) {
        UiDataSourceExecuteRequest request = new UiDataSourceExecuteRequest();
        request.setUsage(UiDataSourceUsages.RELATED_CONTENT_ACTION);
        request.setOperationCode(requiredField(
                binding, "operationCode", "接口操作"));
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

    private UiDataSourceService.ActionOperationDescriptor
            validateActionBinding(
            ActionContext context,
            Map<String, Object> binding) {
        return dataSourceService.validatePinnedActionOperation(
                requiredField(
                        binding,
                        "executableSnapshot",
                        "动作接口操作快照"),
                requiredField(
                        binding,
                        "definitionHash",
                        "动作接口操作哈希"),
                requiredField(binding, "serviceId", "接口服务"),
                requiredField(binding, "sourceCode", "接口服务编码"),
                integer(binding.get("serviceRevision")),
                requiredField(binding, "operationCode", "接口操作"),
                context.claims().ownerType());
    }

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

    private Map<String, Object> optionalActionService(
            ActionContext context,
            String actionKey) {
        return actionServices(context).stream()
                .filter(item -> normalize(text(item.get("actionKey")))
                        .equals(normalize(actionKey)))
                .findFirst()
                .orElse(Map.of());
    }

    private List<Map<String, Object>> actionServices(
            ActionContext context) {
        return mapList(map(context.config().get("specialHandling"))
                .get("actionServices"));
    }

    /** 只按发布映射读取来源或已授权目标字段，不传递整行 data。 */
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
     * 实体字段校验和 mutation policy，Provider 无法跳过任一层。</p>
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
            // Provider 只能处理本次动作令牌已解析并由平台重读授权的目标集合。
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
                        "ownerReleaseVersion", context.claims().releaseVersion(),
                        "maxExpandedCommands", MAX_ACTION_MUTATIONS))
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
     * 返回关系写入后必须同步到宿主表单模型的权威字段值。
     *
     * <p>REFERENCE_FIELD 的实际写入发生在来源记录上。若只刷新关联内容而不
     * 回填宿主模型，用户随后保存旧表单值会把刚建立或解除的关系反向覆盖。
     * 其它当前支持的关系写在目标记录上，来源表单没有对应字段，因此不返回
     * 浏览器可猜测的多值补丁。</p>
     */
    private Map<String, Object> relationSourcePatch(
            ActionContext context,
            MutationPlan plan,
            boolean link) {
        if (!"REFERENCE_FIELD".equals(normalize(text(
                context.relation().get("type"))))) {
            return Map.of();
        }
        String sourceField = requiredField(
                context.relation(), "sourceField", "来源引用字段");
        String linkedId = link && !plan.targetRecordIds().isEmpty()
                ? plan.targetRecordIds().get(0)
                : null;
        return Collections.unmodifiableMap(
                singletonNullable(sourceField, linkedId));
    }

    private MutationPlan mutationPlan(
            ActionContext context,
            EntityDataDTO source,
            List<EntityDataDTO> targets,
            boolean link,
            String operationId,
            String batchIdempotencyKey) {
        String type = normalize(text(context.relation().get("type")));
        List<EntityMutationCommand> commands = new ArrayList<>();
        List<String> targetIds = targets.stream()
                .map(EntityDataDTO::getId)
                .toList();
        if ("REFERENCE_FIELD".equals(type)) {
            if (targets.size() != 1) {
                throw new IllegalArgumentException(
                        "单值引用字段每次只能建立或解除一条关系");
            }
            requireDynamic(context.sourceEntity(), "来源实体");
            capabilityService.requireStandardPermission(
                    context.sourceEntity().getEntityCode(),
                    EntityPermissionAction.UPDATE);
            String fieldCode = requiredField(
                    context.relation(), "sourceField", "来源引用字段");
            EntityField field = requirePinnedField(
                    context.sourceSchema(), fieldCode, "来源");
            requireReference(field, context.targetEntity().getId(), "来源");
            String targetId = targets.get(0).getId();
            requireRelationshipState(
                    scalar(recordValue(source, fieldCode), fieldCode),
                    targetId,
                    link);
            commands.add(updateCommand(
                    context,
                    operationId,
                    0,
                    context.sourceEntity().getEntityCode(),
                    source.getId(),
                    fieldCode,
                    link ? targetId : null,
                    batchIdempotencyKey));
            return new MutationPlan(List.copyOf(commands), targetIds);
        }

        requireDynamic(context.targetEntity(), "目标实体");
        capabilityService.requireStandardPermission(
                context.targetEntity().getEntityCode(),
                EntityPermissionAction.UPDATE);
        String fieldCode;
        if ("REVERSE_REFERENCE".equals(type)) {
            fieldCode = requiredField(
                    context.relation(), "targetField", "目标引用字段");
            EntityField field = requirePinnedField(
                    context.targetSchema(), fieldCode, "目标");
            requireReference(field, context.sourceEntity().getId(), "目标");
        } else {
            EntityRelation relation = requireAssociationRelation(context);
            fieldCode = relation.getChildRefFieldCode();
            EntityField field = requirePinnedField(
                    context.targetSchema(), fieldCode, "目标");
            requireReference(field, context.sourceEntity().getId(), "目标");
        }
        for (int index = 0; index < targets.size(); index++) {
            EntityDataDTO target = targets.get(index);
            requireRelationshipState(
                    scalar(recordValue(target, fieldCode), fieldCode),
                    source.getId(),
                    link);
            commands.add(updateCommand(
                    context,
                    operationId,
                    index,
                    context.targetEntity().getEntityCode(),
                    target.getId(),
                    fieldCode,
                    link ? source.getId() : null,
                    batchIdempotencyKey));
        }
        return new MutationPlan(List.copyOf(commands), targetIds);
    }

    /**
     * 组成型关系必须随父聚合提交。这里仅允许普通关联，避免 UNLINK 被错误
     * 实现成删除子记录，或绕开既有子项归属校验。
     */
    private EntityRelation requireAssociationRelation(
            ActionContext context) {
        String relationCode = requiredField(
                context.relation(), "relationCode", "实体关系编码");
        EntityRelation definition = context.sourceSchema().getRelations() == null
                ? null
                : context.sourceSchema().getRelations().stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                .filter(item -> Objects.equals(
                        relationCode, item.getRelationCode()))
                .findFirst()
                .orElse(null);
        if (definition == null
                || !Objects.equals(
                context.targetEntity().getId(),
                definition.getChildEntityId())
                || !StringUtils.hasText(definition.getChildRefFieldCode())) {
            throw conflict(
                    "VIEW_COMPOSITION_RELATION_INVALID",
                    "已固定实体版本中不存在该关系");
        }
        if (definition.getOwnershipType()
                != EntityRelation.OwnershipType.ASSOCIATION) {
            throw conflict(
                    "VIEW_COMPOSITION_COMPOSITION_ACTION_REJECTED",
                    "组成型关系只能随父表单统一保存，不能独立建立或解除");
        }
        return definition;
    }

    private EntityMutationCommand updateCommand(
            ActionContext context,
            String operationId,
            int index,
            String entityCode,
            String recordId,
            String fieldCode,
            Object value,
            String batchIdempotencyKey) {
        String commandId = operationId + ":" + index;
        // 与接口动作相同，标准关系写的单条回执也必须继承批次级主体隔离。
        String idempotencyKey = "uivc:" + sha256(String.join("|",
                batchIdempotencyKey,
                "relation",
                String.valueOf(index),
                entityCode,
                recordId,
                fieldCode));
        EntityMutationSourceType sourceType = FORM.equals(
                context.claims().ownerType())
                ? EntityMutationSourceType.FORM
                : EntityMutationSourceType.LIST;
        EntityMutationContext mutationContext = EntityMutationContext.builder(
                        sourceType,
                        "VIEW_COMPOSITION_RELATION",
                        "关联内容建立或解除关系")
                .sourceId(context.claims().ownerId())
                .sourceRecord(
                        context.sourceEntity().getEntityCode(),
                        context.claims().sourceRecordId())
                .trace(operationId, idempotencyKey)
                .extraParams(Map.of(
                        "compositionKey", context.claims().compositionKey(),
                        "ownerReleaseId", context.claims().releaseId(),
                        "ownerReleaseVersion", context.claims().releaseVersion()))
                .build();
        return new EntityMutationCommand(
                commandId,
                entityCode,
                recordId,
                EntityMutationOperationType.UPDATE,
                Map.of("data", singletonNullable(fieldCode, value)),
                mutationContext);
    }

    private Map<String, Object> singletonNullable(
            String key,
            Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(key, value);
        return result;
    }

    private void requireRelationshipState(
            Object current,
            String relatedRecordId,
            boolean link) {
        String currentId = current == null ? null : String.valueOf(current);
        if (link && StringUtils.hasText(currentId)
                && !Objects.equals(currentId, relatedRecordId)) {
            throw conflict(
                    "VIEW_COMPOSITION_RELATION_ALREADY_LINKED",
                    "记录已关联其他数据，请先解除原关系");
        }
        if (!link && StringUtils.hasText(currentId)
                && !Objects.equals(currentId, relatedRecordId)) {
            throw conflict(
                    "VIEW_COMPOSITION_RELATION_MISMATCH",
                    "目标记录不属于当前关系，不能解除");
        }
        // currentId 为空时仍进入实体幂等管道。这样首次无操作和同 operationId
        // 重放都具有一致结果，且不会为了识别重放而新增进程内状态。
    }

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

    private List<EntityDataDTO> readTargets(
            ActionContext context,
            List<String> ids,
            boolean requireResolvedMembership) {
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
            if (requireResolvedMembership) {
                record = requireResolvedMembership(context, id);
            }
            result.add(record);
        }
        return List.copyOf(result);
    }

    private UiViewCompositionTokenService.Claims requireCandidateContext(
            ValidatedAction action,
            ActionContext context) {
        if (!StringUtils.hasText(action.candidateListContextToken())) {
            throw forbidden(
                    "VIEW_COMPOSITION_LINK_CANDIDATE_CONTEXT_REQUIRED",
                    "建立关联必须先从服务端候选列表选择记录");
        }
        UiViewCompositionTokenService.Claims candidate =
                tokenService.verifyCandidateList(
                        action.candidateListContextToken());
        UiViewCompositionResolveResponse target = context.resolved();
        if (!Objects.equals(candidate.ownerType(), context.claims().ownerType())
                || !Objects.equals(candidate.ownerId(), context.claims().ownerId())
                || !Objects.equals(candidate.releaseId(), context.claims().releaseId())
                || !Objects.equals(candidate.releaseVersion(), context.claims().releaseVersion())
                || !Objects.equals(candidate.compositionKey(), context.claims().compositionKey())
                || !Objects.equals(candidate.sourceEntityCode(), context.sourceEntity().getEntityCode())
                || !Objects.equals(candidate.sourceRecordId(), context.claims().sourceRecordId())
                || !Objects.equals(candidate.targetEntityCode(), target.getTargetEntityCode())
                || !Objects.equals(candidate.targetContentId(), target.getTargetContentId())
                || !Objects.equals(candidate.targetReleaseId(), target.getTargetReleaseId())
                || !Objects.equals(candidate.targetReleaseVersion(), target.getTargetReleaseVersion())) {
            throw forbidden(
                    "VIEW_COMPOSITION_LINK_CANDIDATE_CONTEXT_MISMATCH",
                    "候选列表与当前宿主、关联内容或固定目标版本不一致");
        }
        // 此处只校验候选令牌的用途和固定身份。候选关系范围会在首次执行的
        // 锁前、锁后各复核一次；不能在批次回执之前复核，因为单值 LINK 成功
        // 后来源引用必然变化，完全相同的幂等重放仍应恢复整批历史结果。
        return candidate;
    }

    private UiViewCompositionTokenService.Claims requireNoCandidateContext(
            ValidatedAction action) {
        if (StringUtils.hasText(action.candidateListContextToken())) {
            throw new IllegalArgumentException(
                    "解除关联不能携带建立关联候选令牌");
        }
        return null;
    }

    /**
     * 锁前和锁后都重新计算候选计划。来源引用或固定关系在等待锁期间变化时，旧
     * 候选令牌立即失效，调用方必须重新打开候选列表，不能依据陈旧范围写入。
     */
    private void requireCandidateScopeStillCurrent(
            ActionContext context,
            UiViewCompositionTokenService.Claims candidate) {
        CandidatePlan expected = candidatePlan(context);
        if (candidate.matchNone() != expected.matchNone()
                || !Objects.equals(
                candidate.fixedFilters(), expected.filters())) {
            throw conflict(
                    "VIEW_COMPOSITION_LINK_CANDIDATE_STALE",
                    "关联关系已变化，请重新打开候选列表后再选择");
        }
    }

    private List<EntityDataDTO> readCandidateTargets(
            ActionContext context,
            List<String> ids,
            UiViewCompositionTokenService.Claims candidate) {
        requireCandidateScopeStillCurrent(context, candidate);
        if (candidate.matchNone()) {
            throw conflict(
                    "VIEW_COMPOSITION_LINK_CANDIDATE_EMPTY",
                    "当前固定条件下没有可建立关联的候选记录");
        }
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
                    context.resolved().getTargetContentKey());
            requireCandidateMembership(
                    context, candidate, record, id);
            result.add(record);
        }
        return List.copyOf(result);
    }

    private void requireCandidateMembership(
            ActionContext context,
            UiViewCompositionTokenService.Claims candidate,
            EntityDataDTO record,
            String recordId) {
        if (record == null
                || !Objects.equals(recordId, record.getId())
                || !matchesFilters(record, candidate.fixedFilters())) {
            throw forbidden(
                    "VIEW_COMPOSITION_LINK_CANDIDATE_OUT_OF_SCOPE",
                    "所选记录不属于当前关联候选范围");
        }
        Map<String, Object> condition = new LinkedHashMap<>(
                targetListFixedFilters(context));
        // 固定列表若约束 id，先对已按数据范围读取的记录执行同等受控判断，再
        // 改为精确 id 查询；否则简单筛选文档无法同时表达两个 id 条件。
        if (hasFilterBase(condition, "id")) {
            Map<String, Object> idFilter = filterForBase(condition, "id");
            if (!matchesFilters(record, idFilter)) {
                throw forbidden(
                        "VIEW_COMPOSITION_LINK_CANDIDATE_OUT_OF_SCOPE",
                        "所选记录不满足目标列表固定条件");
            }
            removeFilterBase(condition, "id");
        }
        candidate.fixedFilters().forEach((key, value) -> {
            String base = stripFilterSuffix(key);
            if (!"id".equals(base)) {
                condition.put(key, value);
            }
        });
        condition.put("id", recordId);
        condition.put("id_op", "EQ");
        PageResult<EntityDataDTO> page =
                context.targetEntity().getStorageMode()
                        == EntityDefinition.StorageMode.SYSTEM
                        ? systemEntityReadService.findPage(
                                context.targetEntity().getEntityCode(),
                                condition, 1, 2)
                        : dynamicDataService.findPage(
                                context.targetEntity().getEntityCode(),
                                context.resolved().getTargetContentKey(),
                                condition, 1, 2);
        if (page.getTotal() != 1
                || page.getRecords().isEmpty()
                || !Objects.equals(
                recordId, page.getRecords().get(0).getId())) {
            throw forbidden(
                    "VIEW_COMPOSITION_LINK_CANDIDATE_OUT_OF_SCOPE",
                    "所选记录不满足目标列表固定条件或当前数据权限");
        }
    }

    private Map<String, Object> filterForBase(
            Map<String, Object> filters,
            String fieldCode) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String suffix : List.of("", "_op", "_start", "_end")) {
            String key = fieldCode + suffix;
            if (filters.containsKey(key)) {
                result.put(key, filters.get(key));
            }
        }
        return result;
    }

    private void removeFilterBase(
            Map<String, Object> filters,
            String fieldCode) {
        for (String suffix : List.of("", "_op", "_start", "_end")) {
            filters.remove(fieldCode + suffix);
        }
    }

    private String stripFilterSuffix(String key) {
        for (String suffix : List.of("_start", "_end", "_op")) {
            if (key.endsWith(suffix)) {
                return key.substring(0, key.length() - suffix.length());
            }
        }
        return key;
    }

    /** 只用于已签名候选条件和固定列表的 id 条件，不接收浏览器任意表达式。 */
    private boolean matchesFilters(
            EntityDataDTO record,
            Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        Set<String> bases = new LinkedHashSet<>();
        filters.keySet().forEach(key ->
                bases.add(stripFilterSuffix(key)));
        for (String base : bases) {
            Object actual = recordValue(record, base);
            Object expected = filters.get(base);
            String operator = filterOperator(filters, base);
            boolean matches = switch (operator) {
                case "IS_NULL" -> actual == null
                        || actual instanceof String text && text.isBlank();
                case "EQ" -> Objects.equals(text(actual), text(expected));
                case "NE" -> !Objects.equals(text(actual), text(expected));
                case "LIKE" -> actual != null && expected != null
                        && text(actual).contains(text(expected));
                case "IN" -> expected instanceof Collection<?> values
                        && values.stream().anyMatch(value ->
                        Objects.equals(text(actual), text(value)));
                case "BETWEEN" -> compareFilterValues(
                        actual, filters.get(base + "_start")) >= 0
                        && compareFilterValues(
                        actual, filters.get(base + "_end")) <= 0;
                case "GT" -> compareFilterValues(actual, expected) > 0;
                case "GE" -> compareFilterValues(actual, expected) >= 0;
                case "LT" -> compareFilterValues(actual, expected) < 0;
                case "LE" -> compareFilterValues(actual, expected) <= 0;
                default -> false;
            };
            if (!matches) {
                return false;
            }
        }
        return true;
    }

    private int compareFilterValues(Object left, Object right) {
        if (left == null || right == null) {
            return left == right ? 0 : left == null ? -1 : 1;
        }
        if (left instanceof Number || right instanceof Number) {
            try {
                return new java.math.BigDecimal(text(left))
                        .compareTo(new java.math.BigDecimal(text(right)));
            } catch (NumberFormatException ignored) {
                // 非标准数值按数据库字符串字段常见的字典序进行安全比较。
            }
        }
        return text(left).compareTo(text(right));
    }

    private EntityDataDTO requireResolvedMembership(
            ActionContext context,
            String recordId) {
        UiViewCompositionResolveResponse resolved = context.resolved();
        if (resolved.isMatchNone()) {
            throw conflict(
                    "VIEW_COMPOSITION_SELECTION_OUT_OF_SCOPE",
                    "当前关联条件没有可选择的数据");
        }
        if (FORM.equals(resolved.getTargetContentType())) {
            if (!Objects.equals(recordId, resolved.getTargetRecordId())) {
                throw forbidden(
                        "VIEW_COMPOSITION_SELECTION_OUT_OF_SCOPE",
                        "选择记录不属于当前关联内容");
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
                    "VIEW_COMPOSITION_SELECTION_OUT_OF_SCOPE",
                    "选择记录不满足关联内容的固定条件");
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
                    "VIEW_COMPOSITION_SELECTION_OUT_OF_SCOPE",
                    "选择记录不满足关联内容的固定条件或数据权限");
        }
        return page.getRecords().get(0);
    }

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

    private Map<String, Object> buildSourcePatch(
            ActionContext context,
            Map<String, Object> select,
            EntityDataDTO targetRecord) {
        List<FieldMapping> mappings = validateSelectMappings(context, select);
        Map<String, Object> patch = new LinkedHashMap<>();
        for (FieldMapping mapping : mappings) {
            Object value = recordValue(targetRecord, mapping.fromTarget());
            if (mapping.required() && value == null) {
                throw conflict(
                        "VIEW_COMPOSITION_MAPPING_REQUIRED_VALUE_MISSING",
                        "目标记录字段 “" + mapping.fromTarget()
                                + "” 为空，无法完成必填回填");
            }
            if (value instanceof Map<?, ?>) {
                throw conflict(
                        "VIEW_COMPOSITION_MAPPING_VALUE_INVALID",
                        "目标记录字段不能以对象形式直接回填");
            }
            patch.put(mapping.toSource(), value);
        }
        return patch;
    }

    private List<FieldMapping> validateSelectMappings(
            ActionContext context,
            Map<String, Object> select) {
        if (!FORM.equals(context.claims().ownerType())
                || !LIST.equals(context.resolved().getTargetContentType())) {
            throw conflict(
                    "VIEW_COMPOSITION_SELECT_CONTEXT_INVALID",
                    "选择回填只适用于表单中嵌入的目标列表");
        }
        List<Map<String, Object>> mappings = mapList(
                select.get("mappings"));
        if (mappings.isEmpty()) {
            throw conflict(
                    "VIEW_COMPOSITION_MAPPING_REQUIRED",
                    "已发布配置缺少选择结果回填字段");
        }
        List<FieldMapping> result = new ArrayList<>();
        for (Map<String, Object> mapping : mappings) {
            if (mapping.containsKey("literal")) {
                throw conflict(
                        "VIEW_COMPOSITION_MAPPING_INVALID",
                        "选择结果回填不允许使用常量代替目标记录字段");
            }
            String fromTarget = firstText(
                    mapping.get("sourceField"), mapping.get("source"));
            String toSource = firstText(
                    mapping.get("targetField"), mapping.get("target"));
            if (!StringUtils.hasText(fromTarget)
                    || !StringUtils.hasText(toSource)) {
                throw conflict(
                        "VIEW_COMPOSITION_MAPPING_INVALID",
                        "选择结果回填必须同时指定目标记录字段和当前表单字段");
            }
            requireReadableTargetListField(context, fromTarget);
            requireEditableSourceFormField(context, toSource);
            result.add(new FieldMapping(
                    fromTarget,
                    toSource,
                    !Boolean.FALSE.equals(mapping.get("required"))));
        }
        return List.copyOf(result);
    }

    private void requireReadableTargetListField(
            ActionContext context,
            String fieldCode) {
        EntityField field = requirePinnedField(
                context.targetSchema(), fieldCode, "目标");
        if (Boolean.FALSE.equals(field.getRuntimeReadable())) {
            throw forbidden(
                    "VIEW_COMPOSITION_MAPPING_FIELD_FORBIDDEN",
                    "目标字段不可用于运行时回填: " + fieldCode);
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
                    "目标字段未在固定列表版本中显示，不能回填: " + fieldCode);
        }
        String sourceType = normalize(text(
                publishedField.get("dataSourceType")));
        if (StringUtils.hasText(sourceType)
                && !Set.of("ENTITY_FIELD", "REFERENCE").contains(sourceType)) {
            throw forbidden(
                    "VIEW_COMPOSITION_MAPPING_FIELD_FORBIDDEN",
                    "计算列或自定义列不能作为实体字段回填: " + fieldCode);
        }
    }

    private void requireEditableSourceFormField(
            ActionContext context,
            String fieldCode) {
        EntityField field = requirePinnedField(
                context.sourceSchema(), fieldCode, "来源");
        if (Boolean.FALSE.equals(field.getEditable())) {
            throw forbidden(
                    "VIEW_COMPOSITION_MAPPING_FIELD_FORBIDDEN",
                    "当前实体字段不可编辑: " + fieldCode);
        }
        Map<String, Object> publishedField = mapList(
                context.ownerSnapshot().get("legacyFields"))
                .stream()
                .filter(item -> Objects.equals(
                        fieldCode, text(item.get("fieldCode"))))
                .findFirst()
                .orElse(null);
        if (publishedField == null
                || truthy(publishedField.get("isHidden"))
                || truthy(publishedField.get("isReadonly"))
                || !editableInMode(publishedField, "edit")) {
            throw forbidden(
                    "VIEW_COMPOSITION_MAPPING_FIELD_FORBIDDEN",
                    "当前字段未在固定表单版本中开放编辑: " + fieldCode);
        }
    }

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

    private Map<String, UiViewCompositionActionCapabilityDTO> capabilities(
            ActionContext context) {
        Set<String> declared = new LinkedHashSet<>(
                stringList(context.config().get("actions")));
        Map<String, UiViewCompositionActionCapabilityDTO> result =
                new LinkedHashMap<>();
        for (String action : List.of(
                "VIEW", "SELECT", "CREATE", "EDIT", "LINK", "UNLINK",
                "SAVE_WITH_FORM")) {
            if (!declared.contains(action)) {
                result.put(action, denied("未在已发布配置中启用"));
                continue;
            }
            result.put(action, capability(context, action, declared));
        }
        for (Map<String, Object> binding : actionServices(context)) {
            String actionKey = normalize(text(binding.get("actionKey")));
            if (!StringUtils.hasText(actionKey)
                    || result.containsKey(actionKey)) {
                continue;
            }
            try {
                dataSourceService.validatePinnedActionOperation(
                        requiredField(
                                binding,
                                "executableSnapshot",
                                "动作接口操作快照"),
                        requiredField(
                                binding,
                                "definitionHash",
                                "动作接口操作哈希"),
                        requiredField(binding, "serviceId", "接口服务"),
                        requiredField(binding, "sourceCode", "接口服务编码"),
                        integer(binding.get("serviceRevision")),
                        requiredField(binding, "operationCode", "接口操作"),
                        context.claims().ownerType());
                result.put(actionKey, allowed());
            } catch (RuntimeException exception) {
                result.put(actionKey, denied(exception.getMessage()));
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private UiViewCompositionActionCapabilityDTO capability(
            ActionContext context,
            String action,
            Set<String> declared) {
        return switch (action) {
            case "VIEW" -> allowed();
            case "CREATE" -> createCapability(context);
            case "EDIT" -> editCapability(context);
            case "SAVE_WITH_FORM" -> denied(
                    "关联内容尚未接入随宿主提交，请使用已有组成型子表单或重复器");
            case "SELECT" -> selectCapability(context, declared);
            case "LINK", "UNLINK" -> relationCapability(context);
            default -> denied("动作不受支持");
        };
    }

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
        if (Boolean.TRUE.equals(create.get("associateAfterCreate"))) {
            return denied(
                    "新增后自动建立关联尚未接入同一事务，请关闭后再发布");
        }
        try {
            return allowed(resolveCreateInitialValues(context, create));
        } catch (RuntimeException exception) {
            return denied(exception.getMessage());
        }
    }

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

    private void requireExactTargetForm(
            ActionContext context,
            String action,
            String targetEntityCode,
            String targetRecordId,
            String targetFormId,
            String targetReleaseId,
            Integer targetReleaseVersion,
            String targetReleaseResolutionToken) {
        UiViewCompositionResolveResponse resolved = context.resolved();
        if (!FORM.equals(resolved.getTargetContentType())
                || !Objects.equals(
                resolved.getTargetEntityCode(), trim(targetEntityCode))
                || !Objects.equals(
                resolved.getTargetContentId(), trim(targetFormId))
                || !Objects.equals(
                resolved.getTargetReleaseId(), trim(targetReleaseId))
                || !Objects.equals(
                resolved.getTargetReleaseVersion(), targetReleaseVersion)) {
            throw forbidden(
                    "VIEW_COMPOSITION_FORM_CONTEXT_MISMATCH",
                    "提交的目标实体或表单发布版本与关联内容不一致");
        }
        if (!StringUtils.hasText(targetReleaseResolutionToken)) {
            throw forbidden(
                    "VIEW_COMPOSITION_FORM_CONTEXT_REQUIRED",
                    "目标表单缺少精确发布版本授权，已停止提交");
        }
        // 复用现有表单发布授权校验；后续正式提交还会在
        // PublishedFormSubmissionService 中再次校验并应用该快照。
        releaseService.resolveAuthorizedRuntimeFormRelease(
                targetFormId,
                targetReleaseId,
                targetReleaseVersion,
                targetReleaseResolutionToken);
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

    private UiViewCompositionActionCapabilityDTO selectCapability(
            ActionContext context,
            Set<String> declared) {
        if (!LIST.equals(context.resolved().getTargetContentType())) {
            return denied("选择记录仅适用于目标列表");
        }
        if (!hasPermission(
                context.targetEntity(), EntityPermissionAction.VIEW)) {
            return denied("没有目标实体查看权限");
        }
        Map<String, Object> select = map(
                map(context.config().get("actionSettings")).get("select"));
        String result = normalize(text(select.get("result")));
        if ("LINK".equals(result)) {
            if (!declared.contains("LINK")) {
                return denied("选择后建立关联必须同时启用建立关联");
            }
            return relationCapability(context);
        }
        if (!"FILL_FIELDS".equals(result)) {
            return denied("选择结果处理方式不受支持");
        }
        try {
            validateSelectMappings(context, select);
            return allowed();
        } catch (RuntimeException exception) {
            return denied(exception.getMessage());
        }
    }

    private UiViewCompositionActionCapabilityDTO relationCapability(
            ActionContext context) {
        String type = normalize(text(context.relation().get("type")));
        if (!MUTABLE_RELATIONS.contains(type)) {
            return denied("当前关联方式不支持关系写入");
        }
        try {
            if ("REFERENCE_FIELD".equals(type)) {
                EntityField field = requirePinnedField(
                        context.sourceSchema(),
                        requiredField(
                                context.relation(),
                                "sourceField",
                                "来源引用字段"),
                        "来源");
                requireReference(
                        field, context.targetEntity().getId(), "来源");
            } else if ("REVERSE_REFERENCE".equals(type)) {
                EntityField field = requirePinnedField(
                        context.targetSchema(),
                        requiredField(
                                context.relation(),
                                "targetField",
                                "目标引用字段"),
                        "目标");
                requireReference(
                        field, context.sourceEntity().getId(), "目标");
            } else {
                requireAssociationRelation(context);
            }
        } catch (RuntimeException exception) {
            return denied(exception.getMessage());
        }
        EntityDefinition writeEntity = "REFERENCE_FIELD".equals(type)
                ? context.sourceEntity() : context.targetEntity();
        if (writeEntity.getStorageMode()
                != EntityDefinition.StorageMode.DYNAMIC) {
            return denied("系统实体不支持通过关联内容直接写入关系");
        }
        if (!hasPermission(writeEntity, EntityPermissionAction.UPDATE)) {
            return denied("没有关系字段所属实体的编辑权限");
        }
        if (!hasPermission(
                context.targetEntity(), EntityPermissionAction.VIEW)) {
            return denied("没有目标实体查看权限");
        }
        return allowed();
    }

    /** 根据固定关系生成最小候选条件，并与目标列表固定条件安全求交。 */
    private CandidatePlan candidatePlan(ActionContext context) {
        String relationType = normalize(text(
                context.relation().get("type")));
        Map<String, Object> listFixed = targetListFixedFilters(context);
        Map<String, Object> candidate = new LinkedHashMap<>();
        boolean matchNone = false;
        if ("REFERENCE_FIELD".equals(relationType)) {
            EntityDataDTO source = readAccessible(
                    context.sourceEntity(),
                    context.claims().sourceRecordId(),
                    null);
            String sourceField = requiredField(
                    context.relation(), "sourceField", "来源引用字段");
            String currentId = trim(text(scalar(
                    recordValue(source, sourceField), sourceField)));
            if (StringUtils.hasText(currentId)) {
                // REFERENCE_FIELD 是单值关系，现有 LINK 动作明确禁止隐式替换。
                // 因此已有引用时不展示“看起来可选、提交必失败”的其他记录；用户
                // 需先在当前已关联列表解除关系，再重新打开候选列表。
                matchNone = true;
            }
        } else {
            String targetField;
            if ("REVERSE_REFERENCE".equals(relationType)) {
                targetField = requiredField(
                        context.relation(), "targetField", "目标引用字段");
            } else if ("ENTITY_RELATION".equals(relationType)) {
                targetField = requireAssociationRelation(context)
                        .getChildRefFieldCode();
            } else {
                throw conflict(
                        "VIEW_COMPOSITION_RELATION_ACTION_UNSUPPORTED",
                        "当前数据关联方式不支持选择候选记录建立关系");
            }
            if (hasFilterBase(listFixed, targetField)
                    && !"IS_NULL".equals(filterOperator(
                    listFixed, targetField))) {
                // SQL 的普通比较与 NULL 不会相交；目标列表若把关系字段固定为
                // 其它值，候选集合必须安全地视为空，不能覆盖列表约束。
                matchNone = true;
            }
            if (!matchNone) {
                // 即使目标列表本身已配置 IS_NULL，也仍在候选令牌中显式签入
                // 关系条件。这样候选安全边界不依赖列表过滤器是否携带占位值，
                // 更不会因某种运行时忽略仅含 _op 的条件而退化为全量查询。
                candidate.put(targetField, true);
                candidate.put(targetField + "_op", "IS_NULL");
            }
        }
        return new CandidatePlan(
                Collections.unmodifiableMap(candidate), matchNone);
    }

    private Map<String, Object> targetListFixedFilters(
            ActionContext context) {
        Object raw = map(context.targetSnapshot().get("list"))
                .get("fixedFilterConfig");
        if (raw == null) {
            return Map.of();
        }
        if (raw instanceof Map<?, ?>) {
            return map(raw);
        }
        if (raw instanceof String text && StringUtils.hasText(text)) {
            try {
                return objectMapper.readValue(
                        text, new TypeReference<Map<String, Object>>() {});
            } catch (Exception exception) {
                throw conflict(
                        "VIEW_COMPOSITION_TARGET_LIST_FILTER_INVALID",
                        "固定目标列表的筛选条件无法解析");
            }
        }
        throw conflict(
                "VIEW_COMPOSITION_TARGET_LIST_FILTER_INVALID",
                "固定目标列表的筛选条件格式不正确");
    }

    private boolean hasFilterBase(
            Map<String, Object> filters,
            String fieldCode) {
        return filters.containsKey(fieldCode)
                || filters.containsKey(fieldCode + "_op")
                || filters.containsKey(fieldCode + "_start")
                || filters.containsKey(fieldCode + "_end");
    }

    private String filterOperator(
            Map<String, Object> filters,
            String fieldCode) {
        String operator = normalize(text(filters.get(fieldCode + "_op")));
        if (StringUtils.hasText(operator)) {
            return operator;
        }
        if (filters.containsKey(fieldCode + "_start")
                || filters.containsKey(fieldCode + "_end")) {
            return "BETWEEN";
        }
        return filters.get(fieldCode) instanceof Collection<?>
                ? "IN" : "EQ";
    }

    private boolean hasPermission(
            EntityDefinition entity,
            EntityPermissionAction action) {
        return entity.getStorageMode() == EntityDefinition.StorageMode.SYSTEM
                || PermissionUtil.hasPermission(
                        action.permissionCode(entity.getEntityCode()));
    }

    private UiViewCompositionActionCapabilityDTO allowed() {
        return allowed(Map.of());
    }

    private UiViewCompositionActionCapabilityDTO allowed(
            Map<String, Object> initialValues) {
        return UiViewCompositionActionCapabilityDTO.builder()
                .available(true)
                .reason("")
                .initialValues(initialValues == null
                        ? Map.of() : initialValues)
                .build();
    }

    private UiViewCompositionActionCapabilityDTO denied(String reason) {
        return UiViewCompositionActionCapabilityDTO.builder()
                .available(false)
                .reason(StringUtils.hasText(reason) ? reason : "操作不可用")
                .build();
    }

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

    private ActionContext resolveContext(String actionContextToken) {
        UiViewCompositionTokenService.Claims claims =
                tokenService.verifySourceRow(actionContextToken);
        UiViewCompositionResolveRequest resolveRequest =
                new UiViewCompositionResolveRequest();
        resolveRequest.setOwnerType(claims.ownerType());
        resolveRequest.setOwnerId(claims.ownerId());
        resolveRequest.setReleaseId(claims.releaseId());
        resolveRequest.setReleaseVersion(claims.releaseVersion());
        resolveRequest.setCompositionKey(claims.compositionKey());
        resolveRequest.setRowContextToken(actionContextToken);
        UiViewCompositionResolveResponse resolved =
                runtimeService.resolve(resolveRequest);

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
                        "关联内容不存在于签名宿主发布版本"));
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
                map(config.get("relation")),
                sourceEntity,
                targetEntity,
                sourceSchema,
                targetSchema);
    }

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

    private ValidatedAction validateRequest(
            UiViewCompositionActionRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getActionContextToken())) {
            throw new IllegalArgumentException("关联内容动作上下文不能为空");
        }
        String action = normalize(request.getAction());
        if (!StringUtils.hasText(action)
                || !action.matches("[A-Z][A-Z0-9_.-]{0,99}")) {
            throw new IllegalArgumentException(
                    "动作标识必须以字母开头，且只能包含字母、数字、点、横线或下划线");
        }
        if (KNOWN_ACTIONS.contains(action)
                && !EXECUTABLE_ACTIONS.contains(action)
                && !"VIEW".equals(action)) {
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
        if (EXECUTABLE_ACTIONS.contains(action) && ids.isEmpty()) {
            throw new IllegalArgumentException("请选择至少一条目标记录");
        }
        return new ValidatedAction(
                request.getActionContextToken().trim(),
                trim(request.getCandidateListContextToken()),
                action,
                ids,
                operationId);
    }

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

    private void requireSelectionCount(List<String> ids, String mode) {
        if (!Set.of("SINGLE", "MULTIPLE").contains(mode)) {
            throw conflict(
                    "VIEW_COMPOSITION_SELECT_MODE_INVALID",
                    "已发布配置包含不支持的选择方式");
        }
        if ("SINGLE".equals(mode) && ids.size() != 1) {
            throw new IllegalArgumentException("当前配置只能选择一条记录");
        }
    }

    private void requireDynamic(
            EntityDefinition entity,
            String label) {
        if (entity.getStorageMode() != EntityDefinition.StorageMode.DYNAMIC) {
            throw conflict(
                    "VIEW_COMPOSITION_SYSTEM_ENTITY_WRITE_UNSUPPORTED",
                    label + "为系统实体，不能通过关联内容直接写入");
        }
    }

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

    private void requireReference(
            EntityField field,
            String expectedEntityId,
            String label) {
        if (field.getFieldType() != EntityField.FieldType.REFERENCE
                || !Objects.equals(
                expectedEntityId, field.getRefEntityId())) {
            throw conflict(
                    "VIEW_COMPOSITION_RELATION_INVALID",
                    label + "关联字段不是指向预期实体的单值引用");
        }
    }

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

    private Object scalar(Object value, String fieldCode) {
        if (value instanceof Collection<?> || value instanceof Map<?, ?>) {
            throw conflict(
                    "VIEW_COMPOSITION_RELATION_VALUE_INVALID",
                    "关系字段必须为单值: " + fieldCode);
        }
        return value;
    }

    private boolean truthy(Object value) {
        return Boolean.TRUE.equals(value)
                || value instanceof Number number && number.intValue() != 0
                || value instanceof String text
                && ("true".equalsIgnoreCase(text) || "1".equals(text));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("关联内容幂等摘要生成失败", exception);
        }
    }

    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, child) ->
                result.put(String.valueOf(key), child));
        return result;
    }

    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> source)) {
            return List.of();
        }
        return source.stream()
                .map(this::map)
                .filter(item -> !item.isEmpty())
                .toList();
    }

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

    private String firstText(Object... values) {
        for (Object value : values) {
            String candidate = trim(text(value));
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String normalizeHash(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

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

    private BusinessConflictException conflict(
            String code,
            String message) {
        return new BusinessConflictException(code, message);
    }

    private BusinessForbiddenException forbidden(
            String code,
            String message) {
        return new BusinessForbiddenException(code, message);
    }

    private record ActionContext(
            UiViewCompositionTokenService.Claims claims,
            UiViewCompositionResolveResponse resolved,
            Map<String, Object> ownerSnapshot,
            Map<String, Object> targetSnapshot,
            Map<String, Object> config,
            Map<String, Object> relation,
            EntityDefinition sourceEntity,
            EntityDefinition targetEntity,
            EntityPublishedSnapshot sourceSchema,
            EntityPublishedSnapshot targetSchema) {
    }

    private record ValidatedAction(
            String actionContextToken,
            String candidateListContextToken,
            String action,
            List<String> targetRecordIds,
            String operationId) {
    }

    private record FieldMapping(
            String fromTarget,
            String toSource,
            boolean required) {
    }

    private record MutationPlan(
            List<EntityMutationCommand> commands,
            List<String> targetRecordIds) {
    }

    private record CandidatePlan(
            Map<String, Object> filters,
            boolean matchNone) {
    }

    private record LockKey(String entityCode, String recordId) {
    }
}
