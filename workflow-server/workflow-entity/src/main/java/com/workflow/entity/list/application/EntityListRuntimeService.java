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
import com.workflow.entity.ui.application.UiViewCompositionTokenService;

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
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
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
    private static final Set<String> EMBED_SAFE_LIST_FIELD_SOURCES = Set.of(
            "ENTITY_FIELD", "REFERENCE");

    private final EntityDataListConfigService dataListService;
    private final EntityDataDynamicService dynamicService;
    private final SystemEntityReadService systemEntityReadService;
    private final EntityListConfigService listConfigService;
    private final EntityDefinitionMapper definitionMapper;
    private final EntityFieldMapper entityFieldMapper;
    private final SystemEntityFieldPolicy systemEntityFieldPolicy;
    private final EntityListFieldMapper fieldMapper;
    private final EntityListConfigMapper listConfigMapper;
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
    private final UiViewCompositionTokenService viewCompositionTokenService;
    private final List<EntityListContextResolver> contextResolvers;
    private final List<EntityListDataProvider> dataProviders;
    private final List<EntityListSchemaProvider> schemaProviders;

    @Transactional(readOnly = true)
    public EntityListSchemaDTO schema(
            String entityCode,
            String listKey,
            String requestedScene) {
        return schema(
                entityCode,
                listKey,
                requestedScene,
                null,
                null,
                null);
    }

    @Transactional(readOnly = true)
    public EntityListSchemaDTO schema(
            String entityCode,
            String listKey,
            String requestedScene,
            String releaseId,
            Integer releaseVersion,
            String releaseResolutionToken) {
        return schema(
                entityCode,
                listKey,
                requestedScene,
                releaseId,
                releaseVersion,
                releaseResolutionToken,
                null);
    }

    @Transactional(readOnly = true)
    public EntityListSchemaDTO schema(
            String entityCode,
            String listKey,
            String requestedScene,
            String releaseId,
            Integer releaseVersion,
            String releaseResolutionToken,
            String viewCompositionContextToken) {
        UiViewCompositionTokenService.Claims compositionContext =
                verifyViewCompositionContext(
                        viewCompositionContextToken);
        EntityListConfig config = requireList(
                entityCode,
                listKey,
                releaseId,
                releaseVersion,
                releaseResolutionToken,
                compositionContext);
        return schemaResolved(entityCode, listKey, requestedScene, config);
    }

    /**
     * 按服务端已经认证并固定的发布坐标解析列表 Schema。
     *
     * <p>该入口仅供 Embed 等可信适配器使用，不能映射为允许浏览器提交 releaseId 的接口。
     * 它绕过的是“历史版本必须带浏览器解析令牌”的传输约束，不绕过列表访问权限、发布快照
     * 完整性或系统实体权限。</p>
     */
    @Transactional(readOnly = true)
    public EntityListSchemaDTO schemaPinned(
            String entityCode,
            String listKey,
            String releaseId,
            int releaseVersion) {
        return schemaResolved(
                entityCode,
                listKey,
                "EMBEDDED",
                requirePinnedList(entityCode, listKey, releaseId, releaseVersion));
    }

    private EntityListSchemaDTO schemaResolved(
            String entityCode,
            String listKey,
            String requestedScene,
            EntityListConfig config) {
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
        schema.setReleaseId(config.getActiveReleaseId());
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
        // 该字段由 PublishedRuntimeService 从已校验快照填充，设计态草稿不会泄漏到运行时。
        schema.setViewCompositions(
                config.getViewCompositions() == null
                        ? List.of()
                        : config.getViewCompositions());

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
        EntityListQueryRequest safeRequest = request == null
                ? new EntityListQueryRequest() : request;
        UiViewCompositionTokenService.Claims compositionContext =
                verifyViewCompositionContext(
                        safeRequest.getViewCompositionContextToken());
        EntityListConfig config = requireList(
                entityCode,
                listKey,
                safeRequest.getReleaseId(),
                safeRequest.getReleaseVersion(),
                safeRequest.getReleaseResolutionToken(),
                compositionContext);
        return queryResolved(
                entityCode,
                listKey,
                safeRequest,
                compositionContext,
                config,
                Map.of(),
                false);
    }

    /**
     * 查询服务端固定的列表发布版本，并合并服务端解析出的 Embed Context 固定条件。
     *
     * <p>客户端条件先按发布列表校验；列表固定条件、Embed Context 条件和 Flow 数据范围均由
     * 服务端追加，客户端不能覆盖。默认排序仍由已发布列表决定。</p>
     */
    @Transactional(readOnly = true)
    public Object queryPinned(
            String entityCode,
            String listKey,
            String releaseId,
            int releaseVersion,
            long pageNum,
            long pageSize,
            Map<String, Object> clientFilters,
            Map<String, Object> trustedContextFilters) {
        EntityListQueryRequest request = new EntityListQueryRequest();
        request.setPageNum(pageNum);
        request.setPageSize(pageSize);
        request.setScene("EMBEDDED");
        request.setFilters(clientFilters == null ? Map.of() : clientFilters);
        return queryResolved(
                entityCode,
                listKey,
                request,
                null,
                requirePinnedList(entityCode, listKey, releaseId, releaseVersion),
                trustedContextFilters == null ? Map.of() : trustedContextFilters,
                true);
    }

    private Object queryResolved(
            String entityCode,
            String listKey,
            EntityListQueryRequest safeRequest,
            UiViewCompositionTokenService.Claims compositionContext,
            EntityListConfig config,
            Map<String, Object> trustedContextFilters,
            boolean bypassPublishedUiEvents) {
        String scene = validateScene(
                config,
                safeRequest.getScene());
        requireListAccess(config);
        Map<String, Object> filters = validateUserFilters(
                config,
                safeRequest.getFilters());
        Map<String, Object> publishedFixedFilters = readObject(
                config.getFixedFilterConfig(), "列表固定条件");
        if (bypassPublishedUiEvents
                && hasTrustedFilterConflict(filters, publishedFixedFilters)) {
            return emptyPage(safeRequest);
        }
        mergeTrusted(filters, publishedFixedFilters);
        mergeTrusted(filters, resolveContextFilters(
                entityCode, listKey, scene, safeRequest.getContext()));
        // Embed Context 来自已认证 Session 和不可变 Release 绑定，不接受浏览器覆盖。
        if (hasTrustedFilterConflict(filters, trustedContextFilters)) {
            return emptyPage(safeRequest);
        }
        mergeTrusted(filters, trustedContextFilters);
        if (compositionContext != null) {
            mergeTrusted(
                    filters,
                    compositionContext.fixedFilters());
            if (compositionContext.matchNone()) {
                return new PageResult<>(
                        List.of(),
                        0,
                        Math.max(1, safeRequest.getPageNum()),
                        Math.max(1, Math.min(
                                200, safeRequest.getPageSize())));
            }
        }

        UiEventExecuteRequest event = new UiEventExecuteRequest();
        event.setEventCode(UiDataSourceUsages.LIST_LOAD);
        event.setConfigType("LIST");
        event.setConfigId(config.getId());
        event.setReleaseId(config.getActiveReleaseId());
        event.setReleaseVersion(config.getPublishedVersion());
        event.setReleaseResolutionToken(
                config.getReleaseResolutionToken());
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
        Object result;
        if (compositionContext == null && !bypassPublishedUiEvents) {
            result = uiEventRuntimeService.execute(
                    event,
                    input -> queryDefault(
                            config,
                            entityCode,
                            listKey,
                            scene,
                            safeRequest,
                            input)).getData();
        } else {
            // 关联内容令牌已经固定目标列表发布版本。现有 LIST_LOAD 事件解析器
            // 只认识父表单令牌，不能让它回退到当前 ACTIVE；这里直接执行同一
            // 发布列表的默认查询链；自定义查询结果会在归一化后由平台再次
            // 应用目标实体数据范围，不能依赖 Provider/Connector 自行声明。
            result = queryDefault(
                    config,
                    entityCode,
                    listKey,
                    scene,
                    safeRequest,
                    eventInput);
        }
        PageResult<?> normalizedResult = pageResultNormalizer.normalize(
                result,
                Math.max(1, safeRequest.getPageNum()),
                Math.max(1, Math.min(200, safeRequest.getPageSize())));
        if (compositionContext == null
                || !usesCustomRecordQuery(config)) {
            return normalizedResult;
        }
        return secureCompositionCustomQueryPage(
                config,
                entityCode,
                listKey,
                filters,
                normalizedResult,
                Math.max(1, safeRequest.getPageNum()),
                Math.max(1, Math.min(
                        200, safeRequest.getPageSize())));
    }

    /**
     * 对关联内容中的自定义查询结果执行平台侧二次校验。
     *
     * <p>Provider 或接口连接器只负责给出候选记录 ID 与顺序，不能成为
     * 目标实体数据范围的权威来源。平台会把本页 ID 与已签名关联条件合并，
     * 再走标准实体列表查询链；任一候选记录不存在、越权或不满足关联条件时，
     * 整页拒绝，避免静默过滤造成跨页补位和数量侧信道。返回行也只投影目标
     * 列表已经发布的展示字段，连接器附带的其它字段不会透传。</p>
     *
     * <p>外部数据源声明的 total 无法证明已经应用平台数据范围，因此不会
     * 透传。这里使用“请求窗口 + 下一页哨兵”的导航总数：短页表示当前窗口
     * 已结束，满页只额外暴露一个可继续翻页的位置；它不是统计总数，也不会
     * 泄露未校验记录数量。</p>
     */
    private PageResult<?> secureCompositionCustomQueryPage(
            EntityListConfig config,
            String entityCode,
            String listKey,
            Map<String, Object> trustedFilters,
            PageResult<?> candidatePage,
            long requestedPageNum,
            long requestedPageSize) {
        List<?> candidates = candidatePage.getRecords() == null
                ? List.of() : candidatePage.getRecords();
        int pageSize = (int) Math.max(
                1, Math.min(200, requestedPageSize));
        if (candidates.size() > pageSize) {
            throw new IllegalStateException(
                    "关联内容自定义列表返回记录数超过请求页大小，已停止加载");
        }

        long pageNum = Math.max(1, requestedPageNum);
        if (candidates.isEmpty()) {
            return new PageResult<>(
                    List.of(),
                    verifiedWindowTotal(
                            pageNum, pageSize, 0),
                    pageNum,
                    pageSize);
        }

        List<String> orderedIds = candidates.stream()
                .map(this::requireCandidateRecordId)
                .toList();
        LinkedHashSet<String> uniqueIds =
                new LinkedHashSet<>(orderedIds);
        Map<String, Object> authoritativeFilters =
                new LinkedHashMap<>(trustedFilters == null
                        ? Map.of() : trustedFilters);
        requireCandidateIdsMatchIdFilter(
                authoritativeFilters, uniqueIds);
        removeIdFilters(authoritativeFilters);
        authoritativeFilters.put("id", List.copyOf(uniqueIds));
        authoritativeFilters.put("id_op", "IN");

        // 标准列表链负责在服务端应用目标 listKey 的 DataScope，并补充平台
        // 管理的列值和行操作能力；不能复用 Provider/Connector 返回的整行。
        PageResult<EntityDataDTO> authoritativePage =
                dataListService.findPageWithResolvedConfig(
                        entityCode,
                        listKey,
                        config,
                        authoritativeFilters,
                        1,
                        uniqueIds.size());
        Map<String, EntityDataDTO> authoritativeById =
                indexAuthoritativeRecords(authoritativePage);
        if (!authoritativeById.keySet().equals(uniqueIds)) {
            throw new ForbiddenException(
                    "关联内容数据源返回了不存在、无权访问或不满足关联条件的目标记录，已停止加载");
        }

        Set<String> visibleFieldCodes =
                publishedVisibleFieldCodes(config);
        List<Map<String, Object>> records = orderedIds.stream()
                .map(authoritativeById::get)
                .map(record -> projectPublishedListFields(
                        record, visibleFieldCodes))
                .toList();
        return new PageResult<>(
                records,
                verifiedWindowTotal(
                        pageNum, pageSize, records.size()),
                pageNum,
                pageSize);
    }

    private boolean usesCustomRecordQuery(
            EntityListConfig config) {
        return StringUtils.hasText(config.getQueryDataSourceId())
                || StringUtils.hasText(
                config.getQueryProviderCode());
    }

    private String requireCandidateRecordId(Object candidate) {
        Object value;
        if (candidate instanceof EntityDataDTO record) {
            value = record.getId();
        } else if (candidate instanceof Map<?, ?> map) {
            value = map.get("id");
            if (value == null) {
                value = map.get("recordId");
            }
        } else {
            throw new IllegalStateException(
                    "关联内容自定义列表必须返回包含目标实体记录 ID 的对象，已停止加载");
        }
        String id = value == null
                ? null : String.valueOf(value).trim();
        if (!StringUtils.hasText(id)) {
            throw new IllegalStateException(
                    "关联内容自定义列表缺少目标实体记录 ID，已停止加载");
        }
        return id;
    }

    private Map<String, EntityDataDTO> indexAuthoritativeRecords(
            PageResult<EntityDataDTO> page) {
        if (page == null || page.getRecords() == null) {
            throw new IllegalStateException(
                    "平台无法复核关联内容目标记录，已停止加载");
        }
        Map<String, EntityDataDTO> result =
                new LinkedHashMap<>();
        for (EntityDataDTO record : page.getRecords()) {
            if (record == null
                    || !StringUtils.hasText(record.getId())
                    || result.put(record.getId(), record) != null) {
                throw new IllegalStateException(
                        "平台复核关联内容目标记录时返回了无效结果，已停止加载");
            }
        }
        return result;
    }

    private Set<String> publishedVisibleFieldCodes(
            EntityListConfig config) {
        List<EntityListField> fallback =
                fieldMapper.findByListConfigId(config.getId());
        List<EntityListField> fields =
                publishedRuntimeService.resolveFields(
                        config,
                        fallback == null ? List.of() : fallback);
        if (fields == null) {
            return Set.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (EntityListField field : fields) {
            if (field != null
                    && Boolean.TRUE.equals(
                    field.getShowInList())
                    && StringUtils.hasText(
                    field.getFieldCode())) {
                result.add(field.getFieldCode());
            }
        }
        return Collections.unmodifiableSet(result);
    }

    private Map<String, Object> projectPublishedListFields(
            EntityDataDTO record,
            Set<String> visibleFieldCodes) {
        Map<String, Object> source = objectMapper.convertValue(
                record,
                new TypeReference<Map<String, Object>>() {});
        Map<String, Object> sourceData = source.get("data")
                        instanceof Map<?, ?> data
                ? objectMapper.convertValue(
                        data,
                        new TypeReference<Map<String, Object>>() {})
                : Map.of();
        Map<String, Object> sourceExtData = source.get("extData")
                        instanceof Map<?, ?> extData
                ? objectMapper.convertValue(
                        extData,
                        new TypeReference<Map<String, Object>>() {})
                : Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", record.getId());
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> extData = new LinkedHashMap<>();
        for (String fieldCode : visibleFieldCodes) {
            if ("id".equals(fieldCode)) {
                continue;
            }
            if (source.containsKey(fieldCode)) {
                result.put(fieldCode, source.get(fieldCode));
            } else if (sourceData.containsKey(fieldCode)) {
                data.put(fieldCode, sourceData.get(fieldCode));
            } else if (sourceExtData.containsKey(fieldCode)) {
                extData.put(fieldCode, sourceExtData.get(fieldCode));
            }
        }
        if (!data.isEmpty()) {
            result.put("data", data);
        }
        if (!extData.isEmpty()) {
            result.put("extData", extData);
        }
        if (record.getActionCapabilities() != null
                && !record.getActionCapabilities().isEmpty()) {
            result.put(
                    "actionCapabilities",
                    record.getActionCapabilities());
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * ID 条件由平台在候选 ID 集合上先行验证，再替换为内部 IN 条件，避免
     * 自定义数据源忽略 SAME_RECORD/引用字段产生的 ID 约束。
     */
    private void requireCandidateIdsMatchIdFilter(
            Map<String, Object> filters,
            Collection<String> candidateIds) {
        boolean hasIdCondition = filters.keySet().stream()
                .anyMatch(key -> "id".equals(
                        stripSuffix(key)));
        if (!hasIdCondition) {
            return;
        }
        for (String id : candidateIds) {
            if (!matchesIdFilter(filters, id)) {
                throw new ForbiddenException(
                        "关联内容数据源返回了不满足关联条件的目标记录，已停止加载");
            }
        }
    }

    private boolean matchesIdFilter(
            Map<String, Object> filters,
            String id) {
        Object lower = filters.get("id_start");
        Object upper = filters.get("id_end");
        if (lower != null
                && id.compareTo(String.valueOf(lower)) < 0) {
            return false;
        }
        if (upper != null
                && id.compareTo(String.valueOf(upper)) > 0) {
            return false;
        }
        if (!filters.containsKey("id")) {
            return true;
        }
        Object expected = filters.get("id");
        if (expected == null
                || (expected instanceof String text
                && !StringUtils.hasText(text))) {
            return true;
        }
        String operator = normalized(
                text(filters.get("id_op")),
                expected instanceof String ? "LIKE" : "EQ");
        return switch (operator) {
            case "EQ" -> Objects.equals(
                    id, String.valueOf(expected));
            case "NE" -> !Objects.equals(
                    id, String.valueOf(expected));
            case "LIKE" -> id.contains(
                    String.valueOf(expected));
            case "GT" -> id.compareTo(
                    String.valueOf(expected)) > 0;
            case "LT" -> id.compareTo(
                    String.valueOf(expected)) < 0;
            case "IN" -> normalizeIdValues(expected)
                    .contains(id);
            case "NOT_IN" -> !normalizeIdValues(expected)
                    .contains(id);
            default -> Objects.equals(
                    id, String.valueOf(expected));
        };
    }

    private Set<String> normalizeIdValues(Object value) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (value instanceof Collection<?> collection) {
            collection.forEach(item -> addIdValue(result, item));
        } else if (value != null
                && value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            for (int index = 0; index < length; index++) {
                addIdValue(
                        result,
                        java.lang.reflect.Array.get(value, index));
            }
        } else if (value instanceof String text) {
            Arrays.stream(text.split(","))
                    .forEach(item -> addIdValue(result, item));
        } else {
            addIdValue(result, value);
        }
        return result;
    }

    private void addIdValue(
            Set<String> values,
            Object value) {
        if (value != null
                && StringUtils.hasText(
                String.valueOf(value))) {
            values.add(String.valueOf(value).trim());
        }
    }

    private void removeIdFilters(
            Map<String, Object> filters) {
        filters.keySet().removeIf(key -> "id".equals(
                stripSuffix(key)));
    }

    private long verifiedWindowTotal(
            long pageNum,
            long pageSize,
            int verifiedRows) {
        long offset;
        try {
            offset = Math.multiplyExact(
                    Math.max(0, pageNum - 1),
                    pageSize);
        } catch (ArithmeticException ignored) {
            offset = Long.MAX_VALUE - pageSize;
        }
        long total;
        try {
            total = Math.addExact(offset, verifiedRows);
        } catch (ArithmeticException ignored) {
            total = Long.MAX_VALUE;
        }
        if (verifiedRows >= pageSize
                && total < Long.MAX_VALUE) {
            total++;
        }
        return total;
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
            request.setReleaseVersion(
                    config.getPublishedVersion());
            request.setServerPinnedRelease(
                    Boolean.TRUE.equals(config.getPinnedRelease()));
            if (Boolean.TRUE.equals(config.getPinnedRelease())) {
                request.setServerIdempotencyKey(
                        "view-composition-list-query:"
                                + config.getActiveReleaseId()
                                + ":" + UserContext.getUserId()
                                + ":" + pageNum
                                + ":" + pageSize);
            }
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

        return dataListService.findPageWithResolvedConfig(
                entityCode,
                listKey,
                config,
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
        return requireList(
                entityCode,
                listKey,
                null,
                null,
                null);
    }

    private EntityListConfig requireList(
            String entityCode,
            String listKey,
            String releaseId,
            Integer releaseVersion,
            String releaseResolutionToken) {
        return requireList(
                entityCode,
                listKey,
                releaseId,
                releaseVersion,
                releaseResolutionToken,
                null);
    }

    private EntityListConfig requireList(
            String entityCode,
            String listKey,
            String releaseId,
            Integer releaseVersion,
            String releaseResolutionToken,
            UiViewCompositionTokenService.Claims compositionContext) {
        if (!StringUtils.hasText(entityCode) || !StringUtils.hasText(listKey)) {
            throw new IllegalArgumentException("entityCode 和 listKey 不能为空");
        }
        EntityListConfig config;
        if (compositionContext == null) {
            config = dataListService.findListConfig(
                    entityCode,
                    listKey,
                    releaseId,
                    releaseVersion,
                    releaseResolutionToken);
        } else {
            requireCompositionTarget(
                    compositionContext,
                    entityCode,
                    releaseId,
                    releaseVersion);
            EntityDefinition definition = definitionMapper
                    .findByEntityCode(entityCode)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "实体不存在: " + entityCode));
            EntityListConfig draft =
                    listConfigMapper.findByEntityIdAndListKey(
                            definition.getId(), listKey);
            if (draft == null
                    || !Objects.equals(
                    draft.getId(),
                    compositionContext.targetContentId())) {
                throw new ForbiddenException(
                        "关联内容列表上下文与目标列表不一致");
            }
            config = publishedRuntimeService
                    .resolveViewCompositionConfig(
                            draft,
                            compositionContext.targetReleaseId(),
                            compositionContext.targetReleaseVersion());
        }
        if (config == null
                || !listKey.equals(config.getListKey())
                || !entityCode.equals(config.getEntityCode())) {
            throw new IllegalArgumentException("列表不存在或未发布: " + listKey);
        }
        if (!Boolean.TRUE.equals(config.getPublishedSnapshot())
                || !StringUtils.hasText(config.getActiveReleaseId())
                || config.getPublishedVersion() == null
                || config.getPublishedVersion() < 1) {
            throw new IllegalStateException("列表尚未发布: " + listKey);
        }
        return config;
    }

    private EntityListConfig requirePinnedList(
            String entityCode,
            String listKey,
            String releaseId,
            int releaseVersion) {
        if (!StringUtils.hasText(entityCode)
                || !StringUtils.hasText(listKey)
                || !StringUtils.hasText(releaseId)
                || releaseVersion < 1) {
            throw new IllegalArgumentException("固定列表发布坐标不完整");
        }
        EntityDefinition definition = definitionMapper
                .findByEntityCode(entityCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "实体不存在: " + entityCode));
        EntityListConfig draft = listConfigMapper.findByEntityIdAndListKey(
                definition.getId(), listKey);
        EntityListConfig config = publishedRuntimeService.resolveViewCompositionConfig(
                draft, releaseId, releaseVersion);
        if (config == null
                || !entityCode.equals(config.getEntityCode())
                || !listKey.equals(config.getListKey())
                || !Boolean.TRUE.equals(config.getPublishedSnapshot())
                || !Boolean.TRUE.equals(config.getPinnedRelease())
                || !releaseId.equals(config.getActiveReleaseId())
                || !Integer.valueOf(releaseVersion).equals(config.getPublishedVersion())) {
            throw new IllegalArgumentException("列表不存在或发布版本不匹配: " + listKey);
        }
        return requireEmbedSafePinnedList(config);
    }

    /**
     * Embed 铆定列表只允许平台内建的静态查询与渲染链。
     *
     * <p>管理端发布器会拒绝这些扩展点，但运行时仍必须复验历史或被篡改的
     * Release，不能让自定义 Provider、DataSource、组件或组合内容进入 iframe
     * 权限边界。事件绑定不会被映射到 {@link EntityListConfig}，且 pinned 查询始终
     * 跳过 UI Event；其余可执行坐标在此显式关闭。</p>
     */
    private EntityListConfig requireEmbedSafePinnedList(EntityListConfig config) {
        if (StringUtils.hasText(config.getCustomComponent())
                || StringUtils.hasText(config.getQueryProviderCode())
                || StringUtils.hasText(config.getQueryDataSourceId())
                || StringUtils.hasText(config.getQueryOperationCode())
                || (config.getViewCompositions() != null
                && !config.getViewCompositions().isEmpty())) {
            throw new IllegalStateException(
                    "Embed 固定列表发布版本包含不受信扩展");
        }
        List<EntityListField> fields = config.getRuntimeFields() == null
                ? List.of() : config.getRuntimeFields();
        for (EntityListField field : fields) {
            if (field == null
                    || StringUtils.hasText(field.getRenderComponent())
                    || StringUtils.hasText(field.getTemplateId())
                    || StringUtils.hasText(field.getDataSourceId())
                    || StringUtils.hasText(field.getDataSourceOperationCode())) {
                throw new IllegalStateException(
                        "Embed 固定列表字段包含不受信扩展");
            }
            String source = field.getDataSourceType();
            if (StringUtils.hasText(source)
                    && !EMBED_SAFE_LIST_FIELD_SOURCES.contains(
                    source.trim().toUpperCase(Locale.ROOT))) {
                throw new IllegalStateException(
                        "Embed 固定列表字段数据源不受信");
            }
        }
        return config;
    }

    private UiViewCompositionTokenService.Claims
            verifyViewCompositionContext(String token) {
        return StringUtils.hasText(token)
                ? viewCompositionTokenService.verifyListContext(token)
                : null;
    }

    private void requireCompositionTarget(
            UiViewCompositionTokenService.Claims context,
            String entityCode,
            String requestedReleaseId,
            Integer requestedReleaseVersion) {
        if (!Objects.equals(
                entityCode, context.targetEntityCode())
                || (StringUtils.hasText(requestedReleaseId)
                && !Objects.equals(
                requestedReleaseId,
                context.targetReleaseId()))
                || (requestedReleaseVersion != null
                && !Objects.equals(
                requestedReleaseVersion,
                context.targetReleaseVersion()))) {
            throw new ForbiddenException(
                    "关联内容列表上下文与请求目标不一致");
        }
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

    /**
     * 同一字段上的 View 固定条件、Launch Context 和客户端条件按 AND 语义组合。
     * 当前列表过滤模型无法表达同字段的两个不同等值条件，因此冲突时必须返回空集，不能让后
     * 合并条件覆盖先前的租户/范围约束。
     */
    private boolean hasTrustedFilterConflict(
            Map<String, Object> current,
            Map<String, Object> trusted) {
        if (trusted == null || trusted.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, Object> entry : trusted.entrySet()) {
            if (current.containsKey(entry.getKey())
                    && !Objects.equals(current.get(entry.getKey()), entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    private PageResult<?> emptyPage(EntityListQueryRequest request) {
        return new PageResult<>(
                List.of(),
                0,
                Math.max(1, request.getPageNum()),
                Math.max(1, Math.min(200, request.getPageSize())));
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
