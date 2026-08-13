package com.workflow.entity.list.application;

import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.application.SystemEntityReadService;
import com.workflow.entity.definition.application.SystemEntityFieldPolicy;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.list.api.request.EntityListQueryRequest;
import com.workflow.entity.list.api.response.EntityListRuntimeContextDTO;
import com.workflow.entity.list.api.response.EntityListSchemaDTO;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import com.workflow.entity.permission.api.request.EntityListScopeSimulationRequest;
import com.workflow.entity.permission.api.response.DataPermissionResult;
import com.workflow.entity.permission.api.response.EntityListScopeSimulationDTO;
import com.workflow.entity.permission.api.response.PermissionPreviewDTO;
import com.workflow.entity.ui.api.request.UiDataSourceExecuteRequest;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import com.workflow.entity.ui.application.UiDataSourceService;
import com.workflow.entity.ui.application.UiEventRuntimeService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.ForbiddenException;
import com.workflow.core.result.PageResult;
import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.admin.security.context.UserContext;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.contracts.entity.list.*;
import com.workflow.contracts.ui.UiDataSourceUsages;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListFieldMapper;
import com.workflow.entity.permission.application.DataPermissionEngine;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityListScopeAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * entityCode + listKey 驱动的统一实体列表运行时。
 */
@Service
@RequiredArgsConstructor
public class EntityListRuntimeService {

    private static final Set<String> SCENES = Set.of(
            "MENU", "PAGE", "DIALOG", "DRAWER",
            "EMBEDDED", "FORM_PICKER", "SUB_TABLE");

    private final EntityDataListConfigService dataListService;
    private final EntityDataDynamicService dynamicService;
    private final SystemEntityReadService systemEntityReadService;
    private final EntityListConfigService listConfigService;
    private final EntityDefinitionMapper definitionMapper;
    private final EntityFieldMapper entityFieldMapper;
    private final SystemEntityFieldPolicy systemEntityFieldPolicy;
    private final EntityListFieldMapper fieldMapper;
    private final SysUserService sysUserService;
    private final DataPermissionEngine dataPermissionEngine;
    private final EntityListScopeAuditService auditService;
    private final EntityActionCapabilityService actionCapabilityService;
    private final ObjectMapper objectMapper;
    private final JsonDocumentCodec jsonDocumentCodec;
    private final com.workflow.entity.permission.application.EntityListActionConfigService actionConfigService;
    private final EntityListRelationalConfigService relationalConfigService;
    private final EntityListPublishedRuntimeService publishedRuntimeService;
    private final EntityListPageResultNormalizer pageResultNormalizer;
    private final UiEventRuntimeService uiEventRuntimeService;
    private final UiDataSourceService uiDataSourceService;
    private final CurrentUserRoleService currentUserRoleService;
    private final List<EntityListContextResolver> contextResolvers;
    private final List<EntityListDataProvider> dataProviders;
    private final List<EntityListSchemaProvider> schemaProviders;

