package com.workflow.entity.excel.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.core.error.ForbiddenException;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.excel.config.EntityExcelConfigurationService;
import com.workflow.entity.excel.model.ExcelEventAction;
import com.workflow.entity.excel.model.ExcelResolvedScheme;
import com.workflow.entity.form.application.model.ResolvedEntityFormRelease;
import com.workflow.entity.list.application.EntityDataListConfigService;
import com.workflow.entity.list.application.EntityListFixedFilters;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityListActionConfigService;
import com.workflow.entity.permission.application.EntityPermissionAction;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.entity.ui.application.UiEventBindingService;
import com.workflow.entity.ui.application.UiEventOriginContext;
import com.workflow.entity.ui.application.UiEventRuntimeService;
import com.workflow.entity.ui.application.UiInterfaceExtensionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 默认按钮和自定义事件共用的执行计划解析器。先鉴权真实入口，再解析方案；
 * 实际文件请求重新鉴权并核对摘要，避免页面打开后配置或权限变化被绕过。
 */
@Service
@RequiredArgsConstructor
public class EntityExcelRuntimeResolver {
    private final EntityExcelConfigurationService configurations;
    private final ExcelActionTokenService tokens;
    private final EntityActionCapabilityService capabilities;
    private final EntityListActionConfigService buttons;
    private final EntityDataListConfigService lists;
    private final EntityListConfigMapper listMapper;
    private final EntityDataDynamicService data;
    private final EntityPublishedSnapshotService entities;
    private final ObjectProvider<UiEventRuntimeService> events;
    private final ObjectProvider<UiEventBindingService> bindings;
    private final ObjectProvider<UiConfigReleaseService> releases;
    private final ObjectProvider<UiInterfaceExtensionService> interfaces;
    private final ObjectMapper mapper;

    /** 默认入口只允许平台已知动作，不能由客户端选择另一套方案。 */
    public Map<String, Object> prepare(ExcelPrepareRequest request) {
        if (request == null || !"LIST".equals(request.ownerType()))
            throw new IllegalArgumentException("默认Excel入口需要列表来源");
        UiEventExecuteRequest origin = new UiEventExecuteRequest();
        origin.setConfigType("LIST"); origin.setConfigId(request.ownerId());
        origin.setEntityCode(request.entityCode()); origin.setListKey(request.listKey());
        origin.setTargetType("BUTTON"); origin.setTargetKey(request.buttonCode());
        origin.setEventCode("TOOLBAR_BUTTON_CLICK");
        origin.setConfigHash(request.configHash()); origin.setRuntimeContext(request.runtimeContext());
        // 只有“导出所选”按选中行评估按钮条件；导入/全部导出不能借勾选某行改变工具栏授权上下文。
        origin.setSelectedIds("exportSelected".equals(request.buttonCode()) ? selectedIds(request.recordIds()) : List.of());
        origin.setInput(Map.of("filters", request.filters() == null ? Map.of() : request.filters()));
        EntityListConfig list = authorizeDefault(origin);
        String operation = "import".equals(request.buttonCode()) ? "IMPORT" : "EXPORT";
        String scope = "exportAll".equals(request.buttonCode()) ? "FILTERED" : "SELECTED";
        return issue(origin, "BUILT_IN", tokens.hash(list), operation, scope, "DEFAULT", null, Map.of());
    }

    /** 仅由已执行的事件 Provider 调用；来源来自线程内已鉴权事件，不接受客户端伪造。 */
    public Map<String, Object> prepareCustom(String extensionId, Map<String, Object> configuration) {
        var current = UiEventOriginContext.current();
        if (current == null || current.chain().steps().stream().noneMatch(step ->
                Objects.equals(extensionId, step.get("extensionId"))
                        && "REPLACE".equals(step.get("strategy")) && step.get("condition") == null))
            throw new ForbiddenException("Excel操作必须绑定按钮事件的无条件主处理步骤");
        return prepareConfiguration(current, configuration);
    }

