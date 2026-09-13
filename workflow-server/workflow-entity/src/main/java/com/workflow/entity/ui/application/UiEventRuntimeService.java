package com.workflow.entity.ui.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.ui.api.request.UiDataSourceExecuteRequest;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import com.workflow.entity.ui.api.response.UiEventExecutionResult;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityPermissionAction;
import com.workflow.entity.form.application.EntityFormActionService;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditResult;
import com.workflow.contracts.audit.AuditRiskLevel;
import com.workflow.contracts.audit.SystemAuditEvent;
import com.workflow.contracts.audit.port.SystemAuditPort;
import com.workflow.contracts.ui.UiDataSourceUsages;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.core.error.ForbiddenException;
import com.workflow.core.logging.LogValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * 统一 UI 事件执行链运行时。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UiEventRuntimeService {

    private static final int OPERATION_SNAPSHOT_VERSION = 1;
    private static final int MAX_LIST_BUTTON_SELECTION = 200;
    private static final List<String> PINNED_OPERATION_FIELDS = List.of(
            "sourceCode",
            "serviceRevision",
            "executableSnapshot",
            "definitionHash");
    private static final List<String> PINNED_BINDING_IDENTITY_FIELDS = List.of(
            "bindingOwnerType",
            "bindingOwnerId",
            "bindingTargetType",
            "bindingTargetKey");
    private static final Set<String> AUDIT_EFFECT_TYPES = Set.of(
            "FIELD_MAPPING",
            "MESSAGE",
            "OPEN_ROUTE",
            "CLOSE_FORM",
            "REFRESH_PARENT",
            "REFRESH_LIST",
            "DOWNLOAD_TASK");
    private static final Set<String> FORM_BUTTON_AUTHORIZATION_ERRORS = Set.of(
            "UI_DATA_SOURCE_DATA_SCOPE_DENIED",
            "UI_DATA_SOURCE_USER_DISABLED",
            "UI_DATA_SOURCE_USER_CONTEXT_REQUIRED",
            "UI_DATA_SOURCE_PERMISSION_PLAN_UNAVAILABLE",
            "UI_DATA_SOURCE_EXECUTION_ORIGIN_REQUIRED",
            "UI_DATA_SOURCE_CONFIG_NOT_FOUND",
            "UI_DATA_SOURCE_ENTITY_NOT_FOUND",
            "UI_DATA_SOURCE_RELEASE_REQUIRED",
            "UI_DATA_SOURCE_RELEASE_CONFLICT");

    private static final Set<String> WRITE_EVENTS = Set.of(
            UiDataSourceUsages.DATA_CREATE,
            UiDataSourceUsages.DATA_UPDATE,
            UiDataSourceUsages.DATA_DELETE,
            UiDataSourceUsages.DATA_BATCH_DELETE,
            UiDataSourceUsages.FORM_SAVE,
            UiDataSourceUsages.SUBFORM_SAVE,
            UiDataSourceUsages.TOOLBAR_BUTTON_CLICK,
            UiDataSourceUsages.ROW_BUTTON_CLICK,
            UiDataSourceUsages.FORM_BUTTON_CLICK,
            UiDataSourceUsages.FIELD_BUTTON_CLICK);

    private final UiEventBindingService bindingService;
    private final UiDataSourceService dataSourceService;
    private final UiEventValueMapper valueMapper;
    private final EntitySelectionRuntimeService selectionRuntimeService;
    private final SystemAuditPort auditPort;
    private final EntityActionCapabilityService actionCapabilityService;
    private final EntityFormActionService formActionService;
    private final UiEventExecutionReceiptService executionReceiptService;
    private final EntityDataDynamicService entityDataService;
    private final ObjectMapper objectMapper;

    /**
     * 执行已发布事件。按钮和字段事件可直接调用此入口。
     */
    public UiEventExecutionResult execute(
            UiEventExecuteRequest request) {
        return execute(request, null);
    }

    /**
     * 执行事件链，并在没有 REPLACE 步骤时调用平台默认处理。
     */
    public UiEventExecutionResult execute(
            UiEventExecuteRequest request,
            Function<Map<String, Object>, Object> defaultHandler) {
        long startedAt = System.nanoTime();
        try {
            requireFormButtonRequestShape(request);
            UiEventBindingService.ResolvedEventChain chain =
                    bindingService.resolvePublished(request);
            requireExecutableFormButtonChain(request, chain);
            requireExecutionPermission(request, chain);
            canonicalizeFormButtonRequest(request);
            UiEventExecutionResult result =
                    UiDataSourceUsages.FORM_BUTTON_CLICK.equals(
                            normalize(request.getEventCode()))
                            ? executionReceiptService.execute(
                                    request,
                                    chain,
                                    () -> executeChain(
                                            request,
                                            defaultHandler,
                                            chain))
                            : executeChain(
                                    request, defaultHandler, chain);
            recordExecution(
                    request,
                    result,
                    null,
                    startedAt);
            logCompleted(request, result, startedAt);
            return result;
        } catch (RuntimeException exception) {
            recordExecution(
                    request,
                    null,
                    exception,
                    startedAt);
            log.info(
                    "UI事件执行失败: configType={}, configId={}, releaseId={}, releaseVersion={}, eventCode={}, targetType={}, targetKey={}, requestId={}, recordId={}, failureType={}, durationMs={}",
                    LogValue.safe(
                            request == null
                                    ? null : request.getConfigType()),
                    LogValue.safe(
                            request == null
                                    ? null : request.getConfigId()),
                    LogValue.safe(
                            request == null
                                    ? null : request.getReleaseId()),
                    request == null
                            ? null : request.getReleaseVersion(),
                    LogValue.safe(
                            request == null
                                    ? null : request.getEventCode()),
                    LogValue.safe(
                            request == null
                                    ? null : request.getTargetType()),
                    LogValue.safe(
                            request == null
                                    ? null : request.getTargetKey()),
                    LogValue.safe(
                            request == null
                                    ? null : auditRequestId(request)),
                    LogValue.safe(
                            request == null
                                    ? null : request.getRecordId()),
                    LogValue.failureType(exception),
                    (System.nanoTime() - startedAt) / 1_000_000);
            throw exception;
        }
    }

    private UiEventExecutionResult executeChain(
            UiEventExecuteRequest request,
            Function<Map<String, Object>, Object> defaultHandler,
            UiEventBindingService.ResolvedEventChain chain) {
        UiEventExecutionResult result = new UiEventExecutionResult();
        Object selection =
                selectionRuntimeService.resolve(request, chain);
        Map<String, Object> state =
                initialState(request, selection);
        Object latest = null;

        for (Map<String, Object> step :
                steps(chain.steps(), "BEFORE")) {
            Object stepResult = executeStep(
                    step, request, chain, state, result);
            if (stepResult instanceof Map<?, ?> map) {
                mutableInput(state).putAll(stringMap(map));
            }
            if (stepResult != null) {
                latest = stepResult;
            }
        }

        List<Map<String, Object>> replacements =
                steps(chain.steps(), "REPLACE");
        if (!replacements.isEmpty()) {
            result.setReplaced(true);
            latest = executeStep(
                    replacements.get(0),
                    request,
                    chain,
                    state,
                    result);
        } else if (defaultHandler != null) {
            latest = defaultHandler.apply(
                    new LinkedHashMap<>(mutableInput(state)));
            result.setDefaultExecuted(true);
            trace(result, "PLATFORM_DEFAULT", "SUCCESS", null);
        }

        state.put("result", latest);
        for (Map<String, Object> step :
                steps(chain.steps(), "AFTER")) {
            Object stepResult = executeStep(
                    step, request, chain, state, result);
            if (Boolean.TRUE.equals(step.get("replaceResult"))) {
                latest = stepResult;
                state.put("result", latest);
                // 列表运行时必须据此重新加载权威行并覆盖 Provider 声明的
                // actionCapabilities；AFTER 替换与 REPLACE 步骤具有同等信任边界。
                result.setReplaced(true);
            }
        }
        result.setData(latest);
        return result;
    }

    private void logCompleted(
            UiEventExecuteRequest request,
            UiEventExecutionResult result,
            long startedAt) {
        log.info(
                "UI事件执行完成: configType={}, configId={}, releaseId={}, releaseVersion={}, eventCode={}, targetType={}, targetKey={}, requestId={}, recordId={}, defaultExecuted={}, replaced={}, replayed={}, traceCount={}, durationMs={}",
                LogValue.safe(request == null
                        ? null : request.getConfigType()),
                LogValue.safe(request == null
                        ? null : request.getConfigId()),
                LogValue.safe(request == null
                        ? null : request.getReleaseId()),
                request == null ? null : request.getReleaseVersion(),
                LogValue.safe(request == null
                        ? null : request.getEventCode()),
                LogValue.safe(request == null
                        ? null : request.getTargetType()),
                LogValue.safe(request == null
                        ? null : request.getTargetKey()),
                LogValue.safe(request == null
                        ? null : auditRequestId(request)),
                LogValue.safe(request == null
                        ? null : request.getRecordId()),
                result.isDefaultExecuted(),
                result.isReplaced(),
                result.isReplayed(),
                result.getTrace() == null ? 0 : result.getTrace().size(),
                (System.nanoTime() - startedAt) / 1_000_000);
    }

    private void requireExecutionPermission(
            UiEventExecuteRequest request,
            UiEventBindingService.ResolvedEventChain chain) {
        String entityCode = chain == null ? null : chain.entityCode();
        if (!StringUtils.hasText(entityCode)) {
            throw new IllegalArgumentException("UI 事件未关联有效实体");
        }
        String eventCode = normalize(request.getEventCode());
        EntityPermissionAction action = switch (eventCode) {
            case UiDataSourceUsages.DATA_CREATE ->
                    EntityPermissionAction.CREATE;
            case UiDataSourceUsages.DATA_UPDATE,
                    UiDataSourceUsages.FORM_SAVE,
                    UiDataSourceUsages.SUBFORM_SAVE,
                    UiDataSourceUsages.FIELD_BUTTON_CLICK ->
                    EntityPermissionAction.UPDATE;
            case UiDataSourceUsages.FORM_BUTTON_CLICK -> {
                // 必须复用刚解析事件链的同一份有效快照，禁止在权限检查与
                // Provider 执行之间再次跟随 ACTIVE/热修复指针。
                String authorizedMode = formActionService.requireCustomButton(
                        request,
                        chain.snapshot(),
                        chain.releaseId(),
                        chain.releaseVersion());
                if (!StringUtils.hasText(authorizedMode)) {
                    throw new BusinessForbiddenException(
                            "UI_EVENT_FORM_BUTTON_MODE_REQUIRED",
                            "表单按钮缺少已验证的运行模式");
                }
                request.setServerAuthorizedMode(authorizedMode);
                yield null;
            }
            case UiDataSourceUsages.DATA_DELETE ->
                    EntityPermissionAction.DELETE;
            case UiDataSourceUsages.DATA_BATCH_DELETE ->
                    EntityPermissionAction.BATCH_DELETE;
            case UiDataSourceUsages.ROW_BUTTON_CLICK,
                    UiDataSourceUsages.TOOLBAR_BUTTON_CLICK -> {
                requirePublishedListButton(request, chain, eventCode);
                yield null;
            }
            default -> StringUtils.hasText(request.getRecordId())
                    ? EntityPermissionAction.VIEW
                    : EntityPermissionAction.LIST;
        };
        if (action != null) {
            actionCapabilityService.requireStandardPermission(
                    entityCode,
                    action);
        }
    }

    /**
     * 从本次事件链已经固定的列表发布快照定位按钮，并以服务端记录重建输入。
     *
     * <p>自定义按钮不能回退 UPDATE 权限；行与选择集都按当前列表数据范围重新
     * 加载，发布按钮配置和权威记录在任何 condition/inputMapping 运行前替换
     * 客户端同名值。</p>
     */
    private void requirePublishedListButton(
            UiEventExecuteRequest request,
            UiEventBindingService.ResolvedEventChain chain,
            String eventCode) {
        if (!"LIST".equals(normalize(request.getConfigType()))
                || !"BUTTON".equals(normalize(request.getTargetType()))
                || !StringUtils.hasText(request.getTargetKey())) {
            throw new BusinessForbiddenException(
                    "UI_EVENT_LIST_BUTTON_TARGET_REQUIRED",
                    "列表按钮事件必须使用已发布列表的精确 BUTTON 目标");
        }
        Map<String, Object> button = publishedListButton(
                chain.snapshot(), eventCode, request.getTargetKey());
        if (button == null) {
            throw new BusinessForbiddenException(
                    "UI_EVENT_LIST_BUTTON_NOT_FOUND",
                    "列表发布快照中不存在该按钮");
        }
        if (!"CUSTOM".equals(normalize(text(button.get("type"))))
                || !"EVENT".equals(normalize(text(
                button.get("customMode"))))) {
            throw new BusinessForbiddenException(
                    "UI_EVENT_LIST_BUTTON_NOT_EXECUTABLE",
                    "列表按钮不是可执行的自定义事件按钮");
        }
        request.setEntityCode(chain.entityCode());
        request.setListKey(chain.listKey());
        bindTrustedListExecution(request, chain, eventCode);

        if (UiDataSourceUsages.ROW_BUTTON_CLICK.equals(eventCode)) {
            if (!StringUtils.hasText(request.getRecordId())) {
                throw new BusinessForbiddenException(
                        "UI_EVENT_LIST_ROW_REQUIRED",
                        "行按钮事件缺少记录 ID");
            }
            EntityDataDTO row = entityDataService.findAccessibleById(
                    chain.entityCode(), request.getRecordId(), chain.listKey());
            actionCapabilityService.requirePublishedListButton(
                    chain.entityCode(), request.getTargetKey(), button, row);
            Map<String, Object> trustedRow = rowMap(row);
            request.setSelectedIds(List.of());
            request.setSelection(trustedRow);
            canonicalizeListButtonInput(
                    request, button, trustedRow, List.of());
            return;
        }

        List<String> selectedIds = request.getSelectedIds() == null
                ? List.of()
                : request.getSelectedIds().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (selectedIds.size() > MAX_LIST_BUTTON_SELECTION) {
            throw new BusinessForbiddenException(
                    "UI_EVENT_LIST_SELECTION_TOO_LARGE",
                    "列表按钮单次最多处理 "
                            + MAX_LIST_BUTTON_SELECTION + " 条数据");
        }
        boolean selectionAction = Set.of(
                "batchDelete", "exportSelected")
                .contains(request.getTargetKey());
        if (selectionAction && selectedIds.isEmpty()) {
            throw new BusinessForbiddenException(
                    "UI_EVENT_LIST_SELECTION_REQUIRED",
                    "请先选择数据");
        }
        List<EntityDataDTO> rows = selectedIds.stream()
                .map(id -> entityDataService.findAccessibleById(
                        chain.entityCode(), id, chain.listKey()))
                .toList();
        if (selectionAction) {
            actionCapabilityService.requirePublishedListButton(
                    chain.entityCode(), request.getTargetKey(), button, rows);
        } else {
            // 运行时能力契约只把两个内置批量动作定义为选择集按钮；其它
            // 工具栏按钮始终在 row=null 上求值，不能由客户端提交 selectedIds
            // 改变 availabilityRule 的判定上下文。
            actionCapabilityService.requirePublishedListButton(
                    chain.entityCode(), request.getTargetKey(), button,
                    (EntityDataDTO) null);
        }
        List<Map<String, Object>> trustedRows = rows.stream()
                .map(this::rowMap)
                .toList();
        request.setRecordId(null);
        request.setSelectedIds(selectedIds);
        request.setSelection(trustedRows);
        canonicalizeListButtonInput(
                request, button, null, trustedRows);
    }

    private Map<String, Object> publishedListButton(
            Map<String, Object> snapshot,
            String eventCode,
            String targetKey) {
        Object rawList = snapshot == null ? null : snapshot.get("list");
        if (!(rawList instanceof Map<?, ?> list)) {
            return null;
        }
        String section = UiDataSourceUsages.ROW_BUTTON_CLICK.equals(eventCode)
                ? "rowActionConfig" : "toolbarConfig";
        Object rawButtons = list.get(section);
        if (!(rawButtons instanceof List<?> buttons)) {
            return null;
        }
        return buttons.stream()
                .filter(Map.class::isInstance)
                .map(item -> stringMap((Map<?, ?>) item))
                .filter(item -> Objects.equals(
                        targetKey, text(item.get("key"))))
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> rowMap(EntityDataDTO row) {
        return objectMapper.convertValue(
                row, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * 给列表按钮 Provider 调用生成服务端可信幂等种子。
     *
     * <p>历史发布通过 releaseResolutionToken 固定后，Provider 访问层要求内部
     * 调用携带可信种子。客户端 requestId 只参与服务端绑定后的摘要；无合法
     * requestId 时使用随机 nonce，避免任何客户端字段被直接当作可信标识。</p>
     */
    private void bindTrustedListExecution(
            UiEventExecuteRequest request,
            UiEventBindingService.ResolvedEventChain chain,
            String eventCode) {
        String requestSeed = UiEventExecutionReceiptService
                .validRequestIdOrNull(request.getRequestId());
        if (!StringUtils.hasText(requestSeed)) {
            requestSeed = UUID.randomUUID().toString();
        }
        String material = String.join("|",
                "ui-list-button",
                text(UserContext.getUserId()),
                text(chain.releaseId()),
                String.valueOf(chain.releaseVersion()),
                text(chain.effectiveReleaseId()),
                text(chain.effectiveContentHash()),
                normalize(eventCode),
                normalize(request.getTargetType()),
                text(request.getTargetKey()),
                text(request.getRecordId()),
                requestSeed);
        request.setServerIdempotencyKey(
                "ui-list-button:" + UUID.nameUUIDFromBytes(
                        material.getBytes(StandardCharsets.UTF_8))
                        .toString().replace("-", ""));
    }

    /** 用发布按钮和服务端记录覆盖所有列表按钮保留输入。 */
    private void canonicalizeListButtonInput(
            UiEventExecuteRequest request,
            Map<String, Object> button,
            Map<String, Object> row,
            List<Map<String, Object>> selectedRows) {
        Map<String, Object> input = new LinkedHashMap<>();
        if (request.getInput() != null) {
            request.getInput().forEach((key, value) -> {
                if (!reservedListButtonInputKey(key)) {
                    input.put(key, value);
                }
            });
        }
        input.put("button", immutableBusinessCopy(button));
        input.put("row", row == null ? Map.of()
                : immutableBusinessCopy(row));
        input.put("currentRow", row == null ? Map.of()
                : immutableBusinessCopy(row));
        input.put("selectedRows", immutableBusinessCopy(selectedRows));
        input.put("records", immutableBusinessCopy(selectedRows));
        input.put("recordId", request.getRecordId());
        input.put("selectedIds", request.getSelectedIds());
        input.put("entityCode", request.getEntityCode());
        input.put("listKey", request.getListKey());
        input.put("eventCode", normalize(request.getEventCode()));
        input.put("targetType", normalize(request.getTargetType()));
        input.put("targetKey", request.getTargetKey());
        request.setInput(input);

        if (request.getContext() != null) {
            Map<String, Object> context = new LinkedHashMap<>();
            request.getContext().forEach((key, value) -> {
                if (!reservedListButtonInputKey(key)) {
                    context.put(key, value);
                }
            });
            context.put("recordId", request.getRecordId());
            context.put("selectedIds", request.getSelectedIds());
            context.put("entityCode", request.getEntityCode());
            context.put("listKey", request.getListKey());
            context.put("eventCode", normalize(request.getEventCode()));
            context.put("targetType", normalize(request.getTargetType()));
            context.put("targetKey", request.getTargetKey());
            request.setContext(context);
        }
    }

    private boolean reservedListButtonInputKey(String key) {
        String normalized = key == null ? ""
                : key.replace("_", "")
                .replace("-", "")
                .toLowerCase(Locale.ROOT);
        return Set.of(
                "button", "row", "record", "currentrow",
                "selectedrows", "records", "selection",
                "recordid", "selectedids", "entitycode", "listkey",
                "eventcode", "targettype", "targetkey", "configid",
                "releaseid", "releaseversion", "releaseresolutiontoken")
                .contains(normalized);
    }

    /**
     * 在任何 condition/inputMapping 运行前重建表单按钮输入。
     *
     * <p>原始 input 先按与 Provider 访问层相同的规则递归校验；随后只保留精确
     * {@code input.form} 业务树，并注入已发布按钮、已鉴权模式和记录。客户端
     * button/task/process/record/mode 均不能参与事件语义。FORM_BUTTON 的原始
     * context 不属于业务输入，全部丢弃后只注入服务端已验证的路由坐标。</p>
     */
    private void canonicalizeFormButtonRequest(
            UiEventExecuteRequest request) {
        if (request == null
                || !UiDataSourceUsages.FORM_BUTTON_CLICK.equals(normalize(
                        request.getEventCode()))) {
            return;
        }
        UiDataSourceExecutionAccessService.validateFormButtonClientInput(
                request.getInput());
        UiDataSourceExecutionAccessService.validateFormButtonClientContext(
                request.getContext());

        Object rawForm = request.getInput() == null
                ? null : request.getInput().get("form");
        if (rawForm != null && !(rawForm instanceof Map<?, ?>)) {
            throw new BusinessForbiddenException(
                    "UI_DATA_SOURCE_EXECUTION_CONTEXT_SPOOFED",
                    "表单按钮 input.form 必须为业务字段对象");
        }
        Map<String, Object> publishedButton =
                request.getServerPublishedButton();
        if (publishedButton == null
                || !Objects.equals(
                        request.getTargetKey(),
                        text(publishedButton.get("key")))) {
            throw new BusinessConflictException(
                    "UI_EVENT_FORM_BUTTON_SNAPSHOT_INVALID",
                    "表单按钮发布配置不可用，请刷新后重试");
        }

        Map<String, Object> canonicalInput = new LinkedHashMap<>();
        canonicalInput.put("form", rawForm == null
                ? Map.of() : immutableBusinessCopy(rawForm));
        canonicalInput.put("button", immutableBusinessCopy(
                publishedButton));
        canonicalInput.put("mode", request.getServerAuthorizedMode());
        canonicalInput.put("recordId", request.getRecordId());
        request.setInput(canonicalInput);

        Map<String, Object> canonicalContext = new LinkedHashMap<>();
        canonicalContext.put("mode", request.getServerAuthorizedMode());
        canonicalContext.put("recordId", request.getRecordId());
        canonicalContext.put("eventCode", normalize(request.getEventCode()));
        canonicalContext.put("targetType", normalize(request.getTargetType()));
        canonicalContext.put("targetKey", request.getTargetKey());
        request.setContext(canonicalContext);

        // 表单按钮没有列表选择语义；公开 selectedIds/selection 若继续保留，
        // 会绕过 canonical input 影响 condition 与 inputMapping。
        request.setSelectedIds(List.of());
        request.setSelection(null);
    }

    private Object immutableBusinessCopy(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, child) -> copy.put(
                    String.valueOf(key), immutableBusinessCopy(child)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof Collection<?> collection) {
            List<Object> copy = collection.stream()
                    .map(this::immutableBusinessCopy)
                    .toList();
            return Collections.unmodifiableList(copy);
        }
        return value;
    }

    /**
     * 发布校验之外的运行时防线：表单按钮必须有且仅有一个主处理步骤。
     *
     * <p>FORM_BUTTON_CLICK 没有平台默认业务动作，REPLACE 在该事件中表示自定义
     * 按钮的主处理，而不是跳过平台逻辑；BEFORE/AFTER 仍可作为主处理前后的
     * 辅助步骤存在。</p>
     */
    private void requireExecutableFormButtonChain(
            UiEventExecuteRequest request,
            UiEventBindingService.ResolvedEventChain chain) {
        if (!UiDataSourceUsages.FORM_BUTTON_CLICK.equals(normalize(
                request == null ? null : request.getEventCode()))) {
            return;
        }
        List<Map<String, Object>> mainSteps =
                chain == null || chain.steps() == null
                        ? List.of()
                        : chain.steps().stream()
                                .filter(step -> "REPLACE".equals(normalize(
                                        text(step.get("strategy")))))
                                .toList();
        int mainStepCount = mainSteps.size();
        if (mainStepCount != 1) {
            throw new BusinessConflictException(
                    "UI_EVENT_FORM_BUTTON_MAIN_STEP_REQUIRED",
                    "启用的表单自定义按钮最终有效链必须且只能包含一个主处理步骤，当前为 "
                            + mainStepCount + " 个");
        }
        if (hasExecutionCondition(mainSteps.get(0))) {
            // 运行时必须防御损坏或历史快照，避免主处理因条件不满足被标记为
            // SKIPPED，而按钮调用仍以成功结束。
            throw new BusinessConflictException(
                    "UI_EVENT_FORM_BUTTON_MAIN_STEP_CONDITION_UNSUPPORTED",
                    "表单自定义按钮的主处理步骤必须无条件执行，请移除主处理的执行条件");
        }
    }

    /** 与条件执行器保持一致：空对象不会参与判断，视为未配置条件。 */
    private boolean hasExecutionCondition(Map<String, Object> step) {
        return step != null
                && step.get("condition") instanceof Map<?, ?> condition
                && !condition.isEmpty();
    }

    /** 表单按钮公开请求只能选择精确 BUTTON 目标，不能借 OWNER 链绕过覆盖。 */
    private void requireFormButtonRequestShape(
            UiEventExecuteRequest request) {
        if (request == null
                || !UiDataSourceUsages.FORM_BUTTON_CLICK.equals(normalize(
                        request.getEventCode()))) {
            return;
        }
        if (!"FORM".equals(normalize(request.getConfigType()))
                || !"BUTTON".equals(normalize(request.getTargetType()))
                || !StringUtils.hasText(request.getTargetKey())) {
            throw new BusinessForbiddenException(
                    "UI_EVENT_FORM_BUTTON_TARGET_REQUIRED",
                    "FORM_BUTTON_CLICK 必须使用已发布表单的精确 BUTTON 目标");
        }
    }

    private void recordExecution(
            UiEventExecuteRequest request,
            UiEventExecutionResult result,
            RuntimeException exception,
            long startedAt) {
        String eventCode = normalize(
                request == null ? null : request.getEventCode());
        boolean write = WRITE_EVENTS.contains(eventCode);
        boolean formButton = UiDataSourceUsages.FORM_BUTTON_CLICK.equals(
                eventCode);
        Map<String, Object> before = new LinkedHashMap<>();
        if (request != null) {
            before.put("configType", normalize(request.getConfigType()));
            before.put("configId", request.getConfigId());
            before.put("releaseId", request.getReleaseId());
            before.put("releaseVersion", request.getReleaseVersion());
            before.put("entityCode", request.getEntityCode());
            before.put("listKey", request.getListKey());
            before.put("targetType", normalize(request.getTargetType()));
            before.put("targetKey", request.getTargetKey());
            if (UiDataSourceUsages.FORM_BUTTON_CLICK.equals(eventCode)) {
                before.put("requestId", auditRequestId(request));
            }
            before.put("recordId", request.getRecordId());
            before.put(
                    "selectedCount",
                    request.getSelectedIds() == null
                            ? 0 : request.getSelectedIds().size());
        }
        Map<String, Object> after = null;
        if (result != null) {
            after = new LinkedHashMap<>();
            after.put("defaultExecuted", result.isDefaultExecuted());
            after.put("replaced", result.isReplaced());
            if (formButton) {
                // 表单任意输入可能被 Provider 映射到消息、effect 或 trace，
                // 幂等审计只保留可关联但不含业务值的摘要。
                after.put("replayed", result.isReplayed());
                after.put("messagePresent", StringUtils.hasText(
                        result.getMessage()));
                after.put("effectTypes", effectTypes(result));
                after.put("effectCount", result.getEffects() == null
                        ? 0 : result.getEffects().size());
                after.put("trace", auditTrace(result));
            } else {
                // 非表单按钮事件保留原有审计契约。
                after.put("message", result.getMessage());
                after.put("effects", result.getEffects());
                after.put("trace", result.getTrace());
            }
        }
        try {
            auditPort.record(SystemAuditEvent.builder()
                    .eventId(UUID.randomUUID().toString()
                            .replace("-", ""))
                    .traceId(MDC.get("traceId"))
                    .module(AuditModule.INTEGRATION)
                    .action(write ? AuditAction.UPDATE : AuditAction.OTHER)
                    .operationName("执行UI事件链:" + eventCode)
                    .riskLevel(write
                            ? AuditRiskLevel.HIGH
                            : AuditRiskLevel.LOW)
                    .result(exception == null
                            ? AuditResult.SUCCESS
                            : AuditResult.FAILURE)
                    .required(false)
                    .operatorId(UserContext.getUserId())
                    .operatorName(UserContext.getUsername())
                    .targetType("UI_EVENT")
                    .targetId(eventTarget(request))
                    .targetName(eventCode)
                    .summary(executionSummary(
                            eventCode, result, exception))
                    .beforeData(before)
                    .afterData(after)
                    .changedFields(result == null
                            ? null : formButton
                            ? auditTrace(result)
                            : result.getTrace())
                    .errorCode(exception == null
                            ? null
                            : exception.getClass().getSimpleName())
                    .errorMessage(exception == null
                            ? null : formButton
                            ? exception.getClass().getSimpleName()
                            : exception.getMessage())
                    .durationMs((System.nanoTime() - startedAt)
                            / 1_000_000)
                    .createdAt(LocalDateTime.now())
                    .build());
        } catch (RuntimeException auditException) {
            log.warn(
                    "记录UI事件执行日志失败: eventCode={}, configId={}",
                    LogValue.safe(eventCode),
                    LogValue.safe(request == null ? null : request.getConfigId()),
                    auditException);
        }
    }

    private String eventTarget(UiEventExecuteRequest request) {
        if (request == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        parts.add(normalize(request.getConfigType()));
        parts.add(text(request.getConfigId()));
        if (StringUtils.hasText(request.getTargetType())) {
            parts.add(normalize(request.getTargetType()));
        }
        if (StringUtils.hasText(request.getTargetKey())) {
            parts.add(request.getTargetKey());
        }
        return parts.stream()
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.joining(":"));
    }

    private String executionSummary(
            String eventCode,
            UiEventExecutionResult result,
            RuntimeException exception) {
        if (exception != null) {
            return UiDataSourceUsages.FORM_BUTTON_CLICK.equals(eventCode)
                    ? eventCode + " 执行失败（"
                            + exception.getClass().getSimpleName() + "）"
                    : eventCode + " 执行失败: "
                            + firstText(exception.getMessage(), "未知错误");
        }
        int stepCount = result == null || result.getTrace() == null
                ? 0 : result.getTrace().size();
        String main = UiDataSourceUsages.FORM_BUTTON_CLICK.equals(eventCode)
                && result != null && result.isReplaced()
                ? "执行自定义按钮主处理"
                : result != null && result.isReplaced()
                ? "自定义接口替代平台处理"
                : result != null && result.isDefaultExecuted()
                ? "已执行平台默认处理"
                : "仅执行自定义映射或接口";
        return eventCode + " 执行成功，" + main
                + "，步骤数 " + stepCount;
    }

    private List<String> effectTypes(UiEventExecutionResult result) {
        if (result.getEffects() == null) {
            return List.of();
        }
        return result.getEffects().stream()
                .map(effect -> normalize(text(effect.get("type"))))
                .filter(StringUtils::hasText)
                .map(type -> AUDIT_EFFECT_TYPES.contains(type)
                        ? type : "UNKNOWN")
                .distinct()
                .toList();
    }

    /** 审计只记录步骤身份和状态，不复制 Provider 错误或字段映射数据。 */
    private List<Map<String, Object>> auditTrace(
            UiEventExecutionResult result) {
        if (result.getTrace() == null) {
            return List.of();
        }
        return result.getTrace().stream()
                .map(item -> {
                    Map<String, Object> summary = new LinkedHashMap<>();
                    summary.put("step", text(item.get("step")));
                    summary.put("status", normalize(text(
                            item.get("status"))));
                    return summary;
                })
                .toList();
    }

    private Object executeStep(
            Map<String, Object> step,
            UiEventExecuteRequest request,
            UiEventBindingService.ResolvedEventChain chain,
            Map<String, Object> state,
            UiEventExecutionResult result) {
        if (!valueMapper.matches(step.get("condition"), state)) {
            trace(result, stepLabel(step), "SKIPPED", null);
            return null;
        }
        String failurePolicy = normalize(text(
                step.getOrDefault("failurePolicy", "STOP")));
        try {
            Object raw;
            String serviceId = firstText(
                    step.get("serviceId"));
            if (StringUtils.hasText(serviceId)) {
                Object mappedInput = valueMapper.apply(
                        step.get("inputMapping"),
                        state,
                        state.get("input"));
                if (!(mappedInput instanceof Map<?, ?> inputMap)) {
                    throw new IllegalArgumentException(
                            "事件接口输入映射结果必须为对象");
                }
                UiDataSourceExecuteRequest execute =
                        new UiDataSourceExecuteRequest();
                execute.setUsage(normalize(request.getEventCode()));
                execute.setOperationCode(firstText(
                        step.get("operationCode")));
                if (!StringUtils.hasText(
                        execute.getOperationCode())) {
                    throw new IllegalArgumentException(
                            "事件接口步骤缺少 operationCode");
                }
                execute.setConfigType(normalize(request.getConfigType()));
                execute.setConfigId(request.getConfigId());
                execute.setTargetType(
                        StringUtils.hasText(request.getTargetType())
                                ? normalize(request.getTargetType())
                                : "OWNER");
                execute.setTargetKey(request.getTargetKey());
                execute.setReleaseId(chain.releaseId());
                execute.setReleaseVersion(chain.releaseVersion());
                execute.setEntityCode(chain.entityCode());
                execute.setListKey(chain.listKey());
                boolean formButton = UiDataSourceUsages.FORM_BUTTON_CLICK.equals(
                        normalize(request.getEventCode()));
                boolean listButton = isListButtonEvent(request);
                execute.setInput(formButton
                        ? trustedFormButtonInput(
                                stringMap(inputMap), request)
                        : listButton
                                ? trustedListButtonInput(
                                        stringMap(inputMap), request)
                                : stringMap(inputMap));
                execute.setContext(runtimeContext(request, state));
                Integer pageNum = positiveInteger(
                        mutableInput(state).get("pageNum"));
                Integer pageSize = positiveInteger(
                        mutableInput(state).get("pageSize"));
                execute.setPageNum(pageNum);
                execute.setPageSize(pageSize == null
                        ? null : Math.min(200, pageSize));
                execute.setServerIdempotencyKey(
                        request.getServerIdempotencyKey());
                execute.setServerPinnedRelease(
                        StringUtils.hasText(
                                request.getReleaseResolutionToken()));
                if (formButton) {
                    execute.setServerPinnedRelease(true);
                    execute.setServerRecordId(request.getRecordId());
                    execute.setServerFormMode(
                            request.getServerAuthorizedMode());
                    execute.setServerTaskId(request.getServerTaskId());
                    execute.setServerProcessInstanceId(
                            request.getServerProcessInstanceId());
                    execute.setServerBindingOwnerType(text(
                            step.get("bindingOwnerType")));
                    execute.setServerBindingOwnerId(text(
                            step.get("bindingOwnerId")));
                    execute.setServerBindingTargetType(text(
                            step.get("bindingTargetType")));
                    execute.setServerBindingTargetKey(text(
                            step.get("bindingTargetKey")));
                    raw = executeProviderStep(
                            step, request, chain, serviceId, execute);
                } else {
                    // 非表单按钮事件保留既有 WRITE/READ Provider 契约，避免影响
                    // DATA_CREATE/UPDATE、列表按钮等已发布执行链。
                    raw = dataSourceService.executeOperation(
                            serviceId,
                            execute.getOperationCode(),
                            execute);
                }
            } else {
                raw = state;
            }
            Object outputMapping = step.get("outputMapping");
            Map<String, Object> mappingSource =
                    StringUtils.hasText(serviceId)
                            ? Map.of(
                                    "data",
                                    raw == null ? Map.of() : raw,
                                    "response",
                                    raw == null ? Map.of() : raw,
                                    "state",
                                    state)
                            : state;
            Object mapped = valueMapper.apply(
                    outputMapping,
                    mappingSource,
                    raw);
            if (outputMapping instanceof List<?> mappings
                    && !mappings.isEmpty()
                    && mapped instanceof Map<?, ?> data) {
                Map<String, Object> effect = new LinkedHashMap<>();
                effect.put("type", "FIELD_MAPPING");
                effect.put("data", stringMap(data));
                effect.put("mappings", mappings);
                result.getEffects().add(effect);
            }
            collectEnvelope(mapped, result);
            trace(result, stepLabel(step), "SUCCESS", null);
            return mapped;
        } catch (RuntimeException exception) {
            boolean formButton = UiDataSourceUsages.FORM_BUTTON_CLICK.equals(
                    normalize(request == null
                            ? null : request.getEventCode()));
            trace(
                    result,
                    stepLabel(step),
                    "FAILED",
                    formButton
                            ? "表单按钮步骤执行失败"
                            : exception.getMessage());
            RuntimeException safeFailure = safeFailClosedFormButtonError(
                    request, exception);
            if (safeFailure != null) {
                throw safeFailure;
            }
            if ("CONTINUE".equals(failurePolicy)) {
                return null;
            }
            if ("EMPTY".equals(failurePolicy)) {
                return Map.of();
            }
            if (formButton) {
                throw new BusinessConflictException(
                        "UI_EVENT_FORM_BUTTON_STEP_FAILED",
                        "表单按钮操作执行失败，请稍后重试");
            }
            throw exception;
        }
    }

    /**
     * 覆盖映射结果中可能由客户端伪造的顶层表单身份。
     * 业务字段容器（例如 input.form）不改写；Provider 需要 recordId/mode 时只能
     * 收到此前已通过按钮鉴权的值，强类型上下文同样读取独立服务端字段。
     */
    private Map<String, Object> trustedFormButtonInput(
            Map<String, Object> mappedInput,
            UiEventExecuteRequest request) {
        Map<String, Object> result = new LinkedHashMap<>(mappedInput);
        result.replaceAll((key, value) -> {
            String normalized = key == null ? ""
                    : key.replace("_", "")
                            .replace("-", "")
                            .toLowerCase(Locale.ROOT);
            if ("recordid".equals(normalized)) {
                return request.getRecordId();
            }
            if ("mode".equals(normalized)) {
                return request.getServerAuthorizedMode();
            }
            return value;
        });
        return result;
    }

    /**
     * 每个 Provider 步骤都重新覆盖列表按钮保留身份。
     *
     * <p>inputMapping 本身属于发布配置，但映射源仍含客户端业务字段；因此映射
     * 后也必须删除大小写及分隔符变体，再从执行前鉴权得到的 canonical input
     * 注入发布按钮、权威行和选择集，防止已鉴权 A 行被映射成 B 行。</p>
     */
    private Map<String, Object> trustedListButtonInput(
            Map<String, Object> mappedInput,
            UiEventExecuteRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        mappedInput.forEach((key, value) -> {
            if (!reservedListButtonInputKey(key)) {
                result.put(key, value);
            }
        });
        Map<String, Object> trusted = request.getInput() == null
                ? Map.of() : request.getInput();
        for (String key : List.of(
                "button", "row", "currentRow", "selectedRows", "records",
                "recordId", "selectedIds", "entityCode", "listKey",
                "eventCode", "targetType", "targetKey")) {
            result.put(key, immutableBusinessCopy(trusted.get(key)));
        }
        result.put("record", immutableBusinessCopy(trusted.get("row")));
        result.put("selection", immutableBusinessCopy(
                request.getSelection()));
        return result;
    }

    private boolean isListButtonEvent(UiEventExecuteRequest request) {
        String eventCode = normalize(request == null
                ? null : request.getEventCode());
        return request != null
                && "LIST".equals(normalize(request.getConfigType()))
                && Set.of(
                UiDataSourceUsages.ROW_BUTTON_CLICK,
                UiDataSourceUsages.TOOLBAR_BUTTON_CLICK)
                .contains(eventCode);
    }

    private Map<String, Object> initialState(
            UiEventExecuteRequest request,
            Object selection) {
        Map<String, Object> input = request.getInput() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(request.getInput());
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("input", input);
        state.put("data", input);
        state.put("context", providerClientContext(request));
        state.put("selection", selection);
        state.put("recordId", request.getRecordId());
        state.put("selectedIds", request.getSelectedIds() == null
                ? List.of() : request.getSelectedIds());
        return state;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mutableInput(
            Map<String, Object> state) {
        return (Map<String, Object>) state.get("input");
    }

    private Map<String, Object> runtimeContext(
            UiEventExecuteRequest request,
            Map<String, Object> state) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.putAll(providerClientContext(request));
        if (UiDataSourceUsages.FORM_BUTTON_CLICK.equals(normalize(
                request.getEventCode()))) {
            // mode 与 recordId 都来自刚完成的服务端按钮鉴权，不能继续沿用
            // 客户端 context 或 inputMapping 中的同名值。
            context.put("mode", request.getServerAuthorizedMode());
        }
        context.put("eventCode", normalize(request.getEventCode()));
        context.put("targetType", normalize(request.getTargetType()));
        context.put("targetKey", request.getTargetKey());
        context.put("recordId", request.getRecordId());
        context.put("selectedIds", request.getSelectedIds() == null
                ? List.of() : request.getSelectedIds());
        context.put("eventState",
                UiDataSourceUsages.FORM_BUTTON_CLICK.equals(normalize(
                        request.getEventCode()))
                        ? providerEventState(state)
                        : state);
        return context;
    }

    /**
     * Provider 已通过独立 input 接收映射后的业务值；可信 context 不再重复嵌套
     * 原始 input/context/result，以免动态字段名被误认为身份元数据或被实现方错用。
     */
    private Map<String, Object> providerEventState(
            Map<String, Object> state) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("recordId", state.get("recordId"));
        summary.put("selectedIds", state.getOrDefault(
                "selectedIds", List.of()));
        summary.put("selectionPresent", state.get("selection") != null);
        summary.put("resultPresent", state.get("result") != null);
        return summary;
    }

    /**
     * 为 FORM_BUTTON_CLICK 构造可交给 Provider 的客户端上下文。
     *
     * <p>主执行入口已经把 FORM_BUTTON context 重建为服务端正向白名单；这里的
     * 保留键过滤是内部调用的二次防线。Provider 的完整身份仍只能从
     * authorization/invocation context 读取。</p>
     */
    private Map<String, Object> providerClientContext(
            UiEventExecuteRequest request) {
        if (request.getContext() == null
                || request.getContext().isEmpty()) {
            return Map.of();
        }
        if (!UiDataSourceUsages.FORM_BUTTON_CLICK.equals(normalize(
                request.getEventCode()))) {
            return new LinkedHashMap<>(request.getContext());
        }
        Map<String, Object> context = new LinkedHashMap<>();
        request.getContext().forEach((key, value) -> {
            if (!UiDataSourceExecutionAccessService
                    .isReservedFormButtonRequestKey(key)) {
                context.put(key, value);
            }
        });
        return context;
    }

    private void collectEnvelope(
            Object value,
            UiEventExecutionResult result) {
        if (!(value instanceof Map<?, ?> map)) {
            return;
        }
        Object message = map.get("message");
        if (message != null && StringUtils.hasText(String.valueOf(message))) {
            result.setMessage(String.valueOf(message));
        }
        if (map.get("effects") instanceof List<?> effects) {
            effects.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> stringMap((Map<?, ?>) item))
                    .forEach(result.getEffects()::add);
        }
    }

    private List<Map<String, Object>> steps(
            List<Map<String, Object>> source,
            String strategy) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> step : source) {
            if (strategy.equals(normalize(text(
                    step.getOrDefault("strategy", "BEFORE"))))) {
                result.add(step);
            }
        }
        return result;
    }

    private void trace(
            UiEventExecutionResult result,
            String step,
            String status,
            String message) {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("step", step);
        trace.put("status", status);
        if (StringUtils.hasText(message)) {
            trace.put("message", message);
        }
        result.getTrace().add(trace);
    }

    private String stepLabel(Map<String, Object> step) {
        return firstText(
                step.get("name"),
                step.get("operationCode"),
                step.get("serviceId"),
                "MAPPING");
    }

    private Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) ->
                result.put(String.valueOf(key), value));
        return result;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            if (value != null
                    && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private Integer positiveInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        try {
            return Math.max(
                    1,
                    Integer.parseInt(String.valueOf(value)));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Integer strictPositiveInteger(Object value) {
        Integer result;
        if (value instanceof Number number
                && number.doubleValue() == number.intValue()) {
            result = number.intValue();
        } else {
            try {
                result = value == null
                        ? null : Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                result = null;
            }
        }
        return result != null && result > 0 ? result : null;
    }

    /**
     * 执行表单按钮发布步骤中的 READ 接口操作。新发布必须使用完整的固定定义；
     * 完全没有固定标记的历史发布仅在本次请求内冻结当前定义，并仍强制
     * READ 校验。部分固定或未知版本均视为制品损坏，不得降级。
     */
    private Object executeProviderStep(
            Map<String, Object> step,
            UiEventExecuteRequest request,
            UiEventBindingService.ResolvedEventChain chain,
            String serviceId,
            UiDataSourceExecuteRequest execute) {
        Object rawVersion = step.get("operationSnapshotVersion");
        boolean hasSnapshotVersion = step.containsKey(
                "operationSnapshotVersion");
        boolean hasPinnedField = PINNED_OPERATION_FIELDS.stream()
                .anyMatch(step::containsKey);
        UiDataSourceService.PublishedOperationSnapshot operation;
        if (!hasSnapshotVersion) {
            if (hasPinnedField) {
                throw invalidPinnedOperation(
                        "历史事件步骤包含不完整的固定操作字段");
            }
            // 兼容标记上线前的发布制品：当次请求只读冻结，
            // 避免直接调用可能已漂移为 WRITE 的普通 Provider。
            operation = dataSourceService.freezeOperation(
                    serviceId, execute.getOperationCode());
            log.warn(
                    "UI历史事件步骤未固定接口定义，本次按当前READ定义兼容执行: configType={}, configId={}, releaseId={}, releaseVersion={}, eventCode={}, targetKey={}, serviceId={}, operationCode={}",
                    LogValue.safe(request.getConfigType()),
                    LogValue.safe(request.getConfigId()),
                    LogValue.safe(chain.releaseId()),
                    chain.releaseVersion(),
                    LogValue.safe(request.getEventCode()),
                    LogValue.safe(request.getTargetKey()),
                    LogValue.safe(serviceId),
                    LogValue.safe(execute.getOperationCode()));
        } else {
            Integer version = strictPositiveInteger(rawVersion);
            if (!Integer.valueOf(OPERATION_SNAPSHOT_VERSION)
                    .equals(version)
                    || !PINNED_OPERATION_FIELDS.stream()
                            .allMatch(step::containsKey)
                    || !PINNED_BINDING_IDENTITY_FIELDS.stream()
                            .allMatch(step::containsKey)) {
                throw invalidPinnedOperation(
                        "已发布事件步骤的固定操作版本或字段不完整");
            }
            operation = new UiDataSourceService.PublishedOperationSnapshot(
                    serviceId,
                    firstText(step.get("sourceCode")),
                    strictPositiveInteger(step.get("serviceRevision")),
                    execute.getOperationCode(),
                    firstText(step.get("executableSnapshot")),
                    firstText(step.get("definitionHash")));
        }
        // 先校验外层绑定身份，再从独立哈希保护的定义执行。
        dataSourceService.validatePinnedReadOperation(
                operation.document(),
                operation.hash(),
                operation.serviceId(),
                operation.sourceCode(),
                operation.serviceRevision(),
                operation.operationCode(),
                normalize(request.getConfigType()));
        return dataSourceService.executePinnedOperation(
                operation.document(),
                operation.hash(),
                execute,
                chain.snapshot(),
                chain.effectiveContentHash());
    }

    /** 固定制品、可信来源和授权错误不能被事件步骤的降级策略吞掉。 */
    private RuntimeException safeFailClosedFormButtonError(
            UiEventExecuteRequest request,
            RuntimeException exception) {
        if (!UiDataSourceUsages.FORM_BUTTON_CLICK.equals(normalize(
                request == null ? null : request.getEventCode()))) {
            return null;
        }
        // 权限错误属于安全边界，未来新增错误码也不能被 CONTINUE/EMPTY 降级；
        // Provider 原异常消息可能含内部地址或密钥，禁止原样穿透公共响应。
        if (exception instanceof ForbiddenException
                || exception instanceof SecurityException) {
            String errorCode = exception instanceof BusinessForbiddenException value
                    && StringUtils.hasText(value.getErrorCode())
                    ? value.getErrorCode()
                    : "UI_EVENT_FORM_BUTTON_FORBIDDEN";
            return new BusinessForbiddenException(
                    errorCode,
                    "无权执行表单按钮");
        }
        String errorCode = exception instanceof BusinessConflictException value
                ? value.getErrorCode()
                : null;
        boolean protectedConflict = StringUtils.hasText(errorCode)
                && (errorCode.startsWith("UI_EVENT_PINNED_")
                || errorCode.startsWith("UI_EVENT_EFFECTIVE_SNAPSHOT_")
                || errorCode.startsWith("UI_INTERFACE_PINNED_")
                || errorCode.startsWith("UI_INTERFACE_PROVIDER_IDENTITY_")
                || errorCode.startsWith("UI_DATA_SOURCE_TRUSTED_")
                || errorCode.startsWith("UI_DATA_SOURCE_PINNED_")
                || "UI_DATA_SOURCE_PUBLISHED_BINDING_REQUIRED".equals(
                        errorCode)
                || "UI_DATA_SOURCE_EXECUTION_CONTEXT_SPOOFED".equals(
                        errorCode)
                || "UI_DATA_SOURCE_SCOPE_MISMATCH".equals(errorCode)
                || FORM_BUTTON_AUTHORIZATION_ERRORS.contains(errorCode));
        return protectedConflict
                ? new BusinessConflictException(
                        errorCode,
                        "表单按钮发布配置不可用，请刷新后重试")
                : null;
    }

    /** 非法或超长客户端 requestId 不进入审计与日志。 */
    private String auditRequestId(UiEventExecuteRequest request) {
        return request == null
                ? null
                : UiEventExecutionReceiptService.validRequestIdOrNull(
                        request.getRequestId());
    }

    private BusinessConflictException invalidPinnedOperation(
            String message) {
        return new BusinessConflictException(
                "UI_EVENT_PINNED_OPERATION_INVALID", message);
    }
}
