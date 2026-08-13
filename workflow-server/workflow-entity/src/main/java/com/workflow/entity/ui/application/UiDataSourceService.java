package com.workflow.entity.ui.application;

import com.workflow.entity.definition.application.EntityDefinitionAccessPolicy;
import com.workflow.entity.definition.application.EntityUiConfigurationPolicy;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.core.error.RevisionConflictException;
import com.workflow.admin.security.context.UserContext;
import com.workflow.admin.dictionary.application.SysDictItemService;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.contracts.entity.list.DataScopePlan;
import com.workflow.contracts.integration.IntegrationConnector;
import com.workflow.contracts.integration.IntegrationRequest;
import com.workflow.contracts.integration.IntegrationResult;
import com.workflow.contracts.integration.IntegrationRuntimeContext;
import com.workflow.contracts.ui.UiDataSourceProvider;
import com.workflow.contracts.ui.UiDataSourceUsages;
import com.workflow.contracts.ui.UiInvocationContext;
import com.workflow.entity.ui.api.request.UiDataSourceExecuteRequest;
import com.workflow.entity.ui.api.request.UiInterfaceOperationExecuteRequest;
import com.workflow.entity.ui.api.request.UiDataSourceSaveRequest;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.admin.dictionary.infrastructure.persistence.record.SysDictItem;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.entity.ui.infrastructure.persistence.record.UiDataSourceDefinition;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiDataSourceDefinitionMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
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
 * <p>
 * 支持字典、静态选项、注册提供器、集成连接器、运行时上下文和结构化计算等
 * 数据源类型，配置中禁止 SQL/脚本/URL 等危险字段；执行链路经
 * {@link UiDataSourceExecutionAccessService} 授权后按数据源类型分派，
 * 并提供带 TTL 的结果缓存。
 * </p>
 */
@Service
public class UiDataSourceService {
        private static final Set<String> SOURCE_TYPES = Set.of(
                        "DICTIONARY", "STATIC_OPTIONS", "REGISTERED_PROVIDER",
                        "INTEGRATION_CONNECTOR", "RUNTIME_CONTEXT",
                        "STRUCTURED_COMPUTE");
        private static final Set<String> SCOPE_TYPES = Set.of("GLOBAL", "ENTITY", "FORM", "LIST");
        private static final Set<String> CONTEXT_TYPES = Set.of("FORM", "LIST", "ENTITY");
        private static final Set<String> USAGES = Set.of(
                        UiDataSourceUsages.FORM_INIT,
                        UiDataSourceUsages.FIELD_OPTIONS,
                        UiDataSourceUsages.FIELD_DEFAULT,
                        UiDataSourceUsages.FIELD_COMPUTE,
                        UiDataSourceUsages.SUBFORM_ROWS,
                        UiDataSourceUsages.LIST_QUERY,
                        UiDataSourceUsages.LIST_COLUMN,
                        UiDataSourceUsages.AFTER_LOAD,
                        UiDataSourceUsages.BEFORE_SUBMIT,
                        UiDataSourceUsages.LIST_LOAD,
                        UiDataSourceUsages.LIST_EXPORT,
                        UiDataSourceUsages.DETAIL_LOAD,
                        UiDataSourceUsages.DATA_CREATE,
                        UiDataSourceUsages.DATA_UPDATE,
                        UiDataSourceUsages.DATA_DELETE,
                        UiDataSourceUsages.DATA_BATCH_DELETE,
                        UiDataSourceUsages.FORM_OPEN,
                        UiDataSourceUsages.FORM_SAVE,
                        UiDataSourceUsages.FORM_RESET,
                        UiDataSourceUsages.FIELD_CHANGE,
                        UiDataSourceUsages.ENTITY_SELECTED,
                        UiDataSourceUsages.FIELD_BUTTON_CLICK,
                        UiDataSourceUsages.SUBFORM_LOAD,
                        UiDataSourceUsages.SUBFORM_SAVE,
                        UiDataSourceUsages.TOOLBAR_BUTTON_CLICK,
                        UiDataSourceUsages.ROW_BUTTON_CLICK,
                        UiDataSourceUsages.FORM_BUTTON_CLICK);
        /** 接口服务定义持久化入口。 */
        private final UiDataSourceDefinitionMapper mapper;
        /** 表单作用域对象查询入口。 */
        private final EntityFormMapper formMapper;
        /** 列表作用域对象查询入口。 */
        private final EntityListConfigMapper listMapper;
        /** 实体作用域配置权限策略。 */
        private final EntityDefinitionAccessPolicy entityAccessPolicy;
        /** 表单和列表所属实体的 UI 配置权限策略。 */
        private final EntityUiConfigurationPolicy entityUiConfigurationPolicy;
        /** 字典型接口服务的数据读取入口。 */
        private final SysDictItemService dictItemService;
        /** 草稿、发布绑定及数据权限执行授权服务。 */
        private final UiDataSourceExecutionAccessService executionAccessService;
        /** 强类型 FORM/LIST/ENTITY 调用上下文工厂。 */
        private final UiInvocationContextFactory invocationContextFactory;
        /** 接口定义、输入输出 Schema 和执行策略校验器。 */
        private final UiDataSourceDefinitionValidator definitionValidator;
        /** 当前部署注册的接口 Provider。 */
        private final List<UiDataSourceProvider> providers;
        /** 当前部署注册的集成 Connector。 */
        private final List<IntegrationConnector> connectors;
        /** JSON 配置、Schema 和操作文档编解码器。 */
        private final JsonDocumentCodec codec;
        /** Provider 和 Connector 超时执行使用的任务执行器。 */
        private final TaskExecutor taskExecutor;

