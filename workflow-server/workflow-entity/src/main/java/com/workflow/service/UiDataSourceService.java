package com.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.workflow.common.BusinessConflictException;
import com.workflow.common.BusinessForbiddenException;
import com.workflow.common.PageResult;
import com.workflow.common.RevisionConflictException;
import com.workflow.common.UserContext;
import com.workflow.common.json.JsonDocumentCodec;
import com.workflow.contracts.entity.list.DataScopePlan;
import com.workflow.contracts.integration.IntegrationConnector;
import com.workflow.contracts.integration.IntegrationRequest;
import com.workflow.contracts.integration.IntegrationResult;
import com.workflow.contracts.integration.IntegrationRuntimeContext;
import com.workflow.contracts.ui.UiDataSourceContext;
import com.workflow.contracts.ui.UiDataSourceProvider;
import com.workflow.dto.UiDataSourceExecuteRequest;
import com.workflow.dto.UiDataSourceSaveRequest;
import com.workflow.entity.EntityForm;
import com.workflow.entity.EntityListConfig;
import com.workflow.entity.SysDictItem;
import com.workflow.entity.SysUser;
import com.workflow.entity.UiDataSourceDefinition;
import com.workflow.mapper.EntityFormMapper;
import com.workflow.mapper.EntityListConfigMapper;
import com.workflow.mapper.UiDataSourceDefinitionMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * UI 数据源定义与执行服务，负责数据源配置的校验、保存、查询与可信执行。
 *
 * <p>支持实体查询、字典、静态选项、注册提供器、集成连接器、运行时上下文和结构化计算
 * 等数据源类型，配置中禁止 SQL/脚本/URL 等危险字段；执行链路经
 * {@link UiDataSourceExecutionAccessService} 授权后按数据源类型分派，
 * 并提供带 TTL 的结果缓存。</p>
 */
@Service
public class UiDataSourceService {

    private static final Set<String> SOURCE_TYPES = Set.of(
            "ENTITY_QUERY", "DICTIONARY", "STATIC_OPTIONS",
            "REGISTERED_PROVIDER", "INTEGRATION_CONNECTOR",
            "RUNTIME_CONTEXT", "STRUCTURED_COMPUTE");
    private static final Set<String> SCOPE_TYPES =
            Set.of("GLOBAL", "ENTITY", "FORM", "LIST");
    private static final Set<String> USAGES = Set.of(
            "FORM_INIT", "FIELD_OPTIONS", "FIELD_DEFAULT", "FIELD_COMPUTE",
            "SUBFORM_ROWS", "LIST_QUERY", "LIST_COLUMN", "AFTER_LOAD", "BEFORE_SUBMIT");
    /** 配置中禁止的危险字段名，防止注入任意 SQL/脚本/URL。 */
    private static final Set<String> FORBIDDEN_KEYS =
            Set.of("sql", "script", "url", "jdbcUrl", "command", "expression");
    private static final Set<String> SCHEMA_TYPES =
            Set.of("object", "array", "string", "number", "integer", "boolean");
    private final UiDataSourceDefinitionMapper mapper;
    private final EntityFormMapper formMapper;
    private final EntityListConfigMapper listMapper;
    private final EntityDefinitionAccessPolicy entityAccessPolicy;
    private final EntityDataDynamicService dynamicService;
    private final SysDictItemService dictItemService;
    private final UiDataSourceExecutionAccessService executionAccessService;
    private final List<UiDataSourceProvider> providers;
    private final List<IntegrationConnector> connectors;
    private final JsonDocumentCodec codec;
    private final TaskExecutor taskExecutor;

    /** 数据源执行结果缓存，按 key+版本+内容哈希索引。 */
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * 构造数据源服务，注入数据源提供器、集成连接器和异步执行器。
     *
     * @param mapper                   数据源定义 Mapper
     * @param formMapper               表单 Mapper
     * @param listMapper               列表配置 Mapper
     * @param entityAccessPolicy       实体访问策略
     * @param dynamicService           实体数据动态服务
     * @param dictItemService          字典项服务
     * @param executionAccessService   数据源执行访问控制服务
     * @param providers                注册的数据源提供器集合
     * @param connectors               集成连接器集合
     * @param codec                    JSON 文档编解码器
     * @param taskExecutor             应用异步任务执行器
     */
    public UiDataSourceService(
            UiDataSourceDefinitionMapper mapper,
            EntityFormMapper formMapper,
            EntityListConfigMapper listMapper,
            EntityDefinitionAccessPolicy entityAccessPolicy,
            EntityDataDynamicService dynamicService,
            SysDictItemService dictItemService,
            UiDataSourceExecutionAccessService executionAccessService,
            List<UiDataSourceProvider> providers,
            List<IntegrationConnector> connectors,
            JsonDocumentCodec codec,
            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        this.mapper = mapper;
        this.formMapper = formMapper;
        this.listMapper = listMapper;
        this.entityAccessPolicy = entityAccessPolicy;
        this.dynamicService = dynamicService;
        this.dictItemService = dictItemService;
        this.executionAccessService = executionAccessService;
        this.providers = providers;
        this.connectors = connectors;
        this.codec = codec;
        this.taskExecutor = taskExecutor;
    }