    /**
     * 内置按钮动作的唯一准备入口。只接受当前已鉴权发布链中的步骤，重新从该链
     * 读取静态参数，客户端 input 或 BEFORE 步骤输出不能切换方案和导出范围。
     * 返回单个 EXCEL_ACTION 效果，由事件引擎统一交付客户端。
     */
    public Map<String, Object> prepareBoundAction(Map<String, Object> step) {
        var current = UiEventOriginContext.current();
        if (current == null || !Objects.equals(current.chain().entityCode(), current.request().getEntityCode()))
            throw new ForbiddenException("Excel动作缺少可信按钮上下文");
        var publishedStep = current.chain().steps().stream()
                .filter(ExcelEventAction::isExcelStep).filter(candidate -> Objects.equals(candidate, step))
                .findFirst().orElseThrow(() -> new ForbiddenException("Excel动作必须来自已发布按钮事件"));
        var request = current.request();
        ExcelEventAction.validateStep(publishedStep, request.getConfigType(), request.getTargetType(), request.getEventCode());
        return prepareConfiguration(current, ExcelEventAction.configuration(publishedStep));
    }

    /** 新内置动作与历史 Provider 共享鉴权、方案解析及签名，历史绑定无需重新发布。 */
    private Map<String, Object> prepareConfiguration(UiEventOriginContext.Origin current, Map<String, Object> configuration) {
        var request = current.request();
        if (!Set.of("FORM_BUTTON_CLICK", "TOOLBAR_BUTTON_CLICK", "ROW_BUTTON_CLICK").contains(request.getEventCode()))
            throw new ForbiddenException("此事件不能准备Excel操作");
        String operation = text(configuration.get("operation"));
        if (!Set.of("IMPORT", "EXPORT", "TEMPLATE").contains(operation))
            throw new IllegalArgumentException("Excel操作无效");
        Map<String, Object> reference = map(configuration.get("schemeRef"));
        String mode = text(reference.get("mode"));
        if (!Set.of("DEFAULT", "EXPLICIT").contains(mode)) throw new IllegalArgumentException("方案引用方式无效");
        String scope = text(configuration.get("dataScope"));
        if (scope.isBlank()) scope = "FORM".equals(request.getConfigType()) || "ROW_BUTTON_CLICK".equals(request.getEventCode())
                ? "CURRENT_RECORD" : "SELECTED";
        if ("ROW_BUTTON_CLICK".equals(request.getEventCode()) && "IMPORT".equals(operation))
            throw new IllegalArgumentException("行按钮不支持批量导入，请使用工具栏按钮");
        if ("EXPORT".equals(operation) && "ROW_BUTTON_CLICK".equals(request.getEventCode()) && !"CURRENT_RECORD".equals(scope))
            throw new IllegalArgumentException("行按钮仅支持导出当前记录");
        // 表单批量范围必须拥有独立的已发布列表及选择来源；仅填 listKey 不能扩大范围。
        Map<String, Object> selectionSource = Map.of();
        if ("EXPORT".equals(operation) && "FORM".equals(request.getConfigType()) && !"CURRENT_RECORD".equals(scope)) {
            if (!"LIST_DIALOG".equals(map(configuration.get("selectionSource")).get("type"))
                    || text(configuration.get("listKey")).isBlank())
                throw new IllegalArgumentException("表单批量导出需要配置已发布列表及LIST_DIALOG选择来源");
            var list = selectionList(request.getEntityCode(), text(configuration.get("listKey")));
            selectionSource = new LinkedHashMap<>();
            selectionSource.put("type", "LIST_DIALOG"); selectionSource.put("entityCode", request.getEntityCode());
            selectionSource.put("listKey", list.getListKey()); selectionSource.put("listId", list.getId());
            selectionSource.put("configHash", list.getConfigHash()); selectionSource.put("runtimeContext", list.getRuntimeContext());
            selectionSource.put("sourceHash", tokens.hash(list));
            if ("FILTERED".equals(scope)) requireDatabaseList(list);
        }
        return issue(request, "CUSTOM", chainHash(current.chain()), operation, scope, mode,
                text(reference.get("schemeCode")), selectionSource);
    }

