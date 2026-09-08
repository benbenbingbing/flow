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
import com.workflow.contracts.integration.spi.IntegrationConnector;
import com.workflow.contracts.integration.IntegrationConnectorConfigurationSnapshot;
import com.workflow.contracts.integration.spi.IntegrationConnectorConfigurationSnapshotProvider;
import com.workflow.contracts.integration.IntegrationRequest;
import com.workflow.contracts.integration.IntegrationResult;
import com.workflow.contracts.integration.IntegrationRuntimeContext;
import com.workflow.contracts.entity.ui.spi.UiDataSourceProvider;
import com.workflow.contracts.ui.UiDataSourceUsages;
import com.workflow.contracts.ui.UiInvocationContext;
import com.workflow.contracts.ui.UiActionCommandPlan;
import com.workflow.contracts.entity.ui.spi.UiActionCommandPlanProvider;
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
import org.springframework.beans.factory.annotation.Autowired;
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
                        UiDataSourceUsages.FORM_BUTTON_CLICK,
                        UiDataSourceUsages.RELATED_CONTENT_RESOLVE,
                        UiDataSourceUsages.RELATED_CONTENT_ACTION);
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
        /** 阻止删除仍被线上发布版本引用的接口服务。 */
        private UiPublishedDataSourceReferenceGuard publishedReferenceGuard;
        /** 强类型 FORM/LIST/ENTITY 调用上下文工厂。 */
        private final UiInvocationContextFactory invocationContextFactory;
        /** 接口定义、输入输出 Schema 和执行策略校验器。 */
        private final UiDataSourceDefinitionValidator definitionValidator;
        /** 当前部署注册的接口 Provider。 */
        private final List<UiDataSourceProvider> providers;
        /** 当前部署注册的集成 Connector。 */
        private final List<IntegrationConnector> connectors;
        /** 由连接器管理模块提供的安全发布快照出口。 */
        private List<IntegrationConnectorConfigurationSnapshotProvider>
                        connectorSnapshotProviders = List.of();
        /** 只生成结构化实体变更计划的本地关联动作 Provider。 */
        private List<UiActionCommandPlanProvider> actionCommandPlanProviders =
                        List.of();
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
         * 注入线上发布引用删除保护。使用 setter 保持既有构造 API 兼容，
         * Spring 运行态仍将该保护作为必需依赖注入。
         */
        @Autowired
        public void setPublishedReferenceGuard(
                        UiPublishedDataSourceReferenceGuard value) {
                this.publishedReferenceGuard = value;
        }

        /**
         * 可选注入连接器快照提供器。单元测试和未开启外部集成的部署
         * 不需要伪造实现；只有发布 INTEGRATION_CONNECTOR 时才强制要求。
         */
        @Autowired(required = false)
        public void setConnectorSnapshotProviders(
                        List<IntegrationConnectorConfigurationSnapshotProvider>
                                        snapshotProviders) {
                this.connectorSnapshotProviders = snapshotProviders == null
                                ? List.of() : List.copyOf(snapshotProviders);
        }

        /** 可选注入本地受控写计划 Provider，不影响只读接口服务部署。 */
        @Autowired(required = false)
        public void setActionCommandPlanProviders(
                        List<UiActionCommandPlanProvider> value) {
                actionCommandPlanProviders = value == null
                                ? List.of() : List.copyOf(value);
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
                catalog.put("actionCommandPlanProviders",
                                actionCommandPlanProviders.stream()
                                                .map(provider -> Map.<String, Object>of(
                                                                "code", provider.getCode(),
                                                                "name", provider.getDisplayName()))
                                                .toList());
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
                // 软删除会让运行时立即拒绝该服务；必须先在同一事务内确认
                // 所有实际生效发布快照均已解除引用。
                if (publishedReferenceGuard == null) {
                        throw new BusinessConflictException(
                                        "UI_DATA_SOURCE_REFERENCE_GUARD_UNAVAILABLE",
                                        "接口服务发布引用保护不可用，拒绝删除");
                }
                publishedReferenceGuard.requireNoExecutableReferences(id);
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
                return executeBoundOperation(request, null, null, false);
        }

        /**
         * 按服务端委托策略固定的历史 Form Release 执行原生数据源绑定。
         *
         * <p>该入口只供已经通过 Embed MVC 授权的控制器调用；发布坐标来自
         * 服务端请求属性而不是公开 DTO，避免会话打开后 ACTIVE 切换导致行为漂移。</p>
         */
        public Object executeBoundOperationAtRelease(
                        UiInterfaceOperationExecuteRequest request,
                        String releaseId,
                        Integer releaseVersion) {
                if (!StringUtils.hasText(releaseId)
                                || releaseVersion == null
                                || releaseVersion < 1) {
                        throw new BusinessForbiddenException(
                                        "UI_DATA_SOURCE_PINNED_RELEASE_REQUIRED",
                                        "Embed 原生数据源执行缺少服务端固定发布版本");
                }
                return executeBoundOperation(
                                request, releaseId, releaseVersion, true);
        }

        private Object executeBoundOperation(
                        UiInterfaceOperationExecuteRequest request,
                        String releaseId,
                        Integer releaseVersion,
                        boolean pinned) {
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
                if (pinned) {
                        // serverPinnedRelease 与内部执行种子只在此服务端分支设置，
                        // 公开请求没有对应字段，不能自行选择历史快照。
                        internal.setReleaseId(releaseId);
                        internal.setReleaseVersion(releaseVersion);
                        internal.setServerPinnedRelease(true);
                        internal.setServerIdempotencyKey(
                                        "embed-delegated-runtime");
                }
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
         * 为关联内容“使用真实数据测试”执行当前接口定义中的只读解析操作。
         *
         * <p>操作解析、READ/失败即终止策略校验和执行使用同一个内存定义，
         * 避免先通过操作目录校验、随后定义被改为 WRITE 或回退策略的竞态。
         * 此入口仍走管理预览鉴权，且只接受关联内容专用 usage。</p>
         */
        public Object previewRelatedContentReadOperation(
                        String id,
                        String operationCode,
                        UiDataSourceExecuteRequest request) {
                if (request == null
                                || !UiDataSourceUsages.RELATED_CONTENT_RESOLVE.equals(
                                                normalize(request.getUsage()))) {
                        throw new IllegalArgumentException(
                                        "关联内容真实数据测试必须使用专用解析位置");
                }
                UiDataSourceDefinition operationDefinition =
                                resolveOperationDefinition(
                                                requireExecutableDefinition(id),
                                                operationCode);
                requirePinnedReadFailurePolicy(operationDefinition);
                request.setOperationCode(blankToNull(operationCode));
                UiDataSourceExecutionAuthorization authorization =
                                executionAccessService.authorizeManagementPreview(
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

        /**
         * 固定一个接口服务操作的完整可执行定义。
         *
         * <p>快照保存已合并操作覆盖后的配置、Schema 和执行策略，
         * 运行时因此无需回读可变的 ui_data_source_definition。集成连接器
         * 还会嵌入经具体连接器校验的配置快照，但凭据仍只保存 SecretRef。</p>
         *
         * @param id 接口服务 ID
         * @param operationCode 具体操作编码
         * @return 规范 JSON 文档及 SHA-256
         */
        public PublishedOperationSnapshot freezeOperation(
                        String id,
                        String operationCode) {
                UiDataSourceDefinition resolved = resolveOperationDefinition(
                                requireExecutableDefinition(id),
                                operationCode);
                Map<String, Object> snapshot = new LinkedHashMap<>();
                snapshot.put("schemaVersion", 2);
                snapshot.put("id", resolved.getId());
                snapshot.put("sourceCode", resolved.getSourceCode());
                snapshot.put("sourceName", resolved.getSourceName());
                snapshot.put("sourceType", normalize(resolved.getSourceType()));
                snapshot.put("providerCode", resolved.getProviderCode());
                if ("REGISTERED_PROVIDER".equals(
                                normalize(resolved.getSourceType()))) {
                        ProviderIdentity identity = currentProviderIdentity(
                                        resolved);
                        snapshot.put("providerVersion", identity.version());
                        snapshot.put(
                                        "providerArtifactDigest",
                                        identity.artifactDigest());
                }
                snapshot.put("scopeType", normalize(resolved.getScopeType()));
                snapshot.put("scopeId", resolved.getScopeId());
                snapshot.put("revision", resolved.getRevision());
                snapshot.put("operationCode", resolved.getOperationCode());
                snapshot.put(
                                "operationContextType",
                                resolved.getOperationContextType());
                snapshot.put("operationKind", resolved.getOperationKind());
                snapshot.put("configDocument", canonicalDocument(
                                resolved.getConfigDocument(),
                                "接口操作配置"));
                snapshot.put("executionPolicyDocument", canonicalDocument(
                                resolved.getExecutionPolicyDocument(),
                                "接口操作执行策略"));
                snapshot.put("inputSchemaDocument", canonicalDocument(
                                resolved.getOperationInputSchemaDocument(),
                                "接口操作输入 Schema"));
                snapshot.put("outputSchemaDocument", canonicalDocument(
                                resolved.getOperationOutputSchemaDocument(),
                                "接口操作输出 Schema"));
                if ("INTEGRATION_CONNECTOR".equals(
                                normalize(resolved.getSourceType()))) {
                        snapshot.put(
                                        "connectorSnapshot",
                                        freezeConnectorConfiguration(resolved));
                }
                String document = codec.canonicalize(
                                codec.write(snapshot, "接口操作发布快照"),
                                "接口操作发布快照");
                return new PublishedOperationSnapshot(
                                resolved.getId(),
                                resolved.getSourceCode(),
                                resolved.getRevision(),
                                resolved.getOperationCode(),
                                document,
                                sha256(document));
        }

        /**
         * 从宿主快照执行固定操作。当前接口服务被修改或重新发布时，
         * 旧宿主仍使用这份定义；快照或哈希不完整则直接 fail-closed。
         */
        public Object executePinnedOperation(
                        String snapshotDocument,
                        String expectedHash,
                        UiDataSourceExecuteRequest request) {
                UiDataSourceDefinition definition = readPinnedOperation(
                                snapshotDocument,
                                expectedHash);
                requirePinnedReadFailurePolicy(definition);
                requirePinnedProviderAvailable(definition);
                if (request != null) {
                        request.setOperationCode(
                                        definition.getOperationCode());
                }
                requireUsage(request == null ? null : request.getUsage());
                UiDataSourceExecutionAuthorization authorization =
                                executionAccessService.authorizePublished(
                                                definition,
                                                request);
                requireOperationContext(
                                definition,
                                authorization.configType());
                return executeAuthorized(
                                definition,
                                request,
                                authorization);
        }

        /**
         * 校验关联内容宿主快照中的钉版读操作，不回读当前接口定义。
         */
        public void validatePinnedReadOperation(
                        String snapshotDocument,
                        String expectedHash,
                        String serviceId,
                        String sourceCode,
                        Integer serviceRevision,
                        String operationCode,
                        String ownerType) {
                UiDataSourceDefinition definition = readPinnedOperation(
                                snapshotDocument,
                                expectedHash);
                if (!Objects.equals(serviceId, definition.getId())
                                || !Objects.equals(
                                                sourceCode,
                                                definition.getSourceCode())
                                || !Objects.equals(
                                                serviceRevision,
                                                definition.getRevision())
                                || !Objects.equals(
                                                operationCode,
                                                definition.getOperationCode())) {
                        throw new BusinessConflictException(
                                        "UI_INTERFACE_PINNED_IDENTITY_CONFLICT",
                                        "已发布接口操作快照与绑定的服务、修订号或操作不一致");
                }
                requirePinnedReadFailurePolicy(definition);
                requirePinnedProviderAvailable(definition);
                if (!normalize(ownerType).equals(normalize(
                                                definition
                                                        .getOperationContextType()))) {
                        throw new BusinessForbiddenException(
                                        "UI_INTERFACE_PINNED_OPERATION_FORBIDDEN",
                                        "关联内容只允许当前宿主上下文的钉版 READ 操作");
                }
        }

        /**
         * 校验设计态关联动作操作是否可安全发布。
         *
         * <p>READ 可同步执行；WRITE 只能绑定注册的本地命令计划 Provider。
         * Connector WRITE 在持久化 Outbox、状态和补偿链完成前明确 fail-closed。</p>
         */
        public ActionOperationDescriptor validateActionOperation(
                        String serviceId,
                        String operationCode,
                        String ownerType) {
                UiDataSourceDefinition definition = resolveOperationDefinition(
                                requireExecutableDefinition(serviceId),
                                operationCode);
                requireActionOperation(definition, ownerType);
                return descriptor(definition);
        }

        /** 固定一个已通过动作治理校验的接口操作。 */
        public PublishedOperationSnapshot freezeActionOperation(
                        String serviceId,
                        String operationCode) {
                UiDataSourceDefinition definition = resolveOperationDefinition(
                                requireExecutableDefinition(serviceId),
                                operationCode);
                requireActionOperation(definition, null);
                return freezeOperation(serviceId, operationCode);
        }

        /**
         * 校验宿主发布快照内动作绑定的身份、上下文和执行类型。
         */
        public ActionOperationDescriptor validatePinnedActionOperation(
                        String snapshotDocument,
                        String expectedHash,
                        String serviceId,
                        String sourceCode,
                        Integer serviceRevision,
                        String operationCode,
                        String ownerType) {
                UiDataSourceDefinition definition = readPinnedOperation(
                                snapshotDocument, expectedHash);
                requirePinnedIdentity(
                                definition,
                                serviceId,
                                sourceCode,
                                serviceRevision,
                                operationCode);
                requireActionOperation(definition, ownerType);
                requirePinnedProviderAvailable(definition);
                return descriptor(definition);
        }

        /**
         * 从宿主发布快照生成本地 WRITE 的强类型计划。
         *
         * <p>此方法只负责验证钉版绑定、Schema 和调用上下文并调用计划 Provider；
         * 不执行实体写入。调用方必须再通过平台权限和 EntityMutationPort。</p>
         */
        public UiActionCommandPlan planPinnedActionOperation(
                        String snapshotDocument,
                        String expectedHash,
                        UiDataSourceExecuteRequest request) {
                UiDataSourceDefinition definition = readPinnedOperation(
                                snapshotDocument, expectedHash);
                requireActionOperation(
                                definition,
                                request == null ? null : request.getConfigType());
                if (!"WRITE".equals(normalize(definition.getOperationKind()))) {
                        throw new BusinessForbiddenException(
                                        "UI_INTERFACE_ACTION_KIND_INVALID",
                                        "当前接口操作不是本地受控写入操作");
                }
                if (request != null) {
                        request.setOperationCode(definition.getOperationCode());
                }
                requireUsage(request == null ? null : request.getUsage());
                UiDataSourceExecutionAuthorization authorization =
                                executionAccessService.authorizePublished(
                                                definition, request);
                requireOperationContext(
                                definition, authorization.configType());
                if (!authorization.dataScopePlan().allowed()) {
                        throw new BusinessForbiddenException(
                                        "UI_DATA_SOURCE_DATA_SCOPE_DENIED",
                                        "当前用户的数据权限计划拒绝执行该本地动作");
                }
                Map<String, Object> input = request == null
                                || request.getInput() == null
                                ? Map.of() : request.getInput();
                Map<String, Object> inputSchema = read(
                                definition.getOperationInputSchemaDocument(),
                                "接口操作输入Schema");
                definitionValidator.validateSchemaDefinition(
                                inputSchema, "数据源输入Schema");
                definitionValidator.validateSchemaValue(
                                inputSchema, input, "数据源输入");
                UiActionCommandPlanProvider provider = actionPlanProvider(
                                definition,
                                true);
                UiInvocationContext context = invocationContextFactory.create(
                                definition, authorization, request);
                UiActionCommandPlan plan = provider.plan(
                                context,
                                Collections.unmodifiableMap(read(
                                                definition.getConfigDocument(),
                                                "接口操作配置")),
                                Collections.unmodifiableMap(
                                                new LinkedHashMap<>(input)));
                if (plan == null) {
                        throw new BusinessConflictException(
                                        "UI_INTERFACE_ACTION_PLAN_EMPTY",
                                        "本地受控写入 Provider 未返回命令计划");
                }
                Map<String, Object> outputSchema = read(
                                definition.getOperationOutputSchemaDocument(),
                                "接口操作输出Schema");
                definitionValidator.validateSchemaDefinition(
                                outputSchema, "数据源输出Schema");
                definitionValidator.validateSchemaValue(
                                outputSchema, plan.result(), "数据源输出");
                return plan;
        }

        private void requirePinnedIdentity(
                        UiDataSourceDefinition definition,
                        String serviceId,
                        String sourceCode,
                        Integer serviceRevision,
                        String operationCode) {
                if (!Objects.equals(serviceId, definition.getId())
                                || !Objects.equals(sourceCode, definition.getSourceCode())
                                || !Objects.equals(serviceRevision, definition.getRevision())
                                || !Objects.equals(operationCode,
                                                definition.getOperationCode())) {
                        throw new BusinessConflictException(
                                        "UI_INTERFACE_PINNED_IDENTITY_CONFLICT",
                                        "已发布接口操作快照与绑定的服务、修订号或操作不一致");
                }
        }

        /**
         * 发布时选择当前最高版本 Provider，并把版本与制品摘要写入宿主快照。
         * 同一编码和版本若出现不同摘要会直接失败，避免依赖注入顺序决定行为。
         */
        private ProviderIdentity currentProviderIdentity(
                        UiDataSourceDefinition definition) {
                if ("WRITE".equals(normalize(
                                definition.getOperationKind()))) {
                        UiActionCommandPlanProvider provider =
                                        actionPlanProvider(definition, false);
                        return new ProviderIdentity(
                                        providerVersion(provider),
                                        providerDigest(provider));
                }
                UiDataSourceProvider provider = dataSourceProvider(
                                definition, false);
                return new ProviderIdentity(
                                providerVersion(provider),
                                providerDigest(provider));
        }

        /** 已钉版定义必须命中精确 Provider；schema v1 仅保留兼容选择最新版。 */
        private void requirePinnedProviderAvailable(
                        UiDataSourceDefinition definition) {
                if (!"REGISTERED_PROVIDER".equals(normalize(
                                definition.getSourceType()))) {
                        return;
                }
                boolean pinned = definition.getProviderVersion() != null;
                if ("WRITE".equals(normalize(
                                definition.getOperationKind()))) {
                        actionPlanProvider(definition, pinned);
                } else {
                        dataSourceProvider(definition, pinned);
                }
        }

        private UiDataSourceProvider dataSourceProvider(
                        UiDataSourceDefinition definition,
                        boolean requirePinnedIdentity) {
                UiDataSourceProvider selected = null;
                for (UiDataSourceProvider provider : providers) {
                        if (provider == null || !sameProviderCode(
                                        provider.getCode(),
                                        definition.getProviderCode())) {
                                continue;
                        }
                        int version = providerVersion(provider);
                        String digest = providerDigest(provider);
                        if (requirePinnedIdentity) {
                                if (Objects.equals(
                                                definition.getProviderVersion(), version)
                                                && Objects.equals(
                                                definition.getProviderArtifactDigest(),
                                                digest)) {
                                        return provider;
                                }
                                continue;
                        }
                        selected = preferLatest(
                                        definition.getProviderCode(),
                                        selected,
                                        provider);
                }
                if (selected != null) {
                        return selected;
                }
                throw pinnedProviderMissing(
                                definition,
                                requirePinnedIdentity,
                                "数据源 Provider");
        }

        private UiActionCommandPlanProvider actionPlanProvider(
                        UiDataSourceDefinition definition,
                        boolean requirePinnedIdentity) {
                UiActionCommandPlanProvider selected = null;
                for (UiActionCommandPlanProvider provider
                                : actionCommandPlanProviders) {
                        if (provider == null || !sameProviderCode(
                                        provider.getCode(),
                                        definition.getProviderCode())) {
                                continue;
                        }
                        int version = providerVersion(provider);
                        String digest = providerDigest(provider);
                        if (requirePinnedIdentity) {
                                if (Objects.equals(
                                                definition.getProviderVersion(), version)
                                                && Objects.equals(
                                                definition.getProviderArtifactDigest(),
                                                digest)) {
                                        return provider;
                                }
                                continue;
                        }
                        selected = preferLatestAction(
                                        definition.getProviderCode(),
                                        selected,
                                        provider);
                }
                if (selected != null) {
                        return selected;
                }
                throw pinnedProviderMissing(
                                definition,
                                requirePinnedIdentity,
                                "本地受控写入 Provider");
        }

        private UiDataSourceProvider preferLatest(
                        String code,
                        UiDataSourceProvider current,
                        UiDataSourceProvider candidate) {
                if (current == null
                                || providerVersion(candidate)
                                > providerVersion(current)) {
                        return candidate;
                }
                if (providerVersion(candidate) == providerVersion(current)
                                && !Objects.equals(
                                providerDigest(candidate),
                                providerDigest(current))) {
                        throw ambiguousProvider(code, providerVersion(candidate));
                }
                return current;
        }

        private UiActionCommandPlanProvider preferLatestAction(
                        String code,
                        UiActionCommandPlanProvider current,
                        UiActionCommandPlanProvider candidate) {
                if (current == null
                                || providerVersion(candidate)
                                > providerVersion(current)) {
                        return candidate;
                }
                if (providerVersion(candidate) == providerVersion(current)
                                && !Objects.equals(
                                providerDigest(candidate),
                                providerDigest(current))) {
                        throw ambiguousProvider(code, providerVersion(candidate));
                }
                return current;
        }

        private int providerVersion(UiDataSourceProvider provider) {
                int version = provider.getVersion();
                if (version < 1) {
                        throw invalidProviderIdentity(provider.getCode());
                }
                return version;
        }

        private int providerVersion(UiActionCommandPlanProvider provider) {
                int version = provider.getVersion();
                if (version < 1) {
                        throw invalidProviderIdentity(provider.getCode());
                }
                return version;
        }

        private String providerDigest(UiDataSourceProvider provider) {
                String digest = normalizeDigest(provider.getArtifactDigest());
                if (digest == null) {
                        throw invalidProviderIdentity(provider.getCode());
                }
                return digest;
        }

        private String providerDigest(UiActionCommandPlanProvider provider) {
                String digest = normalizeDigest(provider.getArtifactDigest());
                if (digest == null) {
                        throw invalidProviderIdentity(provider.getCode());
                }
                return digest;
        }

        private boolean sameProviderCode(String left, String right) {
                return StringUtils.hasText(left)
                                && StringUtils.hasText(right)
                                && left.trim().equalsIgnoreCase(right.trim());
        }

        private BusinessConflictException pinnedProviderMissing(
                        UiDataSourceDefinition definition,
                        boolean pinned,
                        String label) {
                String identity = pinned
                                ? " v" + definition.getProviderVersion()
                                + " / " + definition.getProviderArtifactDigest()
                                : "";
                return new BusinessConflictException(
                                "UI_INTERFACE_PINNED_PROVIDER_MISSING",
                                label + " 未注册精确版本: "
                                                + definition.getProviderCode()
                                                + identity);
        }

        private BusinessConflictException ambiguousProvider(
                        String code,
                        int version) {
                return new BusinessConflictException(
                                "UI_INTERFACE_PROVIDER_IDENTITY_CONFLICT",
                                "同一 Provider 编码和版本注册了不同制品: "
                                                + code + " v" + version);
        }

        private BusinessConflictException invalidProviderIdentity(
                        String code) {
                return new BusinessConflictException(
                                "UI_INTERFACE_PROVIDER_IDENTITY_INVALID",
                                "Provider 必须声明正整数版本和 64 位制品摘要: "
                                                + code);
        }

        private void requireActionOperation(
                        UiDataSourceDefinition definition,
                        String ownerType) {
                requirePinnedReadFailurePolicyValue(definition);
                if (StringUtils.hasText(ownerType)
                                && !normalize(ownerType).equals(normalize(
                                definition.getOperationContextType()))) {
                        throw new BusinessForbiddenException(
                                        "UI_INTERFACE_PINNED_OPERATION_FORBIDDEN",
                                        "接口动作上下文必须与当前宿主一致");
                }
                String kind = normalize(definition.getOperationKind());
                if ("READ".equals(kind)) {
                        return;
                }
                if (!"WRITE".equals(kind)) {
                        throw new BusinessForbiddenException(
                                        "UI_INTERFACE_ACTION_KIND_INVALID",
                                        "接口动作类型只支持 READ 或 WRITE");
                }
                if ("INTEGRATION_CONNECTOR".equals(normalize(
                                definition.getSourceType()))) {
                        throw new BusinessForbiddenException(
                                        "UI_INTERFACE_EXTERNAL_WRITE_OUTBOX_REQUIRED",
                                        "外部写入必须提交后异步执行；当前尚未接入持久任务、幂等、补偿和状态查询，不能发布");
                }
                if (!"REGISTERED_PROVIDER".equals(normalize(
                                definition.getSourceType()))
                                || actionCommandPlanProviders.stream().noneMatch(
                                item -> item.getCode().equalsIgnoreCase(
                                                definition.getProviderCode()))) {
                        throw new BusinessForbiddenException(
                                        "UI_INTERFACE_LOCAL_WRITE_PLAN_REQUIRED",
                                        "本地写入必须使用已注册的受控命令计划 Provider，不能直接执行普通接口写操作");
                }
        }

        private void requirePinnedReadFailurePolicyValue(
                        UiDataSourceDefinition definition) {
                Map<String, Object> policy = read(
                                definition.getExecutionPolicyDocument(),
                                "已发布接口操作策略");
                if (!"FAIL".equals(normalize(text(
                                policy.getOrDefault("failurePolicy", "FAIL"))))) {
                        throw new BusinessForbiddenException(
                                        "UI_INTERFACE_PINNED_FALLBACK_FORBIDDEN",
                                        "关联内容接口操作必须使用失败即终止策略");
                }
        }

        private ActionOperationDescriptor descriptor(
                        UiDataSourceDefinition definition) {
                return new ActionOperationDescriptor(
                                definition.getId(),
                                definition.getSourceCode(),
                                definition.getRevision(),
                                definition.getOperationCode(),
                                normalize(definition.getOperationKind()),
                                normalize(definition.getSourceType()),
                                definition.getProviderCode(),
                                normalize(definition.getOperationContextType()));
        }

        private void requirePinnedReadFailurePolicy(
                        UiDataSourceDefinition definition) {
                if (!"READ".equals(normalize(
                                definition.getOperationKind()))) {
                        throw new BusinessForbiddenException(
                                        "UI_INTERFACE_PINNED_OPERATION_FORBIDDEN",
                                        "关联内容只允许执行钉版 READ 操作");
                }
                requirePinnedReadFailurePolicyValue(definition);
        }

        private Map<String, Object> freezeConnectorConfiguration(
                        UiDataSourceDefinition definition) {
                Map<String, Object> config = read(
                                definition.getConfigDocument(),
                                "连接器接口操作配置");
                String configurationId = text(
                                config.get("connectorConfigId"));
                if (!StringUtils.hasText(configurationId)) {
                        throw new BusinessConflictException(
                                        "UI_CONNECTOR_SNAPSHOT_REQUIRED",
                                        "集成连接器操作未配置 connectorConfigId");
                }
                IntegrationConnectorConfigurationSnapshotProvider provider =
                                connectorSnapshotProviders.stream()
                                .filter(item -> item.connectorCode()
                                                .equalsIgnoreCase(
                                                        definition.getProviderCode()))
                                .findFirst()
                                .orElseThrow(() -> new BusinessConflictException(
                                                "UI_CONNECTOR_SNAPSHOT_PROVIDER_MISSING",
                                                "集成连接器未提供安全发布快照能力: "
                                                        + definition.getProviderCode()));
                IntegrationConnectorConfigurationSnapshot value =
                                provider.snapshot(configurationId);
                if (value == null
                                || !definition.getProviderCode().equalsIgnoreCase(
                                                value.connectorCode())
                                || !configurationId.equals(
                                                value.configurationId())
                                || !StringUtils.hasText(
                                                value.snapshotDocument())) {
                        throw new BusinessConflictException(
                                        "UI_CONNECTOR_SNAPSHOT_INVALID",
                                        "连接器返回的发布快照不完整或与绑定不一致");
                }
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("connectorCode", value.connectorCode());
                result.put("configurationId", value.configurationId());
                result.put("revision", value.revision());
                result.put("snapshotDocument", value.snapshotDocument());
                return result;
        }

        private UiDataSourceDefinition readPinnedOperation(
                        String snapshotDocument,
                        String expectedHash) {
                if (!StringUtils.hasText(snapshotDocument)
                                || !StringUtils.hasText(expectedHash)) {
                        throw new BusinessConflictException(
                                        "UI_INTERFACE_PINNED_SNAPSHOT_REQUIRED",
                                        "已发布接口操作缺少不可变快照或哈希");
                }
                String canonical = codec.canonicalize(
                                snapshotDocument,
                                "已发布接口操作快照");
                if (!constantTimeEquals(
                                expectedHash.trim().toLowerCase(Locale.ROOT),
                                sha256(canonical))) {
                        throw new BusinessConflictException(
                                        "UI_INTERFACE_PINNED_SNAPSHOT_TAMPERED",
                                        "已发布接口操作快照完整性校验失败");
                }
                Map<String, Object> value = codec.readObject(
                                canonical,
                                "已发布接口操作快照");
                Set<String> allowed = Set.of(
                                "schemaVersion", "id", "sourceCode", "sourceName",
                                "sourceType", "providerCode", "providerVersion",
                                "providerArtifactDigest", "scopeType", "scopeId",
                                "revision", "operationCode", "operationContextType",
                                "operationKind", "configDocument",
                                "executionPolicyDocument", "inputSchemaDocument",
                                "outputSchemaDocument", "connectorSnapshot");
                int schemaVersion = integer(value.get("schemaVersion"), 0);
                if (!allowed.containsAll(value.keySet())
                                || !Set.of(1, 2).contains(schemaVersion)) {
                        throw new BusinessConflictException(
                                        "UI_INTERFACE_PINNED_SNAPSHOT_INVALID",
                                        "已发布接口操作快照版本或字段不受支持");
                }
                UiDataSourceDefinition definition = new UiDataSourceDefinition();
                definition.setId(requiredSnapshotText(value, "id"));
                definition.setSourceCode(requiredSnapshotText(
                                value, "sourceCode"));
                definition.setSourceName(text(value.get("sourceName")));
                definition.setSourceType(normalize(requiredSnapshotText(
                                value, "sourceType")));
                if (!SOURCE_TYPES.contains(definition.getSourceType())) {
                        throw new BusinessConflictException(
                                        "UI_INTERFACE_PINNED_SNAPSHOT_INVALID",
                                        "已发布接口操作实现类型不受支持");
                }
                definition.setProviderCode(text(value.get("providerCode")));
                if (schemaVersion >= 2
                                && "REGISTERED_PROVIDER".equals(
                                definition.getSourceType())) {
                        int providerVersion = integer(
                                        value.get("providerVersion"), 0);
                        String artifactDigest = normalizeDigest(text(
                                        value.get("providerArtifactDigest")));
                        if (providerVersion < 1 || artifactDigest == null) {
                                throw new BusinessConflictException(
                                                "UI_INTERFACE_PINNED_PROVIDER_IDENTITY_INVALID",
                                                "已发布接口操作缺少有效的 Provider 版本或制品摘要");
                        }
                        definition.setProviderVersion(providerVersion);
                        definition.setProviderArtifactDigest(artifactDigest);
                }
                definition.setScopeType(normalize(requiredSnapshotText(
                                value, "scopeType")));
                if (!SCOPE_TYPES.contains(definition.getScopeType())) {
                        throw new BusinessConflictException(
                                        "UI_INTERFACE_PINNED_SNAPSHOT_INVALID",
                                        "已发布接口操作作用域不受支持");
                }
                definition.setScopeId(text(value.get("scopeId")));
                definition.setRevision(integer(value.get("revision"), 0));
                if (definition.getRevision() < 1) {
                        throw new BusinessConflictException(
                                        "UI_INTERFACE_PINNED_SNAPSHOT_INVALID",
                                        "已发布接口操作修订号无效");
                }
                definition.setOperationCode(requiredSnapshotText(
                                value, "operationCode"));
                definition.setOperationContextType(normalize(
                                requiredSnapshotText(
                                        value, "operationContextType")));
                if (!CONTEXT_TYPES.contains(
                                definition.getOperationContextType())) {
                        throw new BusinessConflictException(
                                        "UI_INTERFACE_PINNED_SNAPSHOT_INVALID",
                                        "已发布接口操作上下文不受支持");
                }
                definition.setOperationKind(normalize(requiredSnapshotText(
                                value, "operationKind")));
                definition.setConfigDocument(requiredJsonDocument(
                                value, "configDocument"));
                definition.setExecutionPolicyDocument(requiredJsonDocument(
                                value, "executionPolicyDocument"));
                definition.setOperationInputSchemaDocument(
                                requiredJsonDocument(
                                        value, "inputSchemaDocument"));
                definition.setOperationOutputSchemaDocument(
                                requiredJsonDocument(
                                        value, "outputSchemaDocument"));
                definition.setEnabled(true);
                definition.setDeleted(0);
                if ("INTEGRATION_CONNECTOR".equals(
                                definition.getSourceType())) {
                        definition.setConnectorConfigurationSnapshot(
                                        connectorSnapshot(value));
                        requireConnectorSnapshotBinding(definition);
                }
                definitionValidator.validateNoForbiddenKeys(
                                read(definition.getConfigDocument(),
                                        "已发布接口操作配置"),
                                "config");
                definitionValidator.validateExecutionPolicy(read(
                                definition.getExecutionPolicyDocument(),
                                "已发布接口操作策略"));
                return definition;
        }

        private void requireConnectorSnapshotBinding(
                        UiDataSourceDefinition definition) {
                IntegrationConnectorConfigurationSnapshot snapshot =
                                definition.getConnectorConfigurationSnapshot();
                Map<String, Object> config = read(
                                definition.getConfigDocument(),
                                "已发布连接器操作配置");
                if (!StringUtils.hasText(definition.getProviderCode())
                                || !definition.getProviderCode()
                                .equalsIgnoreCase(snapshot.connectorCode())
                                || !Objects.equals(
                                                text(config.get(
                                                        "connectorConfigId")),
                                                snapshot.configurationId())) {
                        throw new BusinessConflictException(
                                        "UI_CONNECTOR_PINNED_SNAPSHOT_CONFLICT",
                                        "已发布连接器快照与接口操作绑定不一致");
                }
        }

        private IntegrationConnectorConfigurationSnapshot connectorSnapshot(
                        Map<String, Object> value) {
                if (!(value.get("connectorSnapshot")
                                instanceof Map<?, ?> raw)) {
                        throw new BusinessConflictException(
                                        "UI_CONNECTOR_PINNED_SNAPSHOT_REQUIRED",
                                        "已发布连接器操作缺少配置快照");
                }
                Map<String, Object> snapshot = stringMap(raw);
                if (!Set.of(
                                "connectorCode", "configurationId", "revision",
                                "snapshotDocument").containsAll(
                                        snapshot.keySet())) {
                        throw new BusinessConflictException(
                                        "UI_CONNECTOR_PINNED_SNAPSHOT_INVALID",
                                        "已发布连接器快照包含未知字段");
                }
                return new IntegrationConnectorConfigurationSnapshot(
                                requiredSnapshotText(
                                        snapshot, "connectorCode"),
                                requiredSnapshotText(
                                        snapshot, "configurationId"),
                                text(snapshot.get("revision")),
                                requiredSnapshotText(
                                        snapshot, "snapshotDocument"));
        }

        private String requiredSnapshotText(
                        Map<String, Object> value,
                        String key) {
                String result = text(value.get(key));
                if (!StringUtils.hasText(result)) {
                        throw new BusinessConflictException(
                                        "UI_INTERFACE_PINNED_SNAPSHOT_INVALID",
                                        "已发布接口操作快照缺少字段: " + key);
                }
                return result;
        }

        private String requiredJsonDocument(
                        Map<String, Object> value,
                        String key) {
                String document = text(value.get(key));
                if (!StringUtils.hasText(document)) {
                        // 空配置、策略或 Schema 在快照中也使用显式空对象，
                        // 避免“缺少”被解释成运行时默认值。
                        return "{}";
                }
                codec.readObject(document, "已发布接口操作 " + key);
                return codec.canonicalize(
                                document,
                                "已发布接口操作 " + key);
        }

        private String canonicalDocument(String document, String label) {
                return StringUtils.hasText(document)
                                ? codec.canonicalize(document, label) : "{}";
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
                        UiDataSourceProvider provider = dataSourceProvider(
                                        definition,
                                        definition.getProviderVersion() != null);
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
                                                        .configurationSnapshot(
                                                                        definition
                                                                                .getConnectorConfigurationSnapshot())
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
                target.setConnectorConfigurationSnapshot(
                                source.getConnectorConfigurationSnapshot());
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
                key.put("providerVersion", definition.getProviderVersion());
                key.put("providerArtifactDigest",
                                definition.getProviderArtifactDigest());
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

        private String normalizeDigest(String value) {
                if (!StringUtils.hasText(value)) {
                        return null;
                }
                String normalized = value.trim().toLowerCase(Locale.ROOT);
                return normalized.matches("[a-f0-9]{64}")
                                ? normalized : null;
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

        private String sha256(String value) {
                try {
                        return HexFormat.of().formatHex(
                                        MessageDigest.getInstance("SHA-256")
                                                        .digest(value.getBytes(
                                                                StandardCharsets.UTF_8)));
                } catch (Exception exception) {
                        throw new IllegalStateException(
                                        "运行环境不支持 SHA-256",
                                        exception);
                }
        }

        private boolean constantTimeEquals(
                        String expected,
                        String actual) {
                return MessageDigest.isEqual(
                                expected.getBytes(StandardCharsets.UTF_8),
                                actual.getBytes(StandardCharsets.UTF_8));
        }

        /** 可存入宿主发布快照的接口操作钉版结果。 */
        public record PublishedOperationSnapshot(
                        String serviceId,
                        String sourceCode,
                        Integer serviceRevision,
                        String operationCode,
                        String document,
                        String hash) {
        }

        /** 设计态和发布态均可使用的受控动作操作描述。 */
        public record ActionOperationDescriptor(
                        String serviceId,
                        String sourceCode,
                        Integer serviceRevision,
                        String operationCode,
                        String operationKind,
                        String sourceType,
                        String providerCode,
                        String contextType) {
        }

        private record CacheEntry(Object value, long expiresAt) {
        }

        private record ProviderIdentity(
                        int version,
                        String artifactDigest) {
        }

}
