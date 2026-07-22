package com.workflow.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.BusinessConflictException;
import com.workflow.common.BusinessForbiddenException;
import com.workflow.common.UserContext;
import com.workflow.common.json.JsonDocumentCodec;
import com.workflow.contracts.entity.list.DataScopePlan;
import com.workflow.dto.UiDataSourceExecuteRequest;
import com.workflow.dto.permission.DataPermissionResult;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityForm;
import com.workflow.entity.EntityFormField;
import com.workflow.entity.EntityFormNode;
import com.workflow.entity.EntityListConfig;
import com.workflow.entity.EntityListField;
import com.workflow.entity.SysUser;
import com.workflow.entity.UiConfigRelease;
import com.workflow.entity.UiDataSourceDefinition;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityFormFieldMapper;
import com.workflow.mapper.EntityFormMapper;
import com.workflow.mapper.EntityFormNodeMapper;
import com.workflow.mapper.EntityListConfigMapper;
import com.workflow.mapper.EntityListFieldMapper;
import com.workflow.mapper.SysMenuMapper;
import com.workflow.mapper.UiConfigReleaseMapper;
import com.workflow.service.permission.DataPermissionEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UiDataSourceExecutionAccessService {

    private static final String FORM = "FORM";
    private static final String LIST = "LIST";
    private static final Set<String> RESERVED_REQUEST_KEYS = Set.of(
            "idempotencykey",
            "datascopeplan",
            "permissionsummary",
            "trustedruntimecontext",
            "authenticateduser",
            "userid",
            "username",
            "tenantid",
            "orgid",
            "organizationid",
            "deptid",
            "departmentid",
            "releaseid",
            "releaseversion",
            "publishedreleaseid");

    private final UiConfigReleaseMapper releaseMapper;
    private final EntityFormMapper formMapper;
    private final EntityFormNodeMapper formNodeMapper;
    private final EntityFormFieldMapper formFieldMapper;
    private final EntityListConfigMapper listMapper;
    private final EntityListFieldMapper listFieldMapper;
    private final EntityDefinitionMapper definitionMapper;
    private final SysMenuMapper menuMapper;
    private final SysUserService userService;
    private final DataPermissionEngine dataPermissionEngine;
    private final UiConfigurationAccessService configurationAccessService;
    private final UiConfigReleaseService releaseService;
    private final JsonDocumentCodec codec;
    private final ObjectMapper objectMapper;

    public UiDataSourceExecutionAuthorization authorizePreview(
            UiDataSourceDefinition definition,
            UiDataSourceExecuteRequest request) {
        Origin origin = resolveOrigin(request);
        requirePreviewAccess(origin);
        rejectTrustedMetadata(definition, request);
        ConfigTarget target = requireTarget(origin);
        String bindingPath = findDraftBinding(
                origin,
                normalize(request.getUsage()),
                definition.getId());
        if (!StringUtils.hasText(bindingPath)) {
            throw forbidden(
                    "UI_DATA_SOURCE_DRAFT_BINDING_REQUIRED",
                    "数据源预览必须来自当前管理员可维护的 FORM/LIST 草稿绑定");
        }
        requireScopeCompatibility(definition, origin, target);
        return authorization(
                true,
                origin,
                null,
                null,
                bindingPath,
                target,
                request);
    }

    public UiDataSourceExecutionAuthorization authorizePublished(
            UiDataSourceDefinition definition,
            UiDataSourceExecuteRequest request) {
        Origin origin = resolveOrigin(request);
        rejectTrustedMetadata(definition, request);
        ConfigTarget target = requireTarget(origin);
        requireListRuntimeAccess(origin, target);
        UiConfigRelease release = resolvePublishedRelease(
                origin,
                target,
                request);
        Map<String, Object> snapshot =
                releaseService.verifiedReleaseSnapshot(release);
        String bindingPath = findPublishedBinding(
                origin,
                snapshot,
                normalize(request.getUsage()),
                definition.getId());
        if (!StringUtils.hasText(bindingPath)) {
            throw forbidden(
                    "UI_DATA_SOURCE_PUBLISHED_BINDING_REQUIRED",
                    "当前 ACTIVE 发布版本未绑定该数据源或使用位置");
        }
        requireScopeCompatibility(definition, origin, target);
        return authorization(
                false,
                origin,
                release.getId(),
                release.getVersion(),
                bindingPath,
                target,
                request);
    }

    private UiConfigRelease resolvePublishedRelease(
            Origin origin,
            ConfigTarget target,
            UiDataSourceExecuteRequest request) {
        if (request.isServerPinnedRelease()) {
            if (!StringUtils.hasText(
                    request.getServerIdempotencyKey())) {
                throw forbidden(
                        "UI_DATA_SOURCE_TRUSTED_EXECUTION_REQUIRED",
                        "历史发布版本只能由服务端可信提交链路执行");
            }
            if (!StringUtils.hasText(request.getReleaseId())
                    || request.getReleaseVersion() == null) {
                throw conflict(
                        "UI_DATA_SOURCE_PINNED_RELEASE_REQUIRED",
                        "服务端固定版本执行缺少发布版本标识");
            }
            UiConfigRelease release =
                    releaseMapper.selectById(
                            request.getReleaseId());
            if (release == null
                    || !Objects.equals(
                    origin.configType(),
                    release.getConfigType())
                    || !Objects.equals(
                    origin.configId(),
                    release.getConfigId())
                    || !Objects.equals(
                    request.getReleaseVersion(),
                    release.getVersion())
                    || !StringUtils.hasText(
                    release.getSnapshotDocument())) {
                throw conflict(
                        "UI_DATA_SOURCE_PINNED_RELEASE_CONFLICT",
                        "服务端固定的UI发布版本不存在或与流程快照不一致");
            }
            return release;
        }

        UiConfigRelease release = releaseMapper.findActive(
                origin.configType(),
                origin.configId());
        if (release == null
                || !StringUtils.hasText(
                release.getSnapshotDocument())) {
            throw conflict(
                    "UI_DATA_SOURCE_RELEASE_REQUIRED",
                    "运行时数据源只能来自已发布并激活的 FORM/LIST 配置");
        }
        requireActiveOwnerRelease(origin, target, release);
        if (StringUtils.hasText(request.getReleaseId())
                && !Objects.equals(
                request.getReleaseId(),
                release.getId())) {
            throw conflict(
                    "UI_DATA_SOURCE_RELEASE_CONFLICT",
                    "客户端配置版本已过期，请刷新页面后重试");
        }
        if (request.getReleaseVersion() != null
                && !Objects.equals(
                request.getReleaseVersion(),
                release.getVersion())) {
            throw conflict(
                    "UI_DATA_SOURCE_RELEASE_CONFLICT",
                    "客户端配置版本已过期，请刷新页面后重试");
        }
        return release;
    }

    private UiDataSourceExecutionAuthorization authorization(
            boolean preview,
            Origin origin,
            String releaseId,
            Integer releaseVersion,
            String bindingPath,
            ConfigTarget target,
            UiDataSourceExecuteRequest request) {
        requireClaimConsistency(request, origin, target);
        SysUser user = currentUser();
        DataScopePlan plan = permissionPlan(
                target.entityCode(),
                target.listKey(),
                user);
        Map<String, Object> requestContext = sanitizeContext(
                request == null ? null : request.getContext());
        return new UiDataSourceExecutionAuthorization(
                preview,
                origin.configType(),
                origin.configId(),
                releaseId,
                releaseVersion,
                bindingPath,
                normalize(request.getUsage()),
                target.entityId(),
                target.entityCode(),
                target.listKey(),
                user,
                plan,
                requestContext,
                request.getServerIdempotencyKey());
    }

    private Origin resolveOrigin(UiDataSourceExecuteRequest request) {
        if (request == null) {
            throw originRequired();
        }
        Map<String, Object> context = request.getContext() == null
                ? Map.of() : request.getContext();
        String contextFormId = text(context.get("formId"));
        String contextListId = firstText(
                context.get("listId"),
                context.get("listConfigId"));
        if (StringUtils.hasText(contextFormId)
                && StringUtils.hasText(contextListId)) {
            throw spoofed("请求同时声明 FORM 和 LIST 来源");
        }
        String explicitType = normalize(request.getConfigType());
        String inferredType = StringUtils.hasText(contextFormId)
                ? FORM
                : StringUtils.hasText(contextListId) ? LIST : "";
        if (StringUtils.hasText(explicitType)
                && StringUtils.hasText(inferredType)
                && !explicitType.equals(inferredType)) {
            throw spoofed("请求配置类型与运行上下文不一致");
        }
        String configType = StringUtils.hasText(explicitType)
                ? explicitType : inferredType;
        if (!Set.of(FORM, LIST).contains(configType)) {
            throw originRequired();
        }
        String contextId = FORM.equals(configType)
                ? contextFormId : contextListId;
        if (StringUtils.hasText(request.getConfigId())
                && StringUtils.hasText(contextId)
                && !Objects.equals(request.getConfigId(), contextId)) {
            throw spoofed("请求配置 ID 与运行上下文不一致");
        }
        String configId = firstText(request.getConfigId(), contextId);
        if (!StringUtils.hasText(configId)) {
            throw originRequired();
        }
        return new Origin(configType, configId);
    }

    private ConfigTarget requireTarget(Origin origin) {
        if (FORM.equals(origin.configType())) {
            EntityForm form = formMapper.selectById(origin.configId());
            if (form == null) {
                throw conflict(
                        "UI_DATA_SOURCE_CONFIG_NOT_FOUND",
                        "数据源来源表单不存在或已删除");
            }
            EntityDefinition entity = definitionMapper.selectById(
                    form.getEntityId());
            if (entity == null) {
                throw conflict(
                        "UI_DATA_SOURCE_ENTITY_NOT_FOUND",
                        "数据源来源表单绑定的实体不存在");
            }
            return new ConfigTarget(
                    form.getEntityId(),
                    entity.getEntityCode(),
                    null,
                    form.getActiveReleaseId(),
                    null,
                    form,
                    null);
        }
        EntityListConfig list = listMapper.selectById(origin.configId());
        if (list == null) {
            throw conflict(
                    "UI_DATA_SOURCE_CONFIG_NOT_FOUND",
                    "数据源来源列表不存在或已删除");
        }
        EntityDefinition entity = StringUtils.hasText(list.getEntityId())
                ? definitionMapper.selectById(list.getEntityId())
                : definitionMapper.findByEntityCode(list.getEntityCode())
                        .orElse(null);
        if (entity == null) {
            throw conflict(
                    "UI_DATA_SOURCE_ENTITY_NOT_FOUND",
                    "数据源来源列表绑定的实体不存在");
        }
        String entityCode = firstText(list.getEntityCode(), entity.getEntityCode());
        return new ConfigTarget(
                entity.getId(),
                entityCode,
                list.getListKey(),
                list.getActiveReleaseId(),
                list.getAccessPermissionCode(),
                null,
                list);
    }

    private String findDraftBinding(
            Origin origin,
            String usage,
            String sourceId) {
        if (FORM.equals(origin.configType())) {
            EntityForm form = formMapper.selectById(origin.configId());
            List<Map<String, Object>> owners = new ArrayList<>();
            owners.add(objectMapper.convertValue(
                    form,
                    new TypeReference<Map<String, Object>>() {}));
            for (EntityFormNode node : formNodeMapper.findByFormId(origin.configId())) {
                owners.add(objectMapper.convertValue(
                        node,
                        new TypeReference<Map<String, Object>>() {}));
            }
            for (EntityFormField field : formFieldMapper.selectByFormId(origin.configId())) {
                owners.add(objectMapper.convertValue(
                        field,
                        new TypeReference<Map<String, Object>>() {}));
            }
            return findFormBinding(owners, usage, sourceId, "$.draft.form");
        }
        EntityListConfig list = listMapper.selectById(origin.configId());
        List<EntityListField> fields =
                listFieldMapper.findByListConfigId(origin.configId());
        return findListBinding(
                objectMapper.convertValue(
                        list,
                        new TypeReference<Map<String, Object>>() {}),
                objectMapper.convertValue(
                        fields,
                        new TypeReference<List<Map<String, Object>>>() {}),
                usage,
                sourceId,
                "$.draft.list");
    }

    private String findPublishedBinding(
            Origin origin,
            Map<String, Object> snapshot,
            String usage,
            String sourceId) {
        String snapshotType = normalize(text(snapshot.get("configType")));
        if (!origin.configType().equals(snapshotType)) {
            throw conflict(
                    "UI_DATA_SOURCE_RELEASE_CONFLICT",
                    "ACTIVE 发布快照类型与请求来源不一致");
        }
        if (FORM.equals(origin.configType())) {
            List<Map<String, Object>> owners = new ArrayList<>();
            addMap(owners, snapshot.get("form"));
            addMaps(owners, snapshot.get("nodes"));
            addMaps(owners, snapshot.get("legacyFields"));
            return findFormBinding(owners, usage, sourceId, "$.release.form");
        }
        Map<String, Object> list = stringMap(snapshot.get("list"));
        return findListBinding(
                list,
                mapList(list.get("fields")),
                usage,
                sourceId,
                "$.release.list");
    }

    private String findFormBinding(
            List<Map<String, Object>> owners,
            String usage,
            String sourceId,
            String basePath) {
        for (int index = 0; index < owners.size(); index++) {
            Map<String, Object> owner = owners.get(index);
            String ownerPath = basePath + "[" + index + "]";
            String bindingPath = findOwnerBinding(
                    owner,
                    usage,
                    sourceId,
                    ownerPath);
            if (StringUtils.hasText(bindingPath)) {
                return bindingPath;
            }
            Object initConfig = owner.get("initConfig");
            Map<String, Object> init = parseObject(
                    initConfig,
                    "表单初始化配置");
            if (!init.isEmpty()) {
                bindingPath = findConfiguredBinding(
                        init,
                        usage,
                        sourceId,
                        ownerPath + ".initConfig");
                if (StringUtils.hasText(bindingPath)) {
                    return bindingPath;
                }
            }
        }
        return null;
    }

    private String findListBinding(
            Map<String, Object> list,
            List<Map<String, Object>> fields,
            String usage,
            String sourceId,
            String basePath) {
        if ("LIST_QUERY".equals(usage)
                && sourceId.equals(text(list.get("queryDataSourceId")))) {
            return basePath + ".queryDataSourceId";
        }
        String ownerBinding = findOwnerBinding(
                list,
                usage,
                sourceId,
                basePath);
        if (StringUtils.hasText(ownerBinding)) {
            return ownerBinding;
        }
        for (int index = 0; index < fields.size(); index++) {
            Map<String, Object> field = fields.get(index);
            if ("LIST_COLUMN".equals(usage)
                    && sourceId.equals(text(field.get("dataSourceId")))) {
                return basePath + ".fields[" + index + "].dataSourceId";
            }
            String bindingPath = findOwnerBinding(
                    field,
                    usage,
                    sourceId,
                    basePath + ".fields[" + index + "]");
            if (StringUtils.hasText(bindingPath)) {
                return bindingPath;
            }
        }
        return null;
    }

    private String findOwnerBinding(
            Map<String, Object> owner,
            String usage,
            String sourceId,
            String ownerPath) {
        Map<String, Object> bindings = parseObject(
                firstNonNull(
                        owner.get("dataSourceBindings"),
                        owner.get("dataSourceBindingsDocument")),
                "数据源绑定");
        return findConfiguredBinding(
                bindings,
                usage,
                sourceId,
                ownerPath + ".dataSourceBindings");
    }

    private String findConfiguredBinding(
            Map<String, Object> bindings,
            String usage,
            String sourceId,
            String path) {
        if (bindings == null || bindings.isEmpty()) {
            return null;
        }
        Object configured = null;
        String matchedKey = usage;
        for (Map.Entry<String, Object> entry : bindings.entrySet()) {
            if (usage.equals(normalize(entry.getKey()))) {
                configured = entry.getValue();
                matchedKey = entry.getKey();
                break;
            }
        }
        if (configured == null) {
            if (usage.equals(normalize(text(bindings.get("usage"))))
                    && sourceId.equals(sourceId(bindings))) {
                return path;
            }
            return null;
        }
        return containsSource(configured, sourceId)
                ? path + "." + matchedKey
                : null;
    }

    private boolean containsSource(Object configured, String sourceId) {
        if (configured instanceof String text) {
            return sourceId.equals(text);
        }
        if (configured instanceof Map<?, ?> map) {
            return sourceId.equals(sourceId(map));
        }
        if (configured instanceof List<?> list) {
            return list.stream().anyMatch(item -> containsSource(item, sourceId));
        }
        return false;
    }

    private String sourceId(Map<?, ?> binding) {
        return firstText(binding.get("sourceId"), binding.get("id"));
    }

    private void requirePreviewAccess(Origin origin) {
        if (FORM.equals(origin.configType())) {
            configurationAccessService.requireFormAccess(origin.configId());
        } else {
            configurationAccessService.requireListAccess(origin.configId());
        }
    }

    private void requireListRuntimeAccess(
            Origin origin,
            ConfigTarget target) {
        if (!LIST.equals(origin.configType())) {
            return;
        }
        String permission = StringUtils.hasText(target.accessPermissionCode())
                ? target.accessPermissionCode()
                : "entity:"
                        + target.entityCode().toLowerCase(Locale.ROOT)
                        + ":list";
        Set<String> permissions = menuMapper.selectPermsByUserId(
                UserContext.getUserId());
        if (permissions == null || !permissions.contains(permission)) {
            throw forbidden(
                    "UI_DATA_SOURCE_LIST_ACCESS_DENIED",
                    "当前用户没有权限访问该已发布列表的数据源");
        }
    }

    private void requireActiveOwnerRelease(
            Origin origin,
            ConfigTarget target,
            UiConfigRelease release) {
        if (!Objects.equals(target.activeReleaseId(), release.getId())) {
            throw conflict(
                    "UI_DATA_SOURCE_RELEASE_CONFLICT",
                    origin.configType()
                            + " 当前激活版本与发布记录不一致，请重新发布或激活");
        }
    }

    private void requireScopeCompatibility(
            UiDataSourceDefinition definition,
            Origin origin,
            ConfigTarget target) {
        String scopeType = normalize(definition.getScopeType());
        boolean compatible = switch (scopeType) {
            case "", "GLOBAL" -> true;
            case "ENTITY" -> Objects.equals(
                    definition.getScopeId(),
                    target.entityId());
            case FORM -> FORM.equals(origin.configType())
                    && Objects.equals(definition.getScopeId(), origin.configId());
            case LIST -> LIST.equals(origin.configType())
                    && Objects.equals(definition.getScopeId(), origin.configId());
            default -> false;
        };
        if (!compatible) {
            throw forbidden(
                    "UI_DATA_SOURCE_SCOPE_MISMATCH",
                    "数据源作用域与请求的 FORM/LIST 发布绑定不一致");
        }
    }

    private void requireClaimConsistency(
            UiDataSourceExecuteRequest request,
            Origin origin,
            ConfigTarget target) {
        if (StringUtils.hasText(request.getEntityCode())
                && !request.getEntityCode().equals(target.entityCode())) {
            throw spoofed("请求 entityCode 与发布配置绑定实体不一致");
        }
        if (LIST.equals(origin.configType())
                && StringUtils.hasText(request.getListKey())
                && !request.getListKey().equals(target.listKey())) {
            throw spoofed("请求 listKey 与发布列表不一致");
        }
    }

    private SysUser currentUser() {
        String userId = UserContext.getUserId();
        if (!StringUtils.hasText(userId)) {
            throw forbidden(
                    "UI_DATA_SOURCE_USER_CONTEXT_REQUIRED",
                    "数据源执行缺少已认证用户上下文");
        }
        SysUser user = userService.getById(userId);
        if (user == null
                || !SysUser.Status.ENABLED.getValue().equals(user.getStatus())) {
            throw forbidden(
                    "UI_DATA_SOURCE_USER_DISABLED",
                    "当前用户不存在或已停用");
        }
        return user;
    }

    private DataScopePlan permissionPlan(
            String entityCode,
            String listKey,
            SysUser user) {
        DataPermissionResult permission =
                dataPermissionEngine.calculatePermission(
                        entityCode,
                        listKey,
                        user);
        if (permission == null) {
            throw conflict(
                    "UI_DATA_SOURCE_PERMISSION_PLAN_UNAVAILABLE",
                    "数据权限引擎未返回可验证的权限计划");
        }
        String sqlFragment = permission.isHasPermission()
                ? (permission.isNeedFilter()
                        ? permission.getSqlCondition()
                        : "1=1")
                : "1=0";
        if (!StringUtils.hasText(sqlFragment)) {
            throw conflict(
                    "UI_DATA_SOURCE_PERMISSION_PLAN_UNAVAILABLE",
                    "数据权限计划缺少有效的范围条件");
        }
        return new DataScopePlan(
                permission.isHasPermission(),
                sqlFragment,
                Map.of(),
                List.of(),
                permission.getMatchedRuleNames() == null
                        ? List.of() : List.copyOf(permission.getMatchedRuleNames()),
                permission.getExplanation(),
                permission.getReleaseVersion());
    }

    private void rejectTrustedMetadata(
            UiDataSourceDefinition definition,
            UiDataSourceExecuteRequest request) {
        if (!"INTEGRATION_CONNECTOR".equals(normalize(
                definition.getSourceType()))
                || request == null) {
            return;
        }
        String rejected = reservedKey(
                request.getContext(),
                request.getServerIdempotencyKey());
        if (!StringUtils.hasText(rejected)) {
            rejected = reservedKey(
                    request.getInput(),
                    request.getServerIdempotencyKey());
        }
        if (StringUtils.hasText(rejected)) {
            throw spoofed(
                    "Connector 请求不能提交服务端保留的可信字段: "
                            + rejected);
        }
    }

    private String reservedKey(
            Map<String, Object> value,
            String trustedIdempotencyKey) {
        if (value == null) {
            return null;
        }
        return value.keySet().stream()
                .filter(Objects::nonNull)
                .filter(key -> {
                    String normalized = key.replace("_", "")
                            .replace("-", "")
                            .toLowerCase(Locale.ROOT);
                    if ("idempotencykey".equals(normalized)
                            && StringUtils.hasText(trustedIdempotencyKey)
                            && Objects.equals(
                                    trustedIdempotencyKey,
                                    text(value.get(key)))) {
                        return false;
                    }
                    return RESERVED_REQUEST_KEYS.contains(normalized);
                })
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> sanitizeContext(
            Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        context.forEach((key, value) -> {
            String normalizedKey = key == null
                    ? ""
                    : key.replace("_", "")
                            .replace("-", "")
                            .toLowerCase(Locale.ROOT);
            if (!RESERVED_REQUEST_KEYS.contains(normalizedKey)) {
                result.put(key, value);
            }
        });
        return Map.copyOf(result);
    }

    private Map<String, Object> parseObject(
            Object value,
            String label) {
        if (value instanceof Map<?, ?> map) {
            return stringMap(map);
        }
        if (value instanceof String document
                && StringUtils.hasText(document)) {
            return codec.readObject(document, label);
        }
        return Map.of();
    }

    private Map<String, Object> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        return stringMap(map);
    }

    private Map<String, Object> stringMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) ->
                result.put(String.valueOf(key), value));
        return result;
    }

    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        addMaps(result, list);
        return result;
    }

    private void addMap(
            List<Map<String, Object>> target,
            Object value) {
        Map<String, Object> map = stringMap(value);
        if (!map.isEmpty()) {
            target.add(map);
        }
    }

    private void addMaps(
            List<Map<String, Object>> target,
            Object value) {
        if (!(value instanceof List<?> list)) {
            return;
        }
        list.forEach(item -> addMap(target, item));
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String candidate = text(value);
            if (StringUtils.hasText(candidate)) {
                return candidate.trim();
            }
        }
        return null;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT)
                : "";
    }

    private BusinessForbiddenException originRequired() {
        return forbidden(
                "UI_DATA_SOURCE_EXECUTION_ORIGIN_REQUIRED",
                "数据源执行必须声明可验证的 FORM/LIST 配置来源");
    }

    private BusinessForbiddenException spoofed(String message) {
        return forbidden(
                "UI_DATA_SOURCE_EXECUTION_CONTEXT_SPOOFED",
                message);
    }

    private BusinessForbiddenException forbidden(
            String errorCode,
            String message) {
        return new BusinessForbiddenException(errorCode, message);
    }

    private BusinessConflictException conflict(
            String errorCode,
            String message) {
        return new BusinessConflictException(errorCode, message);
    }

    private record Origin(
            String configType,
            String configId) {
    }

    private record ConfigTarget(
            String entityId,
            String entityCode,
            String listKey,
            String activeReleaseId,
            String accessPermissionCode,
            EntityForm form,
            EntityListConfig list) {
    }
}