        /** 数据源执行结果缓存，按 key+版本+内容哈希索引。 */
        private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

        /**
         * 构造数据源服务，注入数据源提供器、集成连接器和异步执行器。
         *
         * @param mapper                 数据源定义 Mapper
         * @param formMapper             表单 Mapper
         * @param listMapper             列表配置 Mapper
         * @param entityAccessPolicy     实体访问策略
         * @param entityUiConfigurationPolicy 表单和列表所属实体的配置策略
         * @param dictItemService        字典项服务
         * @param executionAccessService 数据源执行访问控制服务
         * @param invocationContextFactory 强类型调用上下文工厂
         * @param definitionValidator    接口服务定义与 Schema 校验器
         * @param providers              注册的数据源提供器集合
         * @param connectors             集成连接器集合
         * @param codec                  JSON 文档编解码器
         * @param taskExecutor           应用异步任务执行器
         */
        public UiDataSourceService(
                        UiDataSourceDefinitionMapper mapper,
                        EntityFormMapper formMapper,
                        EntityListConfigMapper listMapper,
                        EntityDefinitionAccessPolicy entityAccessPolicy,
                        EntityUiConfigurationPolicy entityUiConfigurationPolicy,
                        SysDictItemService dictItemService,
                        UiDataSourceExecutionAccessService executionAccessService,
                        UiInvocationContextFactory invocationContextFactory,
                        UiDataSourceDefinitionValidator definitionValidator,
                        List<UiDataSourceProvider> providers,
                        List<IntegrationConnector> connectors,
                        JsonDocumentCodec codec,
                        @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
                this.mapper = mapper;
                this.formMapper = formMapper;
                this.listMapper = listMapper;
                this.entityAccessPolicy = entityAccessPolicy;
                this.entityUiConfigurationPolicy =
                                entityUiConfigurationPolicy;
                this.dictItemService = dictItemService;
                this.executionAccessService = executionAccessService;
                this.invocationContextFactory = invocationContextFactory;
                this.definitionValidator = definitionValidator;
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
                catalog.put("contextTypes", CONTEXT_TYPES);
                catalog.put("providers", providerOptions);
                catalog.put("connectors", connectorOptions);
                catalog.put("failurePolicies", List.of("FAIL", "EMPTY", "NULL"));
                return catalog;
        }