    /**
     * 按作用域类型、作用域ID和数据源类型查询数据源定义列表。
     *
     * @param scopeType  作用域类型，为空忽略
     * @param scopeId    作用域ID，为空忽略
     * @param sourceType 数据源类型，为空忽略
     * @return 数据源定义列表
     */
    public List<UiDataSourceDefinition> list(
            String scopeType,
            String scopeId,
            String sourceType) {
        LambdaQueryWrapper<UiDataSourceDefinition> query = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(scopeType)) {
            query.eq(UiDataSourceDefinition::getScopeType, normalize(scopeType));
        }
        if (StringUtils.hasText(scopeId)) {
            query.eq(UiDataSourceDefinition::getScopeId, scopeId);
        }
        if (StringUtils.hasText(sourceType)) {
            query.eq(UiDataSourceDefinition::getSourceType, normalize(sourceType));
        }
        query.eq(UiDataSourceDefinition::getDeleted, 0)
                .orderByAsc(UiDataSourceDefinition::getSourceCode);
        return mapper.selectList(query);
    }

    public Map<String, Object> catalog() {
        List<Map<String, Object>> providerOptions = providers.stream()
                .map(provider -> Map.<String, Object>of(
                        "code", provider.getCode(),
                        "name", provider.getDisplayName(),
                        "schema", provider.configurationSchema()))
                .toList();
        List<Map<String, Object>> connectorOptions = connectors.stream()
                .map(connector -> Map.<String, Object>of(
                        "code", connector.code()))
                .toList();
        Map<String, Object> catalog = new LinkedHashMap<>();
        catalog.put("sourceTypes", SOURCE_TYPES);
        catalog.put("usages", USAGES);
        catalog.put("scopeTypes", SCOPE_TYPES);
        catalog.put("providers", providerOptions);
        catalog.put("connectors", connectorOptions);
        catalog.put("failurePolicies", List.of("FAIL", "EMPTY", "NULL"));
        return catalog;
    }

    @Transactional(rollbackFor = Exception.class)
    public UiDataSourceDefinition save(UiDataSourceSaveRequest request) {
        validateRequest(request);
        validateSchemaDefinition(request.getInputSchema(), "数据源输入Schema");
        validateSchemaDefinition(request.getOutputSchema(), "数据源输出Schema");
        UiDataSourceDefinition current = StringUtils.hasText(request.getId())
                ? mapper.selectById(request.getId())
                : null;
        if (current != null) {
            requireRevision(request.getExpectedRevision(), current);
        }
        UiDataSourceDefinition value =
                current == null ? new UiDataSourceDefinition() : current;
        value.setSourceCode(request.getSourceCode().trim());
        value.setSourceName(request.getSourceName().trim());
        value.setSourceType(normalize(request.getSourceType()));
        value.setProviderCode(blankToNull(request.getProviderCode()));
        value.setScopeType(normalize(
                StringUtils.hasText(request.getScopeType())
                        ? request.getScopeType()
                        : "GLOBAL"));
        value.setScopeId(blankToNull(request.getScopeId()));
        value.setConfigDocument(write(request.getConfig(), "数据源配置"));
        value.setInputSchemaDocument(write(request.getInputSchema(), "数据源输入Schema"));
        value.setOutputSchemaDocument(write(request.getOutputSchema(), "数据源输出Schema"));
        value.setExecutionPolicyDocument(
                write(request.getExecutionPolicy(), "数据源执行策略"));
        value.setEnabled(request.getEnabled() == null || request.getEnabled());
        value.setUpdatedAt(LocalDateTime.now());
        value.setDeleted(0);
        if (current == null) {
            value.setRevision(1);
            value.setCreatedAt(LocalDateTime.now());
            mapper.insert(value);
        } else {
            value.setRevision(current.getRevision() + 1);
            UpdateWrapper<UiDataSourceDefinition> wrapper = new UpdateWrapper<>();
            wrapper.eq("id", value.getId())
                    .eq("revision", current.getRevision())
                    .eq("deleted", 0)
                    .set("source_code", value.getSourceCode())
                    .set("source_name", value.getSourceName())
                    .set("source_type", value.getSourceType())
                    .set("provider_code", value.getProviderCode())
                    .set("scope_type", value.getScopeType())
                    .set("scope_id", value.getScopeId())
                    .set("config_document", value.getConfigDocument())
                    .set("input_schema_document", value.getInputSchemaDocument())
                    .set("output_schema_document", value.getOutputSchemaDocument())
                    .set("execution_policy_document", value.getExecutionPolicyDocument())
                    .set("enabled", value.getEnabled())
                    .set("revision", value.getRevision())
                    .set("update_time", value.getUpdatedAt());
            if (mapper.update(null, wrapper) != 1) {
                throw new RevisionConflictException(
                        "数据源已被其他人修改，请刷新后重试",
                        mapper.selectById(value.getId()));
            }
        }
        return mapper.selectById(value.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String id, Integer expectedRevision) {
        UiDataSourceDefinition current = mapper.selectById(id);
        if (current == null) {
            throw new IllegalArgumentException("数据源不存在");
        }
        requireRevision(expectedRevision, current);
        UpdateWrapper<UiDataSourceDefinition> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id)
                .eq("revision", current.getRevision())
                .eq("deleted", 0)
                .set("deleted", 1)
                .setSql("revision = revision + 1")
                .set("update_time", LocalDateTime.now());
        if (mapper.update(null, wrapper) != 1) {
            throw new RevisionConflictException(
                    "数据源已被其他人修改，请刷新后重试",
                    mapper.selectById(id));
        }
    }

    public Object preview(String id, UiDataSourceExecuteRequest request) {
        UiDataSourceDefinition definition = requireExecutableDefinition(id);
        requireUsage(request == null ? null : request.getUsage());
        UiDataSourceExecutionAuthorization authorization =
                executionAccessService.authorizePreview(
                        definition,
                        request);
        return executeAuthorized(
                definition,
                request,
                authorization);
    }

    public Object execute(String id, UiDataSourceExecuteRequest request) {
        UiDataSourceDefinition definition = requireExecutableDefinition(id);
        requireUsage(request == null ? null : request.getUsage());
        UiDataSourceExecutionAuthorization authorization =
                executionAccessService.authorizePublished(
                        definition,
                        request);
        return executeAuthorized(
                definition,
                request,
                authorization);
    }

    private UiDataSourceDefinition requireExecutableDefinition(String id) {
        UiDataSourceDefinition definition = mapper.selectById(id);
        if (definition == null
                || Integer.valueOf(1).equals(definition.getDeleted())
                || !Boolean.TRUE.equals(definition.getEnabled())) {
            throw new BusinessConflictException(
                    "UI_DATA_SOURCE_NOT_EXECUTABLE",
                    "数据源不存在、已删除或未启用");
        }
        return definition;
    }

    private Object executeAuthorized(
            UiDataSourceDefinition definition,
            UiDataSourceExecuteRequest request,
            UiDataSourceExecutionAuthorization authorization) {
        Map<String, Object> config = read(
                definition.getConfigDocument(), "数据源配置");
        Map<String, Object> input = request == null || request.getInput() == null
                ? Map.of() : request.getInput();
        Map<String, Object> inputSchema = read(
                definition.getInputSchemaDocument(), "数据源输入Schema");
        Map<String, Object> outputSchema = read(
                definition.getOutputSchemaDocument(), "数据源输出Schema");
        validateSchemaDefinition(inputSchema, "数据源输入Schema");
        validateSchemaDefinition(outputSchema, "数据源输出Schema");
        validateSchemaValue(inputSchema, input, "数据源输入");
        Map<String, Object> policy = read(
                definition.getExecutionPolicyDocument(), "数据源执行策略");
        String cacheKey = cacheKey(
                definition,
                input,
                authorization);
        int cacheSeconds = integer(policy.get("cacheSeconds"), 0);
        CacheEntry cached = cache.get(cacheKey);
        if (cacheSeconds > 0 && cached != null && cached.expiresAt() > System.currentTimeMillis()) {
            validateSchemaValue(outputSchema, cached.value(), "数据源输出");
            return cached.value();
        }
        try {
            String userId = authorization.user().getId();
            String username = authorization.user().getUsername();
            int timeoutMs = integer(policy.get("timeoutMs"), 3000);
            Object result = CompletableFuture.supplyAsync(() -> {
                UserContext.setCurrentUser(userId, username);
                try {
                    return executeInternal(
                            definition,
                            request,
                            config,
                            input,
                            authorization);
                } finally {
                    UserContext.clear();
                }
            }, taskExecutor).get(timeoutMs, TimeUnit.MILLISECONDS);
            validateSchemaValue(outputSchema, result, "数据源输出");
            if (cacheSeconds > 0) {
                cache.put(cacheKey, new CacheEntry(
                        result,
                        System.currentTimeMillis() + cacheSeconds * 1000L));
            }
            return result;
        } catch (Exception exception) {
            RuntimeException failure = executionFailure(exception);
            if (isNonRecoverable(failure)) {
                throw failure;
            }
            Object fallback = handleFailure(policy, failure);
            validateSchemaValue(outputSchema, fallback, "数据源输出");
            return fallback;
        }
    }

    public Map<String, Object> validateBinding(
            String id,
            String usage) {
        UiDataSourceDefinition definition = mapper.selectById(id);
        if (definition == null || !Boolean.TRUE.equals(definition.getEnabled())) {
            throw new IllegalArgumentException("数据源不存在或未启用");
        }
        requireUsage(usage);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", true);
        result.put("sourceId", id);
        result.put("sourceType", definition.getSourceType());
        result.put("usage", normalize(usage));
        result.put("revision", definition.getRevision());
        return result;
    }

    private Object executeInternal(
            UiDataSourceDefinition definition,
            UiDataSourceExecuteRequest request,
            Map<String, Object> config,
            Map<String, Object> input,
            UiDataSourceExecutionAuthorization authorization) {
        String sourceType = normalize(definition.getSourceType());
        if ("STATIC_OPTIONS".equals(sourceType)) {
            return config.getOrDefault("options", List.of());
        }
        if ("DICTIONARY".equals(sourceType)) {
            String dictCode = text(config.get("dictCode"));
            return flattenDictionary(dictItemService.getItemTreeByDictCode(dictCode));
        }
        if ("RUNTIME_CONTEXT".equals(sourceType)) {
            return authorization.requestContext();
        }
        if ("ENTITY_QUERY".equals(sourceType)) {
            Map<String, Object> filters = input.get("filters") instanceof Map<?, ?> map
                    ? stringMap(map) : new LinkedHashMap<>();
            Set<String> allowedFilters = config.get("allowedFilters") instanceof List<?> list
                    ? list.stream().map(String::valueOf).collect(java.util.stream.Collectors.toSet())
                    : Set.of();
            if (!allowedFilters.isEmpty()) {
                filters.keySet().removeIf(key -> !allowedFilters.contains(stripSuffix(key)));
            }
            int pageNum = request.getPageNum() == null ? 1 : Math.max(1, request.getPageNum());
            int pageSize = request.getPageSize() == null
                    ? 20 : Math.max(1, Math.min(200, request.getPageSize()));
            if (!authorization.dataScopePlan().allowed()) {
                return new PageResult<>(List.of(), 0, pageNum, pageSize);
            }
            return dynamicService.findPageWithDataScopePlan(
                    authorization.entityCode(),
                    filters,
                    pageNum,
                    pageSize,
                    authorization.dataScopePlan());
        }
        if ("STRUCTURED_COMPUTE".equals(sourceType)) {
            return compute(config, input);
        }
        UiDataSourceContext context = new UiDataSourceContext(
                authorization.usage(),
                authorization.entityCode(),
                authorization.listKey(),
                authorization.user().getId(),
                authorization.requestContext(),
                authorization.user().getUsername(),
                authorization.user().getOrgId(),
                authorization.user().getOrgId(),
                authorization.user().getDeptId(),
                authorization.configType(),
                authorization.configId(),
                authorization.releaseId(),
                authorization.releaseVersion());
        if ("REGISTERED_PROVIDER".equals(sourceType)) {
            if (!authorization.dataScopePlan().allowed()) {
                throw new BusinessForbiddenException(
                        "UI_DATA_SOURCE_DATA_SCOPE_DENIED",
                        "当前用户的数据权限计划拒绝执行该 Provider");
            }
            UiDataSourceProvider provider = providers.stream()
                    .filter(item -> item.getCode().equalsIgnoreCase(
                            definition.getProviderCode()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "数据源Provider未注册: " + definition.getProviderCode()));
            return provider.execute(
                    context,
                    authorization.dataScopePlan(),
                    config,
                    input);
        }
        if ("INTEGRATION_CONNECTOR".equals(sourceType)) {
            if (!authorization.dataScopePlan().allowed()) {
                throw new BusinessForbiddenException(
                        "UI_DATA_SOURCE_DATA_SCOPE_DENIED",
                        "当前用户的数据权限计划拒绝执行该 Connector");
            }
            IntegrationConnector connector = connectors.stream()
                    .filter(item -> item.code().equalsIgnoreCase(
                            definition.getProviderCode()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Integration Connector未注册: "
                                    + definition.getProviderCode()));
            IntegrationResult result = connector.execute(
                    IntegrationRequest.builder()
                            .idempotencyKey(idempotencyKey(
                                    definition,
                                    authorization,
                                    input))
                            .operation(text(config.get("operation")))
                            .parameters(Collections.unmodifiableMap(
                                    new LinkedHashMap<>(input)))
                            .runtimeContext(integrationRuntimeContext(
                                    definition,
                                    authorization))
                            .dataScopePlan(
                                    authorization.dataScopePlan())
                            .permissionSummary(permissionSummary(
                                    authorization.dataScopePlan()))
                            .build());
            if (!result.isSuccess()) {
                throw new IllegalStateException(
                        "Connector执行失败: " + result.getMessage());
            }
            return result.getData();
        }
        throw new IllegalArgumentException("不支持的数据源类型: " + sourceType);
    }

    private Object compute(
            Map<String, Object> config,
            Map<String, Object> input) {
        String operation = normalize(text(config.get("operation")));
        List<Object> values = config.get("inputs") instanceof List<?> paths
                ? paths.stream().map(path -> resolvePath(input, String.valueOf(path))).toList()
                : new ArrayList<>(input.values());
        return switch (operation) {
            case "COALESCE" -> values.stream().filter(value -> value != null).findFirst().orElse(null);
            case "CONCAT" -> values.stream().map(value -> value == null ? "" : String.valueOf(value))
                    .collect(java.util.stream.Collectors.joining(
                            text(config.getOrDefault("separator", ""))));
            case "SUM" -> values.stream()
                    .filter(Number.class::isInstance)
                    .map(Number.class::cast)
                    .mapToDouble(Number::doubleValue)
                    .sum();
            case "IF_EQUALS" -> Objects.equals(
                    values.isEmpty() ? null : values.get(0),
                    config.get("equals"))
                    ? config.get("then") : config.get("else");
            default -> throw new IllegalArgumentException(
                    "不支持的结构化计算操作: " + operation);
        };
    }

    private void validateRequest(UiDataSourceSaveRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getSourceCode())
                || !StringUtils.hasText(request.getSourceName())) {
            throw new IllegalArgumentException("数据源编码和名称不能为空");
        }
        String sourceType = normalize(request.getSourceType());
        if (!SOURCE_TYPES.contains(sourceType)) {
            throw new IllegalArgumentException("不支持的数据源类型: " + sourceType);
        }
        String scopeType = normalize(
                StringUtils.hasText(request.getScopeType())
                        ? request.getScopeType()
                        : "GLOBAL");
        if (!SCOPE_TYPES.contains(scopeType)) {
            throw new IllegalArgumentException("不支持的数据源作用域: " + scopeType);
        }
        if (!"GLOBAL".equals(scopeType) && !StringUtils.hasText(request.getScopeId())) {
            throw new IllegalArgumentException("非全局数据源必须指定 scopeId");
        }
        validateNoForbiddenKeys(request.getConfig(), "config");
        validateExecutionPolicy(request.getExecutionPolicy());
        if (Set.of("REGISTERED_PROVIDER", "INTEGRATION_CONNECTOR").contains(sourceType)
                && !StringUtils.hasText(request.getProviderCode())) {
            throw new IllegalArgumentException("Provider/Connector编码不能为空");
        }
        requireScopeAccess(scopeType, request.getScopeId());
    }

    private void validateExecutionPolicy(Map<String, Object> policy) {
        if (policy == null) {
            return;
        }
        int timeout = policy.get("timeoutMs") instanceof Number number
                ? number.intValue() : 3000;
        if (timeout < 100 || timeout > 30000) {
            throw new IllegalArgumentException("数据源超时必须在 100 到 30000 毫秒之间");
        }
        int cacheSeconds = policy.get("cacheSeconds") instanceof Number number
                ? number.intValue() : 0;
        if (cacheSeconds < 0 || cacheSeconds > 86400) {
            throw new IllegalArgumentException("数据源缓存时间必须在 0 到 86400 秒之间");
        }
        String failure = normalize(
                String.valueOf(policy.getOrDefault("failurePolicy", "FAIL")));
        if (!Set.of("FAIL", "EMPTY", "NULL").contains(failure)) {
            throw new IllegalArgumentException("不支持的数据源失败策略: " + failure);
        }
    }

    private Object handleFailure(
            Map<String, Object> policy,
            RuntimeException exception) {
        String failure = String.valueOf(
                policy.getOrDefault("failurePolicy", "FAIL")).toUpperCase(Locale.ROOT);
        if ("EMPTY".equals(failure)) {
            return List.of();
        }
        if ("NULL".equals(failure)) {
            return null;
        }
        throw exception;
    }

    private String cacheKey(
            UiDataSourceDefinition definition,
            Map<String, Object> input,
            UiDataSourceExecutionAuthorization authorization) {
        Map<String, Object> key = new LinkedHashMap<>();
        key.put("sourceId", definition.getId());
        key.put("revision", definition.getRevision());
        key.put("usage", authorization.usage());
        key.put("configType", authorization.configType());
        key.put("configId", authorization.configId());
        key.put("releaseId", authorization.releaseId());
        key.put("releaseVersion", authorization.releaseVersion());
        key.put("entityCode", authorization.entityCode());
        key.put("listKey", authorization.listKey());
        key.put("userId", authorization.user().getId());
        key.put("tenantId", authorization.user().getOrgId());
        key.put("input", input);
        key.put("context", authorization.requestContext());
        key.put(
                "dataScopePlan",
                dataScopeFingerprint(
                        authorization.dataScopePlan()));
        return codec.canonicalize(
                codec.write(key, "数据源缓存键"),
                "数据源缓存键");
    }

    private Map<String, Object> dataScopeFingerprint(
            DataScopePlan plan) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("allowed", plan.allowed());
        value.put("sqlFragment", plan.sqlFragment());
        value.put("parameters", plan.parameters());
        value.put("requiredJoins", plan.requiredJoins());
        value.put("matchedPolicies", plan.matchedPolicies());
        value.put("releaseVersion", plan.releaseVersion());
        return value;
    }

    private IntegrationRuntimeContext integrationRuntimeContext(
            UiDataSourceDefinition definition,
            UiDataSourceExecutionAuthorization authorization) {
        SysUser user = authorization.user();
        return new IntegrationRuntimeContext(
                definition.getId(),
                authorization.usage(),
                authorization.configType(),
                authorization.configId(),
                authorization.releaseId(),
                authorization.releaseVersion(),
                authorization.entityId(),
                authorization.entityCode(),
                authorization.listKey(),
                user.getId(),
                user.getUsername(),
                user.getOrgId(),
                user.getOrgId(),
                user.getDeptId());
    }

    private Map<String, Object> permissionSummary(
            DataScopePlan plan) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("allowed", plan.allowed());
        summary.put("matchedPolicies", plan.matchedPolicies());
        summary.put("explanation", plan.explanation());
        summary.put("releaseVersion", plan.releaseVersion());
        return Collections.unmodifiableMap(summary);
    }

    private String idempotencyKey(
            UiDataSourceDefinition definition,
            UiDataSourceExecutionAuthorization authorization,
            Map<String, Object> input) {
        Map<String, Object> material = new LinkedHashMap<>();
        material.put("sourceId", definition.getId());
        material.put("sourceRevision", definition.getRevision());
        material.put("configType", authorization.configType());
        material.put("configId", authorization.configId());
        material.put("releaseId", authorization.releaseId());
        material.put("releaseVersion", authorization.releaseVersion());
        material.put("usage", authorization.usage());
        material.put("userId", authorization.user().getId());
        material.put("tenantId", authorization.user().getOrgId());
        material.put("serverSeed", authorization.idempotencySeed());
        material.put("input", input);
        String canonical = codec.canonicalize(
                codec.write(material, "Connector幂等键"),
                "Connector幂等键");
        try {
            return "ui-ds-"
                    + HexFormat.of().formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(canonical.getBytes(
                                            StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "生成 Connector 幂等键失败",
                    exception);
        }
    }

    private void validateSchemaDefinition(
            Map<String, Object> schema,
            String label) {
        if (schema == null || schema.isEmpty()) {
            return;
        }
        validateSchemaNode(schema, label, "$");
    }

    private void validateSchemaNode(
            Map<?, ?> schema,
            String label,
            String path) {
        String type = schemaType(schema, label, path);
        Object required = schema.get("required");
        if (required != null) {
            if (!(required instanceof List<?> requiredFields)) {
                throw schemaError(
                        label,
                        path + ".required 必须为字符串数组");
            }
            for (int index = 0; index < requiredFields.size(); index++) {
                Object field = requiredFields.get(index);
                if (!(field instanceof String text)
                        || !StringUtils.hasText(text)) {
                    throw schemaError(
                            label,
                            path + ".required[" + index + "] 必须为非空字符串");
                }
            }
            if (StringUtils.hasText(type) && !"object".equals(type)) {
                throw schemaError(
                        label,
                        path + ".required 只能用于 object");
            }
        }
        Object properties = schema.get("properties");
        if (properties != null) {
            if (!(properties instanceof Map<?, ?> propertySchemas)) {
                throw schemaError(
                        label,
                        path + ".properties 必须为对象");
            }
            if (StringUtils.hasText(type) && !"object".equals(type)) {
                throw schemaError(
                        label,
                        path + ".properties 只能用于 object");
            }
            for (Map.Entry<?, ?> entry : propertySchemas.entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?> childSchema)) {
                    throw schemaError(
                            label,
                            path + ".properties." + entry.getKey()
                                    + " 必须为 Schema 对象");
                }
                validateSchemaNode(
                        childSchema,
                        label,
                        path + ".properties." + entry.getKey());
            }
        }
        Object items = schema.get("items");
        if (items != null) {
            if (!(items instanceof Map<?, ?> itemSchema)) {
                throw schemaError(
                        label,
                        path + ".items 必须为 Schema 对象");
            }
            if (StringUtils.hasText(type) && !"array".equals(type)) {
                throw schemaError(
                        label,
                        path + ".items 只能用于 array");
            }
            validateSchemaNode(
                    itemSchema,
                    label,
                    path + ".items");
        }
    }

    private String schemaType(
            Map<?, ?> schema,
            String label,
            String path) {
        Object configured = schema.get("type");
        if (configured == null) {
            return "";
        }
        if (!(configured instanceof String text)
                || !StringUtils.hasText(text)) {
            throw schemaError(
                    label,
                    path + ".type 必须为非空字符串");
        }
        String type = text.trim().toLowerCase(Locale.ROOT);
        if (!SCHEMA_TYPES.contains(type)) {
            throw schemaError(
                    label,
                    path + ".type 不支持: " + text);
        }
        return type;
    }

    private void validateSchemaValue(
            Map<String, Object> schema,
            Object value,
            String label) {
        if (schema == null || schema.isEmpty()) {
            return;
        }
        validateSchemaValueNode(
                schema,
                jsonCompatibleValue(value, label),
                label,
                "$");
    }

    private Object jsonCompatibleValue(
            Object value,
            String label) {
        if (value == null
                || value instanceof Map<?, ?>
                || value instanceof List<?>
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean) {
            return value;
        }
        return codec.read(
                codec.write(value, label + " JSON转换"),
                label + " JSON转换");
    }

    private void validateSchemaValueNode(
            Map<?, ?> schema,
            Object value,
            String label,
            String path) {
        String type = schemaType(schema, label, path);
        if (!StringUtils.hasText(type)) {
            if (schema.containsKey("properties")
                    || schema.containsKey("required")) {
                type = "object";
            } else if (schema.containsKey("items")) {
                type = "array";
            }
        }
        if (StringUtils.hasText(type)
                && !matchesSchemaType(type, value)) {
            throw schemaError(
                    label,
                    path + " 类型应为 " + type
                            + "，实际为 " + actualType(value));
        }
        if ("object".equals(type)) {
            Map<?, ?> object = (Map<?, ?>) value;
            Object required = schema.get("required");
            if (required instanceof List<?> requiredFields) {
                for (Object field : requiredFields) {
                    if (!object.containsKey(String.valueOf(field))) {
                        throw schemaError(
                                label,
                                path + "." + field + " 为必填字段");
                    }
                }
            }
            if (schema.get("properties") instanceof Map<?, ?> properties) {
                for (Map.Entry<?, ?> entry : properties.entrySet()) {
                    String property = String.valueOf(entry.getKey());
                    if (object.containsKey(property)) {
                        validateSchemaValueNode(
                                (Map<?, ?>) entry.getValue(),
                                object.get(property),
                                label,
                                path + "." + property);
                    }
                }
            }
        } else if ("array".equals(type)
                && schema.get("items") instanceof Map<?, ?> itemSchema) {
            List<?> values = (List<?>) value;
            for (int index = 0; index < values.size(); index++) {
                validateSchemaValueNode(
                        itemSchema,
                        values.get(index),
                        label,
                        path + "[" + index + "]");
            }
        }
    }

    private boolean matchesSchemaType(
            String type,
            Object value) {
        return switch (type) {
            case "object" -> value instanceof Map<?, ?>;
            case "array" -> value instanceof List<?>;
            case "string" -> value instanceof String;
            case "number" -> value instanceof Number number
                    && isFiniteNumber(number);
            case "integer" -> value instanceof Number number
                    && isInteger(number);
            case "boolean" -> value instanceof Boolean;
            default -> false;
        };
    }

    private boolean isFiniteNumber(Number number) {
        if (number instanceof Double value) {
            return Double.isFinite(value);
        }
        if (number instanceof Float value) {
            return Float.isFinite(value);
        }
        return true;
    }

    private boolean isInteger(Number number) {
        if (!isFiniteNumber(number)) {
            return false;
        }
        if (number instanceof Byte
                || number instanceof Short
                || number instanceof Integer
                || number instanceof Long
                || number instanceof BigInteger) {
            return true;
        }
        if (number instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().scale() <= 0;
        }
        if (number instanceof Double value) {
            return value == Math.rint(value);
        }
        if (number instanceof Float value) {
            return value == Math.rint(value);
        }
        try {
            return new BigDecimal(number.toString())
                    .stripTrailingZeros()
                    .scale() <= 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private String actualType(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map<?, ?>) {
            return "object";
        }
        if (value instanceof List<?>) {
            return "array";
        }
        if (value instanceof String) {
            return "string";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof Number) {
            return "number";
        }
        return value.getClass().getSimpleName();
    }

    private DataSourceValidationException schemaError(
            String label,
            String detail) {
        return new DataSourceValidationException(
                label + " 校验失败: " + detail);
    }

    private RuntimeException executionFailure(
            Exception exception) {
        if (exception instanceof java.util.concurrent.TimeoutException) {
            return new IllegalStateException(
                    "数据源执行超时",
                    exception);
        }
        if (exception instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return new IllegalStateException(
                    "数据源执行被中断",
                    exception);
        }
        Throwable current = exception;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current instanceof RuntimeException runtime
                ? runtime
                : new IllegalStateException(
                        "数据源执行失败",
                        current);
    }

    private boolean isNonRecoverable(
            RuntimeException exception) {
        return exception instanceof DataSourceValidationException
                || exception instanceof BusinessForbiddenException
                || exception instanceof BusinessConflictException
                || exception instanceof SecurityException;
    }

    private void validateNoForbiddenKeys(Object value, String path) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (FORBIDDEN_KEYS.contains(key)) {
                    throw new IllegalArgumentException(
                            "数据源配置禁止使用键: " + path + "." + key);
                }
                validateNoForbiddenKeys(entry.getValue(), path + "." + key);
            }
        } else if (value instanceof List<?> list) {
            for (int index = 0; index < list.size(); index++) {
                validateNoForbiddenKeys(list.get(index), path + "[" + index + "]");
            }
        }
    }

    private void requireScopeAccess(String scopeType, String scopeId) {
        if ("GLOBAL".equals(scopeType)) {
            return;
        }
        if ("ENTITY".equals(scopeType)) {
            entityAccessPolicy.requireDynamicById(scopeId);
            return;
        }
        if ("FORM".equals(scopeType)) {
            EntityForm form = formMapper.selectById(scopeId);
            if (form == null) throw new IllegalArgumentException("表单作用域不存在");
            entityAccessPolicy.requireDynamicById(form.getEntityId());
            return;
        }
        if ("LIST".equals(scopeType)) {
            EntityListConfig list = listMapper.selectById(scopeId);
            if (list == null) throw new IllegalArgumentException("列表作用域不存在");
            entityAccessPolicy.requireDynamicById(list.getEntityId());
        }
    }

    private void requireUsage(String usage) {
        String normalized = normalize(usage);
        if (!USAGES.contains(normalized)) {
            throw new IllegalArgumentException("不支持的数据源使用位置: " + usage);
        }
    }

    private void requireRevision(
            Integer expected,
            UiDataSourceDefinition current) {
        if (expected == null || !expected.equals(current.getRevision())) {
            throw new RevisionConflictException("数据源已被其他人修改", current);
        }
    }

    private List<Map<String, Object>> flattenDictionary(List<SysDictItem> items) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysDictItem item : items == null ? List.<SysDictItem>of() : items) {
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("label", item.getItemLabel());
            option.put("value", item.getItemValue());
            option.put("disabled", !SysDictItem.Status.ENABLED.getValue().equals(item.getStatus()));
            option.put("children", flattenDictionary(item.getChildren()));
            result.add(option);
        }
        return result;
    }

    private Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private Object resolvePath(Map<String, Object> source, String path) {
        Object current = source;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
        }
        return current;
    }

    private String stripSuffix(String key) {
        for (String suffix : List.of("_start", "_end", "_op")) {
            if (key.endsWith(suffix)) {
                return key.substring(0, key.length() - suffix.length());
            }
        }
        return key;
    }

    private String write(Map<String, Object> value, String label) {
        return value == null || value.isEmpty() ? null : codec.write(value, label);
    }

    private Map<String, Object> read(String value, String label) {
        return StringUtils.hasText(value)
                ? codec.readObject(value, label)
                : new LinkedHashMap<>();
    }

    private String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT)
                : "";
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int integer(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private record CacheEntry(Object value, long expiresAt) {
    }

    private static final class DataSourceValidationException
            extends IllegalArgumentException {

        private DataSourceValidationException(String message) {
            super(message);
        }
    }

}