    @Transactional(readOnly = true)
    public EntityListSchemaDTO schema(
            String entityCode,
            String listKey,
            String requestedScene) {
        EntityListConfig config = requireList(entityCode, listKey);
        String scene = validateScene(config, requestedScene);
        requireListAccess(config);
        EntityDefinition definition = definitionMapper.findByEntityCode(entityCode)
                .orElseThrow(() -> new IllegalArgumentException("实体不存在: " + entityCode));
        boolean systemEntity =
                definition.getStorageMode()
                        == EntityDefinition.StorageMode.SYSTEM;
        if (systemEntity) {
            systemEntityReadService.requirePermissions(entityCode);
        }

        EntityListSchemaDTO schema = new EntityListSchemaDTO();
        schema.setId(config.getId());
        schema.setEntityCode(entityCode);
        schema.setEntityName(definition.getEntityName());
        schema.setListKey(config.getListKey());
        schema.setListName(config.getListName());
        schema.setScene(scene);
        schema.setAccessPermissionCode(resolveAccessPermission(config));
        schema.setDataScopeMode(normalized(config.getDataScopeMode(), "INHERIT"));
        schema.setPublishedVersion(config.getPublishedVersion());
        schema.setSelectionConfig(readObject(
                config.getSelectionConfig(), "选择模式配置"));
        schema.setViewConfig(readObject(config.getViewConfig(), "列表视图配置"));
        schema.setToolbarConfig(systemEntity
                ? List.of()
                : publishedRuntimeService.resolveToolbar(
                        config,
                        actionConfigService.resolveToolbarButtons(
                                config, entityCode)));
        schema.setRowActionConfig(systemEntity
                ? List.of(readOnlyViewAction())
                : publishedRuntimeService.resolveRowActions(
                        config,
                        actionConfigService.resolveRowButtons(
                                config, entityCode)));
        schema.setCustomComponent(systemEntity
                ? null : config.getCustomComponent());
        List<String> relationScenes = publishedRuntimeService.resolveScenes(
                config,
                relationalConfigService.findScenes(config.getId()));
        schema.setAllowedScenes(relationScenes.isEmpty()
                ? readArray(config.getAllowedScenes())
                : relationScenes);
        schema.setFixedFilterConfig(readObject(
                config.getFixedFilterConfig(), "列表固定条件"));
        schema.setContextBindingConfig(readObject(
                config.getContextBindingConfig(), "上下文绑定配置"));
        schema.setQueryProviderCode(systemEntity
                ? null : config.getQueryProviderCode());
        schema.setToolbarCapabilities(systemEntity
                ? Map.of()
                : actionCapabilityService.evaluateToolbarActions(
                        entityCode, config));
        List<EntityListField> resolvedFields =
                publishedRuntimeService.resolveFields(
                        config,
                        fieldMapper.findByListConfigId(config.getId()));
        if (systemEntity) {
            Set<String> readableCodes =
                    entityFieldMapper.findByEntityId(definition.getId())
                            .stream()
                            .filter(field ->
                                    systemEntityFieldPolicy
                                            .isRuntimeReadable(
                                                    definition,
                                                    field))
                            .map(field -> field.getFieldCode())
                            .collect(java.util.stream.Collectors.toSet());
            resolvedFields = resolvedFields.stream()
                    .filter(field ->
                            readableCodes.contains(
                                    field.getFieldCode()))
                    .toList();
        }
        schema.setFields(resolvedFields);

        if (StringUtils.hasText(config.getCustomComponent())) {
            for (EntityListSchemaProvider provider : schemaProviders) {
                if (provider.getCode().equalsIgnoreCase(config.getCustomComponent())) {
                    Map<String, Object> base = objectMapper.convertValue(
                            schema, new TypeReference<>() {
                            });
                    Map<String, Object> enhanced = provider.enhance(
                            runtimeContext(entityCode, listKey, scene, null),
                            base);
                    return objectMapper.convertValue(enhanced, EntityListSchemaDTO.class);
                }
            }
        }
        return schema;
    }

    @Transactional(readOnly = true)
    public Object query(
            String entityCode,
            String listKey,
            EntityListQueryRequest request) {
        EntityListConfig config = requireList(entityCode, listKey);
        String scene = validateScene(config, request == null ? null : request.getScene());
        requireListAccess(config);
        EntityListQueryRequest safeRequest = request == null
                ? new EntityListQueryRequest() : request;
        Map<String, Object> filters = validateUserFilters(
                config,
                safeRequest.getFilters());
        mergeTrusted(filters, readObject(config.getFixedFilterConfig(), "列表固定条件"));
        mergeTrusted(filters, resolveContextFilters(
                entityCode, listKey, scene, safeRequest.getContext()));

        UiEventExecuteRequest event = new UiEventExecuteRequest();
        event.setEventCode(UiDataSourceUsages.LIST_LOAD);
        event.setConfigType("LIST");
        event.setConfigId(config.getId());
        event.setReleaseId(config.getActiveReleaseId());
        event.setEntityCode(entityCode);
        event.setListKey(listKey);
        event.setContext(safeRequest.getContext() == null
                ? Map.of()
                : objectMapper.convertValue(
                        safeRequest.getContext(),
                        new TypeReference<Map<String, Object>>() {}));
        Map<String, Object> eventInput = new LinkedHashMap<>();
        eventInput.put("filters", filters);
        eventInput.put("pageNum", Math.max(1, safeRequest.getPageNum()));
        eventInput.put(
                "pageSize",
                Math.max(1, Math.min(200, safeRequest.getPageSize())));
        eventInput.put("scene", scene);
        event.setInput(eventInput);
        Object result = uiEventRuntimeService.execute(
                event,
                input -> queryDefault(
                        config,
                        entityCode,
                        listKey,
                        scene,
                        safeRequest,
                        input)).getData();
        return pageResultNormalizer.normalize(
                result,
                Math.max(1, safeRequest.getPageNum()),
                Math.max(1, Math.min(200, safeRequest.getPageSize())));
    }