        @Transactional(rollbackFor = Exception.class)
        public UiDataSourceDefinition save(UiDataSourceSaveRequest request) {
                validateRequest(request);
                UiDataSourceDefinition current = StringUtils.hasText(request.getId())
                                ? mapper.selectById(request.getId())
                                : null;
                if (current != null) {
                        requireRevision(request.getExpectedRevision(), current);
                }
                UiDataSourceDefinition value = current == null ? new UiDataSourceDefinition() : current;
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
                value.setExecutionPolicyDocument(
                                write(request.getExecutionPolicy(), "数据源执行策略"));
                value.setOperationsDocument(writeList(
                                request.getOperations(), "接口服务操作定义"));
                value.setEnabled(request.getEnabled() == null || request.getEnabled());
                value.setUpdatedAt(LocalDateTime.now());
                value.setDeleted(0);
                if (current == null) {
                        value.setRevision(1);
                        value.setCreatedAt(LocalDateTime.now());
                        mapper.insert(value);
                } else {
                        int currentRevision = current.getRevision();
                        value.setRevision(currentRevision + 1);
                        UpdateWrapper<UiDataSourceDefinition> wrapper = new UpdateWrapper<>();
                        wrapper.eq("id", value.getId())
                                        .eq("revision", currentRevision)
                                        .eq("deleted", 0)
                                        .set("source_code", value.getSourceCode())
                                        .set("source_name", value.getSourceName())
                                        .set("source_type", value.getSourceType())
                                        .set("provider_code", value.getProviderCode())
                                        .set("scope_type", value.getScopeType())
                                        .set("scope_id", value.getScopeId())
                                        .set("config_document", value.getConfigDocument())
                                        .set("execution_policy_document", value.getExecutionPolicyDocument())
                                        .set("operations_document", value.getOperationsDocument())
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
                requireOperationRequest(request);
                UiDataSourceDefinition definition = resolveOperationDefinition(
                                requireExecutableDefinition(id),
                                request.getOperationCode());
                requireUsage(request.getUsage());
                UiDataSourceExecutionAuthorization authorization =
                                executionAccessService.authorizePreview(
                                                definition,
                                                request);
                requireOperationContext(definition, authorization.configType());
                return executeAuthorized(definition, request, authorization);
        }

        public Object execute(String id, UiDataSourceExecuteRequest request) {
                requireOperationRequest(request);
                return executeOperation(id, request.getOperationCode(), request);
        }

        public Object executeBoundOperation(
                        UiInterfaceOperationExecuteRequest request) {
                if (request == null
                                || !StringUtils.hasText(request.getOwnerType())
                                || !StringUtils.hasText(request.getOwnerId())
                                || !StringUtils.hasText(request.getBindingCode())
                                || !StringUtils.hasText(request.getTargetType())
                                || !StringUtils.hasText(request.getServiceId())
                                || !StringUtils.hasText(request.getOperationCode())) {
                        throw new IllegalArgumentException(
                                        "接口执行缺少 owner、binding、service 或 operation");
                }
                String targetType = normalize(request.getTargetType());
                if (!"OWNER".equals(targetType)
                                && !StringUtils.hasText(request.getTargetKey())) {
                        throw new IllegalArgumentException(
                                        "非 OWNER 绑定必须指定 targetKey");
                }
                UiBindingPoint bindingPoint = new UiBindingPoint(
                                normalize(request.getOwnerType()),
                                request.getOwnerId().trim(),
                                targetType,
                                blankToNull(request.getTargetKey()),
                                normalize(request.getBindingCode()),
                                request.getServiceId().trim(),
                                request.getOperationCode().trim());
                UiDataSourceExecuteRequest internal = new UiDataSourceExecuteRequest();
                internal.setUsage(bindingPoint.bindingCode());
                internal.setOperationCode(bindingPoint.operationCode());
                internal.setConfigType(bindingPoint.ownerType());
                internal.setConfigId(bindingPoint.ownerId());
                internal.setTargetType(bindingPoint.targetType());
                internal.setTargetKey(bindingPoint.targetKey());
                internal.setInput(request.getInput() == null
                                ? Map.of()
                                : new LinkedHashMap<>(request.getInput()));
                return executeOperation(
                                bindingPoint.serviceId(),
                                bindingPoint.operationCode(),
                                internal);
        }

        /**
         * 执行接口服务中的指定操作。
         */
        public Object executeOperation(
                        String id,
                        String operationCode,
                        UiDataSourceExecuteRequest request) {
                UiDataSourceDefinition definition = requireExecutableDefinition(id);
                UiDataSourceDefinition operationDefinition = resolveOperationDefinition(definition, operationCode);
                if (request != null) {
                        request.setOperationCode(blankToNull(operationCode));
                }
                requireUsage(request == null ? null : request.getUsage());
                UiDataSourceExecutionAuthorization authorization = executionAccessService.authorizePublished(
                                operationDefinition,
                                request);
                requireOperationContext(
                                operationDefinition,
                                authorization.configType());
                return executeAuthorized(
                                operationDefinition,
                                request,
                                authorization);
        }

        /**
         * 实体变更管道 PREPARE 阶段执行受管理接口操作。
         *
         * <p>
         * 此入口只供服务端版本策略调用，不要求接口先绑定某个表单或列表发布版本。
         * </p>
         */
        public Object executeManagedMutationOperation(
                        String id,
                        String operationCode,
                        UiDataSourceExecuteRequest request) {
                UiDataSourceDefinition definition = requireExecutableDefinition(id);
                UiDataSourceDefinition operationDefinition = resolveOperationDefinition(
                                definition,
                                operationCode);
                if (request == null) {
                        request = new UiDataSourceExecuteRequest();
                }
                request.setUsage(
                                UiDataSourceUsages
                                                .ENTITY_MUTATION_PREPARE);
                request.setOperationCode(
                                blankToNull(operationCode));
                UiDataSourceExecutionAuthorization authorization = executionAccessService
                                .authorizeEntityMutation(
                                                operationDefinition,
                                                request);
                requireMutationScope(
                                operationDefinition,
                                authorization);
                requireOperationContext(
                                operationDefinition,
                                "ENTITY");
                return executeAuthorized(
                                operationDefinition,
                                request,
                                authorization);
        }

        /**
         * 在管理端调试接口服务中的指定操作，不要求该操作已经绑定到发布页面。
         */
        public Object previewOperation(
                        String id,
                        String operationCode,
                        UiDataSourceExecuteRequest request) {
                UiDataSourceDefinition definition = requireExecutableDefinition(id);
                UiDataSourceDefinition operationDefinition = resolveOperationDefinition(definition, operationCode);
                if (request != null) {
                        request.setOperationCode(blankToNull(operationCode));
                }
                requireUsage(request == null ? null : request.getUsage());
                UiDataSourceExecutionAuthorization authorization = executionAccessService.authorizeManagementPreview(
                                operationDefinition,
                                request);
                requireOperationContext(
                                operationDefinition,
                                authorization.configType());
                return executeAuthorized(
                                operationDefinition,
                                request,
                                authorization);
        }

        /**
         * 返回接口服务操作目录，供事件绑定校验和设计器选择。
         */
        public List<Map<String, Object>> operations(String id) {
                UiDataSourceDefinition definition = requireExecutableDefinition(id);
                List<Map<String, Object>> operations = readList(
                                definition.getOperationsDocument(), "接口服务操作定义");
                if (operations.isEmpty()) {
                        throw new BusinessConflictException(
                                        "UI_INTERFACE_OPERATIONS_REQUIRED",
                                        "接口服务未配置操作");
                }
                return operations;
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

        private void requireMutationScope(
                        UiDataSourceDefinition definition,
                        UiDataSourceExecutionAuthorization authorization) {
                String scopeType = normalize(
                                definition.getScopeType());
                if ("GLOBAL".equals(scopeType)
                                || scopeType.isEmpty()) {
                        return;
                }
                if ("ENTITY".equals(scopeType)
                                && (Objects.equals(
                                                definition.getScopeId(),
                                                authorization.entityId())
                                                || Objects.equals(
                                                                definition.getScopeId(),
                                                                authorization.entityCode()))) {
                        return;
                }
                throw new BusinessForbiddenException(
                                "ENTITY_MUTATION_SOURCE_SCOPE_MISMATCH",
                                "受管理接口的作用域与目标实体不一致");
        }

        private Object executeAuthorized(
                        UiDataSourceDefinition definition,
                        UiDataSourceExecuteRequest request,
                        UiDataSourceExecutionAuthorization authorization) {
                Map<String, Object> config = read(
                                definition.getConfigDocument(), "数据源配置");
                Map<String, Object> input = request == null || request.getInput() == null
                                ? Map.of()
                                : request.getInput();
                Map<String, Object> inputSchema = read(
                                definition.getOperationInputSchemaDocument(),
                                "接口操作输入Schema");
                Map<String, Object> outputSchema = read(
                                definition.getOperationOutputSchemaDocument(),
                                "接口操作输出Schema");
                definitionValidator.validateSchemaDefinition(
                                inputSchema,
                                "数据源输入Schema");
                definitionValidator.validateSchemaDefinition(
                                outputSchema,
                                "数据源输出Schema");
                definitionValidator.validateSchemaValue(
                                inputSchema,
                                input,
                                "数据源输入");
                Map<String, Object> policy = read(
                                definition.getExecutionPolicyDocument(), "数据源执行策略");
                String cacheKey = cacheKey(
                                definition,
                                input,
                                authorization);
                int cacheSeconds = integer(policy.get("cacheSeconds"), 0);
                CacheEntry cached = cache.get(cacheKey);
                if (cacheSeconds > 0 && cached != null && cached.expiresAt() > System.currentTimeMillis()) {
                        definitionValidator.validateSchemaValue(
                                        outputSchema,
                                        cached.value(),
                                        "数据源输出");
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
                        definitionValidator.validateSchemaValue(
                                        outputSchema,
                                        result,
                                        "数据源输出");
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
                        definitionValidator.validateSchemaValue(
                                        outputSchema,
                                        fallback,
                                        "数据源输出");
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
                result.put("serviceId", id);
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
                UiInvocationContext context = invocationContextFactory.create(
                                definition,
                                authorization,
                                request);
                if ("RUNTIME_CONTEXT".equals(sourceType)) {
                        return context;
                }
                if ("STRUCTURED_COMPUTE".equals(sourceType)) {
                        return compute(config, input);
                }
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
                                                        .connectorConfigId(text(
                                                                        config.get("connectorConfigId")))
                                                        .parameters(Collections.unmodifiableMap(
                                                                        new LinkedHashMap<>(input)))
                                                        .runtimeContext(
                                                                        new IntegrationRuntimeContext(
                                                                                        context))
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
                                                        ? config.get("then")
                                                        : config.get("else");
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
                if ("GLOBAL".equals(scopeType)
                                && Set.of("REGISTERED_PROVIDER", "INTEGRATION_CONNECTOR")
                                                .contains(sourceType)) {
                        throw new IllegalArgumentException(
                                        "Provider 和 Connector 必须绑定实体、表单或列表范围");
                }
                definitionValidator.validateNoForbiddenKeys(
                                request.getConfig(),
                                "config");
                definitionValidator.validateExecutionPolicy(
                                request.getExecutionPolicy());
                validateOperations(request.getOperations());
                validateOperationScopes(
                                request.getOperations(),
                                scopeType);
                if (Set.of("REGISTERED_PROVIDER", "INTEGRATION_CONNECTOR").contains(sourceType)
                                && !StringUtils.hasText(request.getProviderCode())) {
                        throw new IllegalArgumentException("Provider/Connector编码不能为空");
                }
                requireScopeAccess(scopeType, request.getScopeId());
        }

        private void validateOperations(
                        List<Map<String, Object>> operations) {
                if (operations == null || operations.isEmpty()) {
                        throw new IllegalArgumentException("接口服务至少需要一个操作");
                }
                Set<String> codes = new java.util.LinkedHashSet<>();
                for (int index = 0; index < operations.size(); index++) {
                        Map<String, Object> operation = operations.get(index);
                        String code = text(operation.get("code"));
                        String name = text(operation.get("name"));
                        String kind = normalize(text(operation.getOrDefault("kind", "READ")));
                        String contextType = normalize(text(operation.get("contextType")));
                        if (!StringUtils.hasText(code) || !StringUtils.hasText(name)) {
                                throw new IllegalArgumentException(
                                                "接口服务第 " + (index + 1) + " 个操作缺少编码或名称");
                        }
                        if (!codes.add(code.trim())) {
                                throw new IllegalArgumentException("接口服务操作编码重复: " + code);
                        }
                        if (!Set.of("READ", "WRITE").contains(kind)) {
                                throw new IllegalArgumentException(
                                                "接口服务操作类型仅支持 READ/WRITE: " + code);
                        }
                        if (!CONTEXT_TYPES.contains(contextType)) {
                                throw new IllegalArgumentException(
                                                "接口服务操作必须声明 FORM/LIST/ENTITY 上下文: "
                                                                + code);
                        }
                        Map<String, Object> config = operation.get("config") instanceof Map<?, ?> map
                                        ? stringMap(map)
                                        : Map.of();
                        Map<String, Object> inputSchema = operation.get("inputSchema") instanceof Map<?, ?> map
                                        ? stringMap(map)
                                        : Map.of();
                        Map<String, Object> outputSchema = operation.get("outputSchema") instanceof Map<?, ?> map
                                        ? stringMap(map)
                                        : Map.of();
                        Map<String, Object> policy = operation.get("executionPolicy") instanceof Map<?, ?> map
                                        ? stringMap(map)
                                        : Map.of();
                        definitionValidator.validateNoForbiddenKeys(
                                        config,
                                        "operations." + code + ".config");
                        definitionValidator.validateSchemaDefinition(
                                        inputSchema, "接口操作 " + code + " 输入Schema");
                        definitionValidator.validateSchemaDefinition(
                                        outputSchema, "接口操作 " + code + " 输出Schema");
                        definitionValidator.validateExecutionPolicy(policy);
                }
        }

        private void validateOperationScopes(
                        List<Map<String, Object>> operations,
                        String scopeType) {
                for (Map<String, Object> operation : operations) {
                        String contextType = normalize(
                                        text(operation.get("contextType")));
                        boolean compatible = switch (scopeType) {
                                case "ENTITY" -> true;
                                case "FORM" -> "FORM".equals(contextType);
                                case "LIST" -> "LIST".equals(contextType);
                                case "GLOBAL" -> !"ENTITY".equals(contextType);
                                default -> false;
                        };
                        if (!compatible) {
                                throw new IllegalArgumentException(
                                                "接口操作 "
                                                                + text(operation.get("code"))
                                                                + " 的上下文 "
                                                                + contextType
                                                                + " 与作用范围 "
                                                                + scopeType
                                                                + " 不兼容");
                        }
                }
        }

        private UiDataSourceDefinition resolveOperationDefinition(
                        UiDataSourceDefinition definition,
                        String operationCode) {
                List<Map<String, Object>> operations = readList(
                                definition.getOperationsDocument(), "接口服务操作定义");
                if (operations.isEmpty()) {
                        throw new IllegalArgumentException("接口服务未配置操作");
                }
                if (!StringUtils.hasText(operationCode)) {
                        throw new IllegalArgumentException("接口执行必须指定 operationCode");
                }
                String expected = operationCode.trim();
                Map<String, Object> operation = operations.stream()
                                .filter(item -> Objects.equals(
                                                expected,
                                                text(item.get("code"))))
                                .findFirst()
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "接口服务操作不存在: " + operationCode));
                UiDataSourceDefinition resolved = copyDefinition(definition);
                resolved.setOperationCode(expected);
                resolved.setOperationContextType(
                                normalize(text(operation.get("contextType"))));
                resolved.setOperationKind(normalize(
                                text(operation.getOrDefault("kind", "READ"))));
                Map<String, Object> baseConfig = read(
                                definition.getConfigDocument(), "接口服务基础配置");
                if (operation.get("config") instanceof Map<?, ?> map) {
                        baseConfig.putAll(stringMap(map));
                }
                resolved.setConfigDocument(write(baseConfig, "接口操作配置"));
                Map<String, Object> inputSchema =
                                operation.get("inputSchema") instanceof Map<?, ?> map
                                                ? stringMap(map)
                                                : Map.of();
                Map<String, Object> outputSchema =
                                operation.get("outputSchema") instanceof Map<?, ?> map
                                                ? stringMap(map)
                                                : Map.of();
                resolved.setOperationInputSchemaDocument(write(
                                inputSchema, "接口操作输入Schema"));
                resolved.setOperationOutputSchemaDocument(write(
                                outputSchema, "接口操作输出Schema"));
                if (operation.get("executionPolicy") instanceof Map<?, ?> map) {
                        Map<String, Object> policy = read(
                                        definition.getExecutionPolicyDocument(),
                                        "接口服务执行策略");
                        policy.putAll(stringMap(map));
                        resolved.setExecutionPolicyDocument(write(
                                        policy, "接口操作执行策略"));
                }
                return resolved;
        }

        private UiDataSourceDefinition copyDefinition(
                        UiDataSourceDefinition source) {
                UiDataSourceDefinition target = new UiDataSourceDefinition();
                target.setId(source.getId());
                target.setSourceCode(source.getSourceCode());
                target.setSourceName(source.getSourceName());
                target.setSourceType(source.getSourceType());
                target.setProviderCode(source.getProviderCode());
                target.setScopeType(source.getScopeType());
                target.setScopeId(source.getScopeId());
                target.setConfigDocument(source.getConfigDocument());
                target.setExecutionPolicyDocument(source.getExecutionPolicyDocument());
                target.setOperationsDocument(source.getOperationsDocument());
                target.setOperationInputSchemaDocument(
                                source.getOperationInputSchemaDocument());
                target.setOperationOutputSchemaDocument(
                                source.getOperationOutputSchemaDocument());
                target.setOperationCode(source.getOperationCode());
                target.setOperationContextType(source.getOperationContextType());
                target.setOperationKind(source.getOperationKind());
                target.setRevision(source.getRevision());
                target.setEnabled(source.getEnabled());
                target.setCreatedAt(source.getCreatedAt());
                target.setUpdatedAt(source.getUpdatedAt());
                target.setDeleted(source.getDeleted());
                return target;
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
                key.put("serviceId", definition.getId());
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
                material.put("serviceId", definition.getId());
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
                return exception instanceof UiDataSourceDefinitionValidator.ValidationException
                                || exception instanceof BusinessForbiddenException
                                || exception instanceof BusinessConflictException
                                || exception instanceof SecurityException;
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
                        if (form == null)
                                throw new IllegalArgumentException("表单作用域不存在");
                        entityUiConfigurationPolicy
                                        .requireConfigurableById(
                                                        form.getEntityId());
                        return;
                }
                if ("LIST".equals(scopeType)) {
                        EntityListConfig list = listMapper.selectById(scopeId);
                        if (list == null)
                                throw new IllegalArgumentException("列表作用域不存在");
                        entityUiConfigurationPolicy
                                        .requireConfigurableById(
                                                        list.getEntityId());
                }
        }