    private Map<String, Object> issue(UiEventExecuteRequest source, String entryType, String sourceHash,
            String operation, String scope, String mode, String schemeCode, Map<String, Object> selectionSource) {
        UiEventExecuteRequest origin = copyOrigin(source);
        if ("LIST".equals(origin.getConfigType())) resolveList(origin);
        requireOperation(origin.getEntityCode(), operation, scope);
        if ("DEFAULT".equals(mode) && text(schemeCode).isBlank()) schemeCode = null;
        var scheme = configurations.resolvePublished(origin.getEntityCode(), operation, mode, schemeCode);
        var entity = entities.getLatestPinnedByEntityId(scheme.entityId());
        requireReadableFields(scheme, entity, operation);
        String entityHash = entity.schemaHash();
        var form = "EXPORT".equals(operation) ? null : writeForm(scheme);
        String formHash = form == null ? "" : form.effectiveContentHash();
        if ("EXPORT".equals(operation) && selectionSource.isEmpty()) requireExportRange(origin, scope);
        if ("EXPORT".equals(operation) && !Set.of("CURRENT_RECORD", "SELECTED", "FILTERED").contains(scope))
            throw new IllegalArgumentException("导出范围无效");
        var claims = new ExcelActionTokenService.Claims(1, userId(), Instant.now().getEpochSecond() + 900,
                entryType, origin, sourceHash, operation, scope, mode, scheme.schemeCode(),
                scheme.contentHash(), scheme.defaultHash(), entityHash, formHash,
                "IMPORT".equals(operation) ? scheme.document().importConfig().precheckEnabled()
                        ? List.of("IMPORT", "CHECK", "TEMPLATE") : List.of("IMPORT", "TEMPLATE") : List.of(operation), selectionSource);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "EXCEL_ACTION"); result.put("operation", operation);
        result.put("actionContextToken", tokens.issue(claims)); result.put("schemeName", scheme.document().name());
        result.put("templateFileName", safeName(scheme.document().name()) + "_导入模板.xlsx");
        result.put("dataScope", scope);
        if ("IMPORT".equals(operation)) {
            var options = scheme.document().importConfig();
            result.put("precheckEnabled", options.precheckEnabled());
            result.put("validateHeader", options.validateHeader());
            result.put("stopAtEmptyRow", options.stopAtEmptyRow());
            result.put("batchSize", options.batchSize());
            result.put("maxFileSizeMb", options.maxFileSizeMb());
            result.put("returnUnimported", options.returnUnimported());
            result.put("failurePolicy", options.failurePolicy());
            result.put("transactionPolicy", options.transactionPolicy());
        }
        if (!selectionSource.isEmpty()) {
            var publicSource = new LinkedHashMap<>(selectionSource); publicSource.remove("sourceHash");
            result.put("selectionSource", publicSource);
        }
        return result;
    }

    /** 文件受理前验明来源、操作、方案和依赖摘要；返回后续各阶段共享的固定执行计划。 */
    public Plan resolve(String token, String operation) {
        var claims = tokens.verify(token, operation);
        var origin = claims.origin();
        EntityListConfig list = null;
        String sourceHash;
        if ("BUILT_IN".equals(claims.entryType())) {
            list = authorizeDefault(origin);
            sourceHash = tokens.hash(list);
        } else if ("CUSTOM".equals(claims.entryType())) {
            sourceHash = chainHash(events.getObject().authorizeExcelOrigin(origin));
            if ("LIST".equals(origin.getConfigType())) list = resolveList(origin);
        } else throw new ForbiddenException("Excel入口类型无效");
        requireOperation(origin.getEntityCode(), operation, claims.dataScope());
        var scheme = configurations.resolvePublished(origin.getEntityCode(), claims.operation(),
                claims.referenceMode(), "DEFAULT".equals(claims.referenceMode()) ? null : claims.schemeCode());
        // UI 隐藏只是引导，旧令牌或手写请求也不能调用已关闭的预校验入口。
        if ("CHECK".equals(operation) && !scheme.document().importConfig().precheckEnabled())
            throw new IllegalArgumentException("当前 Excel 方案未启用预校验");
        var entity = entities.getLatestPinnedByEntityId(scheme.entityId());
        requireReadableFields(scheme, entity, operation);
        var form = "EXPORT".equals(operation) ? null : writeForm(scheme);
        if (!Objects.equals(sourceHash, claims.sourceHash()) || !Objects.equals(scheme.contentHash(), claims.schemeHash())
                || !Objects.equals(scheme.defaultHash(), claims.defaultHash()) || !Objects.equals(entity.schemaHash(), claims.entityHash())
                || !Objects.equals(form == null ? "" : form.effectiveContentHash(), claims.writeFormHash()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "EXCEL_CONFIGURATION_CHANGED: 配置已变化，请重新打开并下载模板");
        if (hasSelectionDialog(claims)) {
            list = selectionList(scheme.entityCode(), text(claims.selectionSource().get("listKey")));
            if (!Objects.equals(tokens.hash(list), claims.selectionSource().get("sourceHash")))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "EXCEL_CONFIGURATION_CHANGED: 导出列表配置已变化");
            if ("FILTERED".equals(claims.dataScope())) requireDatabaseList(list);
        } else if ("EXPORT".equals(operation)) requireExportRange(origin, claims.dataScope());
        return new Plan(claims, scheme, list, entity, form);
    }

    private EntityListConfig authorizeDefault(UiEventExecuteRequest origin) {
        if (!"LIST".equals(origin.getConfigType()) || !"BUTTON".equals(origin.getTargetType())
                || !"TOOLBAR_BUTTON_CLICK".equals(origin.getEventCode())
                || !Set.of("import", "exportSelected", "exportAll").contains(text(origin.getTargetKey())))
            throw new ForbiddenException("无效的默认Excel按钮");
        EntityListConfig list = resolveList(origin);
        var button = buttons.resolveToolbarButtons(list, origin.getEntityCode()).stream()
                .filter(item -> Objects.equals(item.get("key"), origin.getTargetKey()) && "built-in".equals(item.get("type")))
                .findFirst().orElseThrow(() -> new ForbiddenException("默认Excel按钮不存在"));
        capabilities.requirePublishedListButton(origin.getEntityCode(), origin.getTargetKey(), button,
                origin.getSelectedIds() == null || origin.getSelectedIds().isEmpty() ? List.of()
                        : origin.getSelectedIds().stream().map(id -> data.findAccessibleById(origin.getEntityCode(), id, list.getListKey())).toList());
        return list;
    }

    private EntityListConfig resolveList(UiEventExecuteRequest origin) {
        var declared = listMapper.selectById(origin.getConfigId());
        if (declared == null || !Objects.equals(declared.getEntityCode(), origin.getEntityCode())
                || !Objects.equals(declared.getListKey(), origin.getListKey())) throw new ForbiddenException("Excel列表来源不匹配");
        var list = lists.findListConfig(origin.getEntityCode(), origin.getListKey(), origin.getConfigHash(), origin.getRuntimeContext());
        if (list == null || !Objects.equals(list.getId(), origin.getConfigId())) throw new ForbiddenException("Excel列表未发布");
        requireListAccess(list);
        return list;
    }

    private void requireExportRange(UiEventExecuteRequest origin, String scope) {
        if (!Set.of("CURRENT_RECORD", "SELECTED", "FILTERED").contains(scope)) throw new IllegalArgumentException("导出范围无效");
        if ("CURRENT_RECORD".equals(scope)) {
            if (text(origin.getRecordId()).isBlank()) throw new IllegalArgumentException("请先保存记录再导出");
            data.findAccessibleById(origin.getEntityCode(), origin.getRecordId(), origin.getListKey());
            if ("LIST".equals(origin.getConfigType())) {
                var list = resolveList(origin);
                var filters = effectiveFilters(origin, list);
                // 行级数据权限不包含列表固定条件；当前行导出也必须落在用户看到的列表来源范围中。
                var scoped = lists.findExportBatchWithResolvedConfig(origin.getEntityCode(), list.getListKey(), list,
                        filters, List.of(origin.getRecordId()), null);
                if (scoped.records().stream().noneMatch(row -> Objects.equals(row.getId(), origin.getRecordId())))
                    throw new ForbiddenException("记录不在当前列表筛选范围内");
            }
        }
        if ("SELECTED".equals(scope) && (origin.getSelectedIds() == null || origin.getSelectedIds().isEmpty()))
            throw new IllegalArgumentException("请先选择需要导出的记录");
        if (origin.getSelectedIds() != null && origin.getSelectedIds().size() > 5000)
            throw new IllegalArgumentException("一次最多导出5000条选中记录");
        if ("SELECTED".equals(scope)) {
            for (String id : selectedIds(origin.getSelectedIds()))
                data.findAccessibleById(origin.getEntityCode(), id, origin.getListKey());
        }
        if ("FILTERED".equals(scope)) {
            if (!"LIST".equals(origin.getConfigType())) throw new ForbiddenException("筛选导出缺少已发布列表来源");
            var list = resolveList(origin);
            requireDatabaseList(list);
        }
    }

    private EntityListConfig selectionList(String entityCode, String listKey) {
        var list = lists.findListConfig(entityCode, listKey);
        if (list == null || !Objects.equals(entityCode, list.getEntityCode()))
            throw new IllegalArgumentException("导出选择来源必须是当前实体的已发布列表");
        requireListAccess(list);
        return list;
    }

    /** 导出/导入动作权限不能替代进入来源列表的标准权限和独立访问权限。 */
    private void requireListAccess(EntityListConfig list) {
        capabilities.requireStandardPermission(list.getEntityCode(), EntityPermissionAction.LIST);
        if (!text(list.getAccessPermissionCode()).isBlank() && !PermissionUtil.hasPermission(list.getAccessPermissionCode()))
            throw new ForbiddenException("没有权限访问Excel来源列表");
    }

    private void requireDatabaseList(EntityListConfig list) {
        var query = new UiEventExecuteRequest();
        query.setConfigType("LIST"); query.setConfigId(list.getId()); query.setEntityCode(list.getEntityCode());
        query.setListKey(list.getListKey()); query.setReleaseId(list.getActiveReleaseId()); query.setReleaseVersion(list.getPublishedVersion());
        query.setConfigHash(list.getConfigHash()); query.setRuntimeContext(list.getRuntimeContext()); query.setEventCode("LIST_LOAD"); query.setTargetType("OWNER");
        var chain = bindings.getObject().resolvePublished(query);
        if (!text(list.getQueryProviderCode()).isBlank() || !text(list.getQueryInterfaceExtensionId()).isBlank()
                || chain.steps().stream().anyMatch(step -> "REPLACE".equals(step.get("strategy"))))
            throw new IllegalArgumentException("当前自定义数据源尚未配置受控Excel导出适配器");
    }

    /** 表单绑定的列表弹窗在此显式提交选择集；其固定条件、来源及授权不能随请求改变。 */
    public Plan withExportSelection(Plan plan, List<String> recordIds, Map<String, Object> filters) {
        if (!hasSelectionDialog(plan.claims())) return plan;
        var ids = selectedIds(recordIds);
        if (ids.size() > 5000 || ("SELECTED".equals(plan.claims().dataScope()) && ids.isEmpty()))
            throw new IllegalArgumentException("请选择1至5000条需要导出的记录");
        for (String id : ids) data.findAccessibleById(plan.scheme().entityCode(), id, plan.list().getListKey());
        var c = plan.claims(); var source = copyOrigin(c.origin());
        source.setSelectedIds(ids); source.setInput(Map.of("filters", filters == null ? Map.of() : filters));
        var bound = new ExcelActionTokenService.Claims(c.version(), c.userId(), c.expiresAt(), c.entryType(), source,
                c.sourceHash(), c.operation(), c.dataScope(), c.referenceMode(), c.schemeCode(), c.schemeHash(), c.defaultHash(),
                c.entityHash(), c.writeFormHash(), c.allowedOperations(), c.selectionSource());
        return new Plan(bound, plan.scheme(), plan.list(), plan.entity(), plan.writeForm());
    }

    public static boolean hasSelectionDialog(ExcelActionTokenService.Claims claims) {
        return claims.selectionSource() != null && "LIST_DIALOG".equals(claims.selectionSource().get("type"));
    }

    /** 固定条件与用户条件取交集，任何导出都不能通过省略前端参数扩大列表范围。 */
    public Map<String, Object> filters(Plan plan) {
        return effectiveFilters(plan.claims().origin(), plan.list());
    }

    private Map<String, Object> effectiveFilters(UiEventExecuteRequest origin, EntityListConfig list) {
        Map<String, Object> filters = map(origin.getInput() == null ? null : origin.getInput().get("filters"));
        Map<String, Object> fixed = list == null ? Map.of() : EntityListFixedFilters.normalize(readMap(list.getFixedFilterConfig()));
        if (EntityListFixedFilters.conflictsWithFixedEquality(filters, fixed))
            throw new IllegalArgumentException("筛选条件与列表固定条件冲突，请重新筛选后导出");
        return EntityListFixedFilters.apply(filters, fixed);
    }

    public String originKey(Plan plan) {
        var origin = plan.claims().origin();
        return tokens.hash(List.of(plan.claims().entryType(), origin.getConfigType(), origin.getConfigId(), origin.getTargetKey()));
    }

    private void requireOperation(String entityCode, String operation, String scope) {
        if ("EXPORT".equals(operation)) {
            capabilities.requireStandardPermission(entityCode,
                    "FILTERED".equals(scope) ? EntityPermissionAction.EXPORT_ALL : EntityPermissionAction.EXPORT);
            if ("CURRENT_RECORD".equals(scope)) capabilities.requireStandardPermission(entityCode, EntityPermissionAction.VIEW);
        }
        else {
            capabilities.requireStandardPermission(entityCode, EntityPermissionAction.IMPORT);
            capabilities.requireStandardPermission(entityCode, EntityPermissionAction.CREATE);
        }
    }

    private ResolvedEntityFormRelease writeForm(ExcelResolvedScheme scheme) {
        return scheme.writeFormId() == null ? null : releases.getObject().resolveRuntimeFormRelease(scheme.writeFormId());
    }

    private String chainHash(UiEventBindingService.ResolvedEventChain chain) {
        // 列表旧事件未必固定接口定义；把实时接口摘要纳入绑定，防止接口改动后旧令牌继续执行。
        var definitions = chain.steps().stream().filter(step -> step.get("extensionId") != null)
                .map(step -> interfaces.getObject().freezeOperation(String.valueOf(step.get("extensionId")),
                        step.get("operationCode") == null ? null : String.valueOf(step.get("operationCode"))).hash()).toList();
        return tokens.hash(List.of(chain.snapshot(), chain.steps(), definitions));
    }

    private UiEventExecuteRequest copyOrigin(UiEventExecuteRequest source) {
        UiEventExecuteRequest copy = mapper.convertValue(source, UiEventExecuteRequest.class);
        // 输入只保留数据范围，避免签名中携带表单未保存字段或 Provider 可改写的执行参数。
        copy.setInput(Map.of("filters", map(source.getInput() == null ? null : source.getInput().get("filters"))));
        copy.setSelection(null); copy.setPreview(false);
        return copy;
    }

    /** 在签发前及实际受理时检查最新发布字段，不让已有方案绕过显式的运行时禁读标记。 */
    private void requireReadableFields(ExcelResolvedScheme scheme,
            EntityPublishedSnapshotService.PinnedEntitySnapshot entity, String operation) {
        if (scheme.document().columns().isEmpty()) return;
        var fields = entity.snapshot().getFields();
        for (var column : scheme.document().columns()) {
            if ("EXPORT".equals(operation) ? !column.exportEnabled() : !column.importEnabled()) continue;
            var field = fields.stream().filter(item -> Objects.equals(item.getFieldCode(), column.fieldCode())).findFirst()
                    .orElseThrow(() -> new ForbiddenException("Excel方案字段不在当前已发布实体中：" + column.fieldCode()));
            if (Boolean.FALSE.equals(field.getRuntimeReadable()))
                throw new ForbiddenException("Excel方案包含不可读取字段：" + column.fieldCode());
        }
    }

    /** 请求规模先检查再查数据库，空白 ID 不能借空集合触发其他数据范围。 */
    private static List<String> selectedIds(List<String> ids) {
        if (ids == null) return List.of();
        if (ids.size() > 5000) throw new IllegalArgumentException("一次最多导出5000条选中记录");
        if (ids.stream().anyMatch(id -> id == null || id.isBlank())) throw new IllegalArgumentException("选择记录ID不能为空");
        return ids.stream().map(String::trim).distinct().toList();
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try { return mapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception ex) { throw new IllegalArgumentException("列表固定条件无效", ex); }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : Map.of();
    }

    public static String text(Object value) { return value == null ? "" : String.valueOf(value); }
    public static String safeName(String value) { return text(value).replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_"); }
    private String userId() {
        String userId = UserContext.getUserId();
        if (userId == null) throw new ForbiddenException("请先登录");
        return userId;
    }

    public record Plan(ExcelActionTokenService.Claims claims, ExcelResolvedScheme scheme, EntityListConfig list,
            EntityPublishedSnapshotService.PinnedEntitySnapshot entity, ResolvedEntityFormRelease writeForm) { }
}