    private Object queryDefault(
            EntityListConfig config,
            String entityCode,
            String listKey,
            String scene,
            EntityListQueryRequest safeRequest,
            Map<String, Object> eventInput) {
        Map<String, Object> filters =
                eventInput.get("filters") instanceof Map<?, ?> map
                        ? objectMapper.convertValue(
                                map,
                                new TypeReference<Map<String, Object>>() {})
                        : Map.of();
        int pageNum = positiveInt(
                eventInput.get("pageNum"),
                (int) Math.max(1, safeRequest.getPageNum()));
        int pageSize = Math.max(
                1,
                Math.min(
                        200,
                        positiveInt(
                                eventInput.get("pageSize"),
                                (int) Math.max(
                                        1,
                                        safeRequest.getPageSize()))));
        EntityDefinition definition =
                definitionMapper.findByEntityCode(entityCode)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "实体不存在: " + entityCode));
        if (definition.getStorageMode()
                == EntityDefinition.StorageMode.SYSTEM) {
            if (StringUtils.hasText(
                    config.getQueryProviderCode())
                    || StringUtils.hasText(
                            config.getQueryDataSourceId())) {
                throw new IllegalStateException(
                        "平台系统表列表不能覆盖可信只读查询");
            }
            Map<String, Object> viewConfig = readObject(
                    config.getViewConfig(), "列表视图配置");
            Map<String, Object> tableConfig =
                    viewConfig.get("table")
                                    instanceof Map<?, ?> table
                            ? objectMapper.convertValue(
                                    table,
                                    new TypeReference<Map<String, Object>>() {})
                            : Map.of();
            return systemEntityReadService.findPage(
                    entityCode,
                    filters,
                    pageNum,
                    pageSize,
                    text(tableConfig.get("defaultSortField")),
                    text(tableConfig.get("defaultSortDirection")));
        }
        if (StringUtils.hasText(
                config.getQueryDataSourceId())) {
            if (!StringUtils.hasText(
                    config.getQueryOperationCode())) {
                throw new IllegalStateException(
                        "列表查询接口缺少操作编码");
            }
            UiDataSourceExecuteRequest request =
                    new UiDataSourceExecuteRequest();
            request.setUsage(UiDataSourceUsages.LIST_QUERY);
            request.setOperationCode(
                    config.getQueryOperationCode());
            request.setConfigType("LIST");
            request.setConfigId(config.getId());
            request.setReleaseId(
                    config.getActiveReleaseId());
            request.setEntityCode(entityCode);
            request.setListKey(listKey);
            request.setTargetType("OWNER");
            request.setPageNum(pageNum);
            request.setPageSize(pageSize);
            Map<String, Object> input =
                    new LinkedHashMap<>();
            input.put("filters", filters);
            input.put("sorts", List.of());
            input.put("currentRow", Map.of());
            input.put("selectedRows", List.of());
            input.put("records", List.of());
            input.put("pageNum", pageNum);
            input.put("pageSize", pageSize);
            input.put("scene", scene);
            request.setInput(input);
            return uiDataSourceService.executeOperation(
                    config.getQueryDataSourceId(),
                    config.getQueryOperationCode(),
                    request);
        }
        if (StringUtils.hasText(config.getQueryProviderCode())) {
            EntityListDataProvider provider = dataProviders.stream()
                    .filter(item -> item.getCode().equalsIgnoreCase(config.getQueryProviderCode()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "自定义列表数据源未注册: " + config.getQueryProviderCode()));
            SysUser user = currentUser();
            DataPermissionResult permission =
                    dataPermissionEngine.calculatePermission(entityCode, listKey, user);
            DataScopePlan plan = new DataScopePlan(
                    permission.isHasPermission(),
                    permission.isNeedFilter() ? permission.getSqlCondition() : "1=1",
                    Map.of(),
                    List.of(),
                    permission.getMatchedRuleNames() == null
                            ? List.of() : permission.getMatchedRuleNames(),
                    permission.getExplanation(),
                    permission.getReleaseVersion());
            Map<String, Object> query = new LinkedHashMap<>();
            query.put(
                    EntityListQueryFields.PAGE_NUM,
                    pageNum);
            query.put(
                    EntityListQueryFields.PAGE_SIZE,
                    pageSize);
            query.put(
                    EntityListQueryFields.FILTERS,
                    filters);
            return provider.query(
                    runtimeContext(entityCode, listKey, scene, safeRequest.getContext()),
                    plan,
                    query);
        }

        return dataListService.findPageWithConfig(
                entityCode,
                listKey,
                filters,
                pageNum,
                pageSize);
    }

    private int positiveInt(
            Object value,
            int fallback) {
        if (value instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        try {
            return value == null
                    ? fallback
                    : Math.max(1, Integer.parseInt(String.valueOf(value)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @Transactional(readOnly = true)
    public EntityListScopeSimulationDTO simulate(
            String entityCode,
            String listKey,
            EntityListScopeSimulationRequest request) {
        currentUserRoleService.requireSuperAdmin();
        if (systemEntityReadService.isSystemEntity(entityCode)) {
            throw new IllegalStateException(
                    "平台系统表不使用动态实体数据范围模拟");
        }
        EntityListConfig config = requireList(entityCode, listKey);
        String userId = request == null || !StringUtils.hasText(request.getUserId())
                ? UserContext.getUserId() : request.getUserId();
        SysUser user = sysUserService.getById(userId);
        if (user == null) {
            throw new IllegalArgumentException("模拟用户不存在");
        }
        Map<String, Object> filters = validateUserFilters(
                config,
                request == null ? Map.of() : request.getFilters());
        mergeTrusted(filters, readObject(config.getFixedFilterConfig(), "列表固定条件"));
        PermissionPreviewDTO preview =
                dataPermissionEngine.previewPermissionDetail(entityCode, listKey, user);
        PageResult<EntityDataDTO> page = dynamicService.findPageForUser(
                entityCode, listKey, filters, 1, 10, user);

        EntityListScopeSimulationDTO result = new EntityListScopeSimulationDTO();
        result.setEntityCode(entityCode);
        result.setListKey(listKey);
        result.setUserId(userId);
        result.setDataScopeMode(preview.getDataScopeMode());
        result.setReleaseVersion(preview.getReleaseVersion());
        result.setPreview(preview);
        result.setVisibleCount(page.getTotal());
        result.setSamples(page.getRecords());
        if ("OVERRIDE".equalsIgnoreCase(config.getDataScopeMode())) {
            result.getWarnings().add("当前列表使用独立范围，可能比实体默认范围更宽");
        }
        auditService.record(
                entityCode, listKey, UserContext.getUserId(), "SIMULATE", "SUCCESS",
                Map.of("targetUserId", userId, "visibleCount", page.getTotal()));
        return result;
    }

    private EntityListConfig requireList(String entityCode, String listKey) {
        if (!StringUtils.hasText(entityCode) || !StringUtils.hasText(listKey)) {
            throw new IllegalArgumentException("entityCode 和 listKey 不能为空");
        }
        EntityListConfig config = dataListService.findListConfig(entityCode, listKey);
        if (config == null || !listKey.equals(config.getListKey())) {
            throw new IllegalArgumentException("列表不存在或未发布: " + listKey);
        }
        if (config.getPublishedVersion() == null || config.getPublishedVersion() < 1) {
            throw new IllegalStateException("列表尚未发布: " + listKey);
        }
        return config;
    }

    private void requireListAccess(EntityListConfig config) {
        String permission = resolveAccessPermission(config);
        Set<String> permissions =
                PermissionUtil.getCurrentUserPermissions();
        if (!permissions.contains("*")
                && !PermissionUtil.hasPermission(permission)) {
            throw new ForbiddenException("没有权限访问列表：" + config.getListName());
        }
    }

    private String resolveAccessPermission(EntityListConfig config) {
        if (StringUtils.hasText(config.getAccessPermissionCode())) {
            return config.getAccessPermissionCode();
        }
        EntityDefinition definition =
                definitionMapper.findByEntityCode(config.getEntityCode())
                        .orElse(null);
        if (definition != null
                && definition.getStorageMode()
                == EntityDefinition.StorageMode.SYSTEM) {
            return systemEntityFieldPolicy
                    .requiredPermissions(config.getEntityCode())
                    .stream()
                    .findFirst()
                    .orElse("");
        }
        return "entity:"
                + config.getEntityCode()
                        .toLowerCase(Locale.ROOT)
                + ":list";
    }

    private Map<String, Object> readOnlyViewAction() {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("key", "view");
        action.put("type", "built-in");
        action.put("label", "查看");
        action.put("buttonType", "primary");
        action.put("link", true);
        action.put("sort", 1);
        action.put("enabled", true);
        action.put("perm", "");
        return action;
    }

    private String validateScene(EntityListConfig config, String scene) {
        String normalizedScene = normalized(scene, "PAGE");
        if (!SCENES.contains(normalizedScene)) {
            throw new IllegalArgumentException("不支持的列表运行场景: " + scene);
        }
        List<String> allowed = publishedRuntimeService.resolveScenes(
                config,
                relationalConfigService.findScenes(config.getId()));
        if (allowed.isEmpty()) {
            allowed = readArray(config.getAllowedScenes());
        }
        if (!allowed.isEmpty()
                && allowed.stream().noneMatch(normalizedScene::equalsIgnoreCase)) {
            throw new ForbiddenException("当前列表不允许在 " + normalizedScene + " 场景使用");
        }
        return normalizedScene;
    }

    private Map<String, Object> validateUserFilters(
            EntityListConfig config,
            Map<String, Object> requestFilters) {
        if (requestFilters == null || requestFilters.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Set<String> queryFields = new LinkedHashSet<>();
        for (EntityListField field : publishedRuntimeService.resolveFields(
                config,
                fieldMapper.findByListConfigId(config.getId()))) {
            if (Boolean.TRUE.equals(field.getIsQuery())) {
                queryFields.add(field.getFieldCode());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : requestFilters.entrySet()) {
            String base = stripSuffix(entry.getKey());
            if (!queryFields.contains(base)) {
                throw new IllegalArgumentException("字段未配置为可查询条件: " + base);
            }
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private Map<String, Object> resolveContextFilters(
            String entityCode,
            String listKey,
            String scene,
            EntityListRuntimeContextDTO context) {
        if (context == null || !StringUtils.hasText(context.getRelationKey())) {
            return Map.of();
        }
        EntityListContextResolver resolver = contextResolvers.stream()
                .filter(item -> item.getRelationKey().equalsIgnoreCase(context.getRelationKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "列表上下文关系未注册: " + context.getRelationKey()));
        Map<String, Object> resolved = resolver.resolve(
                runtimeContext(entityCode, listKey, scene, context));
        return resolved == null ? Map.of() : resolved;
    }

    private EntityListRuntimeContext runtimeContext(
            String entityCode,
            String listKey,
            String scene,
            EntityListRuntimeContextDTO context) {
        return new EntityListRuntimeContext(
                entityCode,
                listKey,
                scene,
                context == null ? null : context.getSourceEntityCode(),
                context == null ? null : context.getSourceRecordId(),
                context == null ? null : context.getRelationKey(),
                context == null || context.getParameters() == null
                        ? Map.of() : context.getParameters());
    }

    private void mergeTrusted(
            Map<String, Object> target,
            Map<String, Object> trusted) {
        if (trusted != null) {
            target.putAll(trusted);
        }
    }

    private Map<String, Object> readObject(String json, String label) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        return jsonDocumentCodec.readObject(json, label);
    }

    private List<String> readArray(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        return jsonDocumentCodec.readArray(json, "列表允许场景").stream()
                .map(String::valueOf)
                .toList();
    }

    private String stripSuffix(String key) {
        for (String suffix : List.of("_start", "_end", "_op")) {
            if (key.endsWith(suffix)) {
                return key.substring(0, key.length() - suffix.length());
            }
        }
        return key;
    }

    private SysUser currentUser() {
        SysUser user = sysUserService.getById(UserContext.getUserId());
        if (user == null) {
            throw new ForbiddenException("当前用户不存在");
        }
        return user;
    }

    private String normalized(String value, String fallback) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT)
                : fallback;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
