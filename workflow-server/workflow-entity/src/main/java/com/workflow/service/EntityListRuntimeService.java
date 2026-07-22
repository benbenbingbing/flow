package com.workflow.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.ForbiddenException;
import com.workflow.common.PageResult;
import com.workflow.common.PermissionUtil;
import com.workflow.common.UserContext;
import com.workflow.common.json.JsonDocumentCodec;
import com.workflow.contracts.entity.list.*;
import com.workflow.dto.*;
import com.workflow.dto.permission.*;
import com.workflow.entity.*;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityListFieldMapper;
import com.workflow.service.permission.DataPermissionEngine;
import com.workflow.service.permission.EntityActionCapabilityService;
import com.workflow.service.permission.EntityListScopeAuditService;
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
    private final EntityListConfigService listConfigService;
    private final EntityDefinitionMapper definitionMapper;
    private final EntityListFieldMapper fieldMapper;
    private final SysUserService sysUserService;
    private final DataPermissionEngine dataPermissionEngine;
    private final EntityListScopeAuditService auditService;
    private final EntityActionCapabilityService actionCapabilityService;
    private final ObjectMapper objectMapper;
    private final JsonDocumentCodec jsonDocumentCodec;
    private final com.workflow.service.permission.EntityListActionConfigService actionConfigService;
    private final EntityListRelationalConfigService relationalConfigService;
    private final EntityListPublishedRuntimeService publishedRuntimeService;
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
        schema.setToolbarConfig(publishedRuntimeService.resolveToolbar(
                config,
                actionConfigService.resolveToolbarButtons(config, entityCode)));
        schema.setRowActionConfig(publishedRuntimeService.resolveRowActions(
                config,
                actionConfigService.resolveRowButtons(config, entityCode)));
        schema.setCustomComponent(config.getCustomComponent());
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
        schema.setQueryProviderCode(config.getQueryProviderCode());
        schema.setToolbarCapabilities(
                actionCapabilityService.evaluateToolbarActions(entityCode, config));
        schema.setFields(publishedRuntimeService.resolveFields(
                config,
                fieldMapper.findByListConfigId(config.getId())));

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

        if (StringUtils.hasText(config.getQueryDataSourceId())) {
            UiDataSourceExecuteRequest dataSourceRequest =
                    new UiDataSourceExecuteRequest();
            dataSourceRequest.setUsage("LIST_QUERY");
            dataSourceRequest.setConfigType("LIST");
            dataSourceRequest.setConfigId(config.getId());
            dataSourceRequest.setReleaseId(config.getActiveReleaseId());
            dataSourceRequest.setEntityCode(entityCode);
            dataSourceRequest.setListKey(listKey);
            dataSourceRequest.setPageNum((int) Math.max(
                    1, Math.min(Integer.MAX_VALUE, safeRequest.getPageNum())));
            dataSourceRequest.setPageSize((int) Math.max(
                    1, Math.min(200, safeRequest.getPageSize())));
            dataSourceRequest.setContext(safeRequest.getContext() == null
                    ? Map.of()
                    : objectMapper.convertValue(
                            safeRequest.getContext(),
                            new TypeReference<Map<String, Object>>() {}));
            dataSourceRequest.setInput(Map.of("filters", filters));
            return uiDataSourceService.execute(
                    config.getQueryDataSourceId(),
                    dataSourceRequest);
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
            query.put("pageNum", Math.max(1, safeRequest.getPageNum()));
            query.put("pageSize", Math.max(1, Math.min(200, safeRequest.getPageSize())));
            query.put("filters", filters);
            return provider.query(
                    runtimeContext(entityCode, listKey, scene, safeRequest.getContext()),
                    plan,
                    query);
        }

        return dataListService.findPageWithConfig(
                entityCode,
                listKey,
                filters,
                safeRequest.getPageNum(),
                safeRequest.getPageSize());
    }

    @Transactional(readOnly = true)
    public EntityListScopeSimulationDTO simulate(
            String entityCode,
            String listKey,
            EntityListScopeSimulationRequest request) {
        currentUserRoleService.requireSuperAdmin();
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
        if (!PermissionUtil.hasPermission(permission)) {
            throw new ForbiddenException("没有权限访问列表：" + config.getListName());
        }
    }

    private String resolveAccessPermission(EntityListConfig config) {
        return StringUtils.hasText(config.getAccessPermissionCode())
                ? config.getAccessPermissionCode()
                : "entity:" + config.getEntityCode().toLowerCase(Locale.ROOT) + ":list";
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
}