        private void requireUsage(String usage) {
                String normalized = normalize(usage);
                if (!USAGES.contains(normalized)) {
                        throw new IllegalArgumentException("不支持的数据源使用位置: " + usage);
                }
        }

        private void requireOperationRequest(
                        UiDataSourceExecuteRequest request) {
                if (request == null
                                || !StringUtils.hasText(request.getOperationCode())) {
                        throw new IllegalArgumentException(
                                        "接口执行必须指定 operationCode");
                }
        }

        private void requireOperationContext(
                        UiDataSourceDefinition definition,
                        String ownerType) {
                String expected = normalize(definition.getOperationContextType());
                String actual = normalize(ownerType);
                if ("ENTITY_MUTATION".equals(actual)) {
                        actual = "ENTITY";
                }
                if (!Objects.equals(expected, actual)) {
                        throw new BusinessForbiddenException(
                                        "UI_INTERFACE_CONTEXT_MISMATCH",
                                        "接口操作上下文与绑定对象类型不一致");
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

        private String write(Map<String, Object> value, String label) {
                return value == null || value.isEmpty() ? null : codec.write(value, label);
        }

        private String writeList(
                        List<Map<String, Object>> value,
                        String label) {
                return value == null || value.isEmpty()
                                ? null
                                : codec.write(value, label);
        }

        private Map<String, Object> read(String value, String label) {
                return StringUtils.hasText(value)
                                ? codec.readObject(value, label)
                                : new LinkedHashMap<>();
        }

        private List<Map<String, Object>> readList(
                        String value,
                        String label) {
                if (!StringUtils.hasText(value)) {
                        return List.of();
                }
                return codec.readArray(value, label).stream()
                                .filter(Map.class::isInstance)
                                .map(item -> stringMap((Map<?, ?>) item))
                                .toList();
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

}
