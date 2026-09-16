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
import com.workflow.contracts.entity.ui.spi.UiDataSourceProvider;
import com.workflow.contracts.ui.UiDataSourceUsages;
import com.workflow.contracts.ui.UiInvocationContext;
import com.workflow.contracts.ui.UiActionCommandPlan;
import com.workflow.contracts.entity.ui.spi.UiActionCommandPlanProvider;
import com.workflow.entity.ui.api.request.UiExtensionExecuteRequest;
import com.workflow.entity.ui.api.request.UiBoundExtensionExecuteRequest;
import com.workflow.entity.ui.api.request.UiExtensionDefinitionSaveRequest;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.admin.dictionary.infrastructure.persistence.record.SysDictItem;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.entity.ui.infrastructure.persistence.record.UiExtensionDefinition;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiExtensionDefinitionMapper;
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
import java.util.regex.Pattern;

/**
 * 可调用接口扩展的定义与执行服务。
 *
 * <p>
 * 支持字典、静态选项、注册提供器、运行时上下文和结构化计算等
 * 接口实现类型，配置中禁止 SQL/脚本/URL 等危险字段；执行链路经
 * {@link UiDataSourceExecutionAccessService} 授权后按数据源类型分派，
 * 并提供带 TTL 的结果缓存。
 * </p>
 */
@Service
public class UiInterfaceExtensionService {
        private static final Set<String> SOURCE_TYPES = Set.of(
                        "DICTIONARY", "STATIC_OPTIONS", "REGISTERED_PROVIDER",
                        "RUNTIME_CONTEXT", "STRUCTURED_COMPUTE");
        private static final Set<String> SCOPE_TYPES = Set.of("GLOBAL", "ENTITY", "FORM", "LIST");
        private static final Set<String> CONTEXT_TYPES = Set.of("FORM", "LIST", "ENTITY");
        /** 与数据库 varchar(255) 对齐，并允许迁移后的 source.operation 编码。 */
        private static final Pattern EXTENSION_KEY =
                        Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,254}");
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
        /** 统一扩展定义持久化入口；本服务只访问 INTERFACE 类型。 */
        private final UiExtensionDefinitionMapper mapper;
        /** 表单作用域对象查询入口。 */
        private final EntityFormMapper formMapper;
        /** 列表作用域对象查询入口。 */
        private final EntityListConfigMapper listMapper;
        /** 实体作用域配置权限策略。 */
        private final EntityDefinitionAccessPolicy entityAccessPolicy;
        /** 表单和列表所属实体的 UI 配置权限策略。 */
        private final EntityUiConfigurationPolicy entityUiConfigurationPolicy;
        /** 字典型接口扩展的数据读取入口。 */
        private final SysDictItemService dictItemService;
        /** 草稿、发布绑定及数据权限执行授权服务。 */
        private final UiDataSourceExecutionAccessService executionAccessService;
        /** 阻止删除仍被线上发布版本引用的接口扩展。 */
        private UiPublishedDataSourceReferenceGuard publishedReferenceGuard;
        /** 强类型 FORM/LIST/ENTITY 调用上下文工厂。 */
        private final UiInvocationContextFactory invocationContextFactory;
        /** 接口定义、输入输出 Schema 和执行策略校验器。 */
        private final UiExtensionDefinitionValidator definitionValidator;
        /** 当前部署注册的接口 Provider。 */
        private final List<UiDataSourceProvider> providers;
        /** 只生成结构化实体变更计划的本地关联动作 Provider。 */
        private List<UiActionCommandPlanProvider> actionCommandPlanProviders =
                        List.of();
        /** JSON 配置、Schema 和操作文档编解码器。 */
        private final JsonDocumentCodec codec;
        /** Provider 超时执行使用的任务执行器。 */
        private final TaskExecutor taskExecutor;

        /** 接口执行结果缓存，按 key+版本+内容哈希索引。 */
        private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

        /**
         * 构造接口扩展执行服务，注入数据源提供器和异步执行器。
         *
         * @param mapper                 数据源定义 Mapper
         * @param formMapper             表单 Mapper
         * @param listMapper             列表配置 Mapper
         * @param entityAccessPolicy     实体访问策略
         * @param entityUiConfigurationPolicy 表单和列表所属实体的配置策略
         * @param dictItemService        字典项服务
         * @param executionAccessService 数据源执行访问控制服务
         * @param invocationContextFactory 强类型调用上下文工厂
         * @param definitionValidator    接口扩展定义与 Schema 校验器
         * @param providers              注册的数据源提供器集合
         * @param codec                  JSON 文档编解码器
         * @param taskExecutor           应用异步任务执行器
         */
        public UiInterfaceExtensionService(
                        UiExtensionDefinitionMapper mapper,
                        EntityFormMapper formMapper,
                        EntityListConfigMapper listMapper,
                        EntityDefinitionAccessPolicy entityAccessPolicy,
                        EntityUiConfigurationPolicy entityUiConfigurationPolicy,
                        SysDictItemService dictItemService,
                        UiDataSourceExecutionAccessService executionAccessService,
                        UiInvocationContextFactory invocationContextFactory,
                        UiExtensionDefinitionValidator definitionValidator,
                        List<UiDataSourceProvider> providers,
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

        /** 可选注入本地受控写计划 Provider，不影响只读接口扩展部署。 */
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
        public List<UiExtensionDefinition> list(
                        String scopeType,
                        String scopeId,
                        String sourceType) {
                LambdaQueryWrapper<UiExtensionDefinition> query = new LambdaQueryWrapper<>();
                if (StringUtils.hasText(scopeType)) {
                        query.eq(UiExtensionDefinition::getScopeType, normalize(scopeType));
                }
                if (StringUtils.hasText(scopeId)) {
                        query.eq(UiExtensionDefinition::getScopeId, scopeId);
                }
                if (StringUtils.hasText(sourceType)) {
                        query.eq(UiExtensionDefinition::getImplementationType, normalize(sourceType));
                }
                query.eq(UiExtensionDefinition::getExtensionType, "INTERFACE")
                                .eq(UiExtensionDefinition::getDeleted, 0)
                                .orderByAsc(UiExtensionDefinition::getExtensionKey);
                return mapper.selectList(query);
        }

        public Map<String, Object> catalog() {
                List<Map<String, Object>> providerOptions = providers.stream()
                                .map(provider -> Map.<String, Object>of(
                                                "code", provider.getCode(),
                                                "name", provider.getDisplayName(),
                                                "schema", provider.configurationSchema()))
                                .toList();
                Map<String, Object> catalog = new LinkedHashMap<>();
                catalog.put("implementationTypes", SOURCE_TYPES);
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
                catalog.put("failurePolicies", List.of("FAIL", "EMPTY", "NULL"));
                return catalog;
        }

        @Transactional(rollbackFor = Exception.class)
        public UiExtensionDefinition save(
                        UiExtensionDefinitionSaveRequest request) {
                if (request == null) {
                        validateRequest(null);
                }
                UiExtensionDefinition current = StringUtils.hasText(request.getId())
                                ? mapper.selectById(request.getId().trim())
                                : null;
                if (StringUtils.hasText(request.getId()) && current == null) {
                        throw new IllegalArgumentException("接口扩展不存在");
                }
                if (current != null) {
                        requireStableIdentity(current, request);
                }
                validateRequest(request);
                if (current != null) {
                        requireRevision(request.getExpectedRevision(), current);
                }
                UiExtensionDefinition value = current == null ? new UiExtensionDefinition() : current;
                value.setExtensionType("INTERFACE");
                value.setExtensionKey(request.getExtensionKey().trim());
                value.setDisplayName(request.getDisplayName().trim());
                value.setVersion(request.getVersion() == null
                                ? 1 : request.getVersion());
                value.setSnapshotVersion(request.getSnapshotVersion() == null
                                ? 1 : request.getSnapshotVersion());
                value.setImplementationType(normalize(
                                request.getImplementationType()));
                value.setProviderCode(blankToNull(request.getProviderCode()));
                value.setScopeType(normalize(
                                StringUtils.hasText(request.getScopeType())
                                                ? request.getScopeType()
                                                : "GLOBAL"));
                value.setScopeId(blankToNull(request.getScopeId()));
                value.setImplementationConfigDocument(write(
                                request.getImplementationConfig(), "接口实现配置"));
                value.setExecutionPolicyDocument(
                                write(request.getExecutionPolicy(), "接口执行策略"));
                value.setInputSchemaDocument(write(
                                request.getInputSchema(), "接口输入Schema"));
                value.setOutputSchemaDocument(write(
                                request.getOutputSchema(), "接口输出Schema"));
                value.setInterfaceKind(normalize(request.getInterfaceKind()));
                value.setInterfaceContextType(normalize(
                                request.getInterfaceContextType()));
                value.setProviderOperationCode(StringUtils.hasText(
                                request.getProviderOperationCode())
                                ? request.getProviderOperationCode().trim()
                                : value.getExtensionKey());
                value.setStatus(StringUtils.hasText(request.getStatus())
                                ? normalize(request.getStatus()) : "ACTIVE");
                value.setUpdatedAt(LocalDateTime.now());
                value.setDeleted(0);
                if (current == null) {
                        value.setRevision(1);
                        value.setCreatedAt(LocalDateTime.now());
                        mapper.insert(value);
                } else {
                        int currentRevision = current.getRevision();
                        value.setRevision(currentRevision + 1);
                        UpdateWrapper<UiExtensionDefinition> wrapper = new UpdateWrapper<>();
                        wrapper.eq("id", value.getId())
                                        .eq("revision", currentRevision)
                                        .eq("deleted", 0)
                                        .set("extension_type", "INTERFACE")
                                        .set("extension_key", value.getExtensionKey())
                                        .set("display_name", value.getDisplayName())
                                        .set("version", value.getVersion())
                                        .set("snapshot_version", value.getSnapshotVersion())
                                        .set("implementation_type", value.getImplementationType())
                                        .set("provider_code", value.getProviderCode())
                                        .set("scope_type", value.getScopeType())
                                        .set("scope_id", value.getScopeId())
                                        .set("implementation_config_document", value.getImplementationConfigDocument())
                                        .set("execution_policy_document", value.getExecutionPolicyDocument())
                                        .set("input_schema_document", value.getInputSchemaDocument())
                                        .set("output_schema_document", value.getOutputSchemaDocument())
                                        .set("interface_kind", value.getInterfaceKind())
                                        .set("interface_context_type", value.getInterfaceContextType())
                                        .set("provider_operation_code", value.getProviderOperationCode())
                                        .set("status", value.getStatus())
                                        .set("revision", value.getRevision())
                                        .set("update_time", value.getUpdatedAt());
                        if (mapper.update(null, wrapper) != 1) {
                                throw new RevisionConflictException(
                                                "接口扩展已被其他人修改，请刷新后重试",
                                                mapper.selectById(value.getId()));
                        }
                }
                return mapper.selectById(value.getId());
        }

        /**
         * 更新只能落在既有 INTERFACE 记录上，且稳定 key 不允许修改。该服务可能
         * 被内部代码直接调用，因此不能只依赖统一目录入口的身份校验。
         */
        private void requireStableIdentity(
                        UiExtensionDefinition current,
                        UiExtensionDefinitionSaveRequest request) {
                if (!"INTERFACE".equals(normalize(
                                current.getExtensionType()))) {
                        throw new IllegalArgumentException(
                                        "更新目标不是接口扩展");
                }
                if (!StringUtils.hasText(request.getExtensionKey())) {
                        throw new IllegalArgumentException("接口扩展编码不能为空");
                }
                if (!Objects.equals(
                                current.getExtensionKey(),
                                request.getExtensionKey().trim())) {
                        throw new IllegalArgumentException(
                                        "更新时不能修改接口扩展注册名");
                }
        }

        @Transactional(rollbackFor = Exception.class)
        public void delete(String id, Integer expectedRevision) {
                UiExtensionDefinition current = mapper.selectById(id);
                if (current == null || !"INTERFACE".equals(normalize(
                                current.getExtensionType()))) {
                        throw new IllegalArgumentException("接口扩展不存在");
                }
                requireRevision(expectedRevision, current);
                // 软删除会让运行时立即拒绝该接口扩展；必须先在同一事务内确认
                // 所有实际生效发布快照均已解除引用。
                if (publishedReferenceGuard == null) {
                        throw new BusinessConflictException(
                                        "UI_DATA_SOURCE_REFERENCE_GUARD_UNAVAILABLE",
                                        "接口扩展发布引用保护不可用，拒绝删除");
                }
                publishedReferenceGuard.requireNoExecutableReferences(
                                id, current.getLegacyServiceId());
                UpdateWrapper<UiExtensionDefinition> wrapper = new UpdateWrapper<>();
                wrapper.eq("id", id)
                                .eq("revision", current.getRevision())
                                .eq("deleted", 0)
                                .set("deleted", 1)
                                .setSql("revision = revision + 1")
                                .set("update_time", LocalDateTime.now());
                if (mapper.update(null, wrapper) != 1) {
                        throw new RevisionConflictException(
                                        "接口扩展已被其他人修改，请刷新后重试",
                                        mapper.selectById(id));
                }
        }

        public Object preview(String id, UiExtensionExecuteRequest request) {
                requireOperationRequest(request);
                UiExtensionDefinition definition =
                                requireExecutableDefinition(id);
                request.setOperationCode(
                                definition.getProviderOperationCode());
                requireUsage(request.getUsage());
                UiDataSourceExecutionAuthorization authorization =
                                executionAccessService.authorizePreview(
                                                definition,
                                                request);
                requireOperationContext(definition, authorization.configType());
                return executeAuthorized(definition, request, authorization);
        }

        public Object execute(String id, UiExtensionExecuteRequest request) {
                requireOperationRequest(request);
                UiExtensionDefinition definition =
                                requireExecutableDefinition(id);
                request.setOperationCode(
                                definition.getProviderOperationCode());
                return executeResolved(definition, request);
        }

        public Object executeBoundOperation(
                        UiBoundExtensionExecuteRequest request) {
                return executeBoundOperation(request, null, null, false);
        }

        /**
         * 按服务端委托策略固定的历史 Form Release 执行原生数据源绑定。
         *
         * <p>该入口只供已经通过 Embed MVC 授权的控制器调用；发布坐标来自
         * 服务端请求属性而不是公开 DTO，避免会话打开后 ACTIVE 切换导致行为漂移。</p>
         */
        public Object executeBoundOperationAtRelease(
                        UiBoundExtensionExecuteRequest request,
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
                        UiBoundExtensionExecuteRequest request,
                        String releaseId,
                        Integer releaseVersion,
                        boolean pinned) {
                if (request == null
                                || !StringUtils.hasText(request.getOwnerType())
                                || !StringUtils.hasText(request.getOwnerId())
                                || !StringUtils.hasText(request.getBindingCode())
                                || !StringUtils.hasText(request.getTargetType())
                                || !StringUtils.hasText(request.getExtensionId())) {
                        throw new IllegalArgumentException(
                                        "接口执行缺少 owner、binding 或 extensionId");
                }
                if (UiDataSourceUsages.FORM_BUTTON_CLICK.equals(
                                normalize(request.getBindingCode()))) {
                        // 表单按钮必须经过按钮可用性/权限、幂等回执和钉版定义校验；
                        // 通用绑定接口只能验证“引用存在”，不能替代完整事件运行时。
                        throw new BusinessForbiddenException(
                                        "UI_EVENT_RUNTIME_REQUIRED",
                                        "FORM_BUTTON_CLICK 必须通过 UI 事件运行接口执行");
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
                                request.getExtensionId().trim(),
                                null);
                UiExtensionDefinition definition = requireExecutableDefinition(
                                bindingPoint.extensionId());
                UiExtensionExecuteRequest internal = new UiExtensionExecuteRequest();
                internal.setUsage(bindingPoint.bindingCode());
                internal.setOperationCode(
                                definition.getProviderOperationCode());
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
                return executeResolved(definition, internal);
        }

        private Object executeResolved(
                        UiExtensionDefinition definition,
                        UiExtensionExecuteRequest request) {
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
         * 执行指定接口扩展；operationCode 仅供历史快照内部兼容。
         */
        public Object executeOperation(
                        String id,
                        String operationCode,
                        UiExtensionExecuteRequest request) {
                UiExtensionDefinition definition = requireExecutableDefinition(
                                id, operationCode);
                UiExtensionDefinition operationDefinition = resolveOperationDefinition(definition, operationCode);
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
         * 执行字段事件接口，授权绑定使用事件解析时的同一份有效表单快照。
         * 流程热修复新增的回填步骤可能不在基础发布中，不能重新查询基础版判断是否绑定。
         *
         * @param id 已发布事件步骤引用的接口 ID
         * @param operationCode 历史接口路由编码，当前 extensionId 引用可为空
         * @param request 已完成版本及操作权限校验的内部字段事件请求
         * @param resolvedHostSnapshot 已验真的有效表单快照
         * @param expectedHostHash 解析时确认的快照哈希
         * @return Provider 的已校验输出
         */
        public Object executeResolvedFormFieldOperation(
                        String id,
                        String operationCode,
                        UiExtensionExecuteRequest request,
                        Map<String, Object> resolvedHostSnapshot,
                        String expectedHostHash) {
                UiExtensionDefinition definition = resolveOperationDefinition(
                                requireExecutableDefinition(id, operationCode), operationCode);
                requireUsage(request == null ? null : request.getUsage());
                UiDataSourceExecutionAuthorization authorization =
                                executionAccessService.authorizeResolvedFormFieldEvent(
                                                definition, request, resolvedHostSnapshot, expectedHostHash);
                requireOperationContext(definition, authorization.configType());
                return executeAuthorized(definition, request, authorization);
        }

        /**
         * 在管理端调试指定接口扩展，不要求该接口已经绑定到发布页面。
         */
        public Object previewOperation(
                        String id,
                        String operationCode,
                        UiExtensionExecuteRequest request) {
                UiExtensionDefinition definition = requireExecutableDefinition(
                                id, operationCode);
                UiExtensionDefinition operationDefinition = resolveOperationDefinition(definition, operationCode);
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
                        UiExtensionExecuteRequest request) {
                if (request == null
                                || !UiDataSourceUsages.RELATED_CONTENT_RESOLVE.equals(
                                                normalize(request.getUsage()))) {
                        throw new IllegalArgumentException(
                                        "关联内容真实数据测试必须使用专用解析位置");
                }
                UiExtensionDefinition operationDefinition =
                                resolveOperationDefinition(
                                                requireExecutableDefinition(
                                                                id,
                                                                operationCode),
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
         * 固定一条接口扩展的完整可执行定义。
         *
         * <p>新快照使用 extension/interface 命名并保存配置、Schema、执行策略和
         * Provider 内部路由，运行时因此无需回读可变定义。</p>
         *
         * @param extensionId 接口扩展 ID
         * @return 规范 JSON 文档及 SHA-256
         */
        public PublishedOperationSnapshot freezeExtension(
                        String extensionId) {
                return freezeDefinition(requireExecutableDefinition(
                                extensionId));
        }

        /**
         * 兼容不可变历史引用的钉版入口。
         *
         * @param id 当前 extensionId 或迁移前 serviceId
         * @param operationCode 迁移前操作编码；当前调用应使用
         * {@link #freezeExtension(String)}
         */
        public PublishedOperationSnapshot freezeOperation(
                        String id,
                        String operationCode) {
                UiExtensionDefinition resolved = resolveOperationDefinition(
                                requireExecutableDefinition(id, operationCode),
                                operationCode);
                return freezeDefinition(resolved);
        }

        private PublishedOperationSnapshot freezeDefinition(
                        UiExtensionDefinition resolved) {
                Map<String, Object> snapshot = new LinkedHashMap<>();
                snapshot.put("schemaVersion", 3);
                snapshot.put("extensionType", "INTERFACE");
                snapshot.put("extensionId", resolved.getId());
                snapshot.put("extensionKey", resolved.getExtensionKey());
                snapshot.put("displayName", resolved.getDisplayName());
                snapshot.put("implementationType", normalize(
                                resolved.getImplementationType()));
                snapshot.put("providerCode", resolved.getProviderCode());
                if ("REGISTERED_PROVIDER".equals(
                                normalize(resolved.getImplementationType()))) {
                        ProviderIdentity identity = currentProviderIdentity(
                                        resolved);
                        snapshot.put("providerVersion", identity.version());
                        snapshot.put(
                                        "providerArtifactDigest",
                                        identity.artifactDigest());
                }
                snapshot.put("scopeType", normalize(resolved.getScopeType()));
                snapshot.put("scopeId", resolved.getScopeId());
                snapshot.put("extensionRevision", resolved.getRevision());
                snapshot.put("providerOperationCode",
                                resolved.getProviderOperationCode());
                snapshot.put("interfaceContextType",
                                resolved.getInterfaceContextType());
                snapshot.put("interfaceKind", resolved.getInterfaceKind());
                snapshot.put("implementationConfigDocument", canonicalDocument(
                                resolved.getImplementationConfigDocument(),
                                "接口扩展配置"));
                snapshot.put("executionPolicyDocument", canonicalDocument(
                                resolved.getExecutionPolicyDocument(),
                                "接口扩展执行策略"));
                snapshot.put("inputSchemaDocument", canonicalDocument(
                                resolved.getInputSchemaDocument(),
                                "接口扩展输入 Schema"));
                snapshot.put("outputSchemaDocument", canonicalDocument(
                                resolved.getOutputSchemaDocument(),
                                "接口扩展输出 Schema"));
                String document = codec.canonicalize(
                                codec.write(snapshot, "接口扩展发布快照"),
                                "接口扩展发布快照");
                return new PublishedOperationSnapshot(
                                resolved.getId(),
                                resolved.getExtensionKey(),
                                resolved.getRevision(),
                                resolved.getProviderOperationCode(),
                                document,
                                sha256(document));
        }

        /**
         * 从宿主快照执行固定接口。当前接口扩展被修改或重新发布时，
         * 旧宿主仍使用这份定义；快照或哈希不完整则直接 fail-closed。
         */
        public Object executePinnedOperation(
                        String snapshotDocument,
                        String expectedHash,
                        UiExtensionExecuteRequest request) {
                UiExtensionDefinition definition = readPinnedOperation(
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
         * 从同一次表单按钮事件解析出的可信宿主快照执行固定 READ 操作。
         *
         * <p>该入口不会再次解析 ACTIVE/历史基础发布，因此批准热修复和并发版本
         * 切换都不会改变本次请求的绑定身份。宿主快照、接口快照各自独立验哈希，
         * 并继续执行当前用户 DataScope。</p>
         *
         * @param snapshotDocument 钉版接口定义文档
         * @param expectedHash 钉版接口定义哈希
         * @param request 服务端构造的表单按钮操作请求
         * @param resolvedHostSnapshot 同一次事件解析得到的有效表单快照
         * @param expectedHostHash 有效表单快照的可信内容哈希
         * @return Provider 的已校验结果
         */
        public Object executePinnedOperation(
                        String snapshotDocument,
                        String expectedHash,
                        UiExtensionExecuteRequest request,
                        Map<String, Object> resolvedHostSnapshot,
                        String expectedHostHash) {
                UiExtensionDefinition definition = readPinnedOperation(
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
                                executionAccessService
                                                .authorizeResolvedFormButton(
                                                                definition,
                                                                request,
                                                                resolvedHostSnapshot,
                                                                expectedHostHash);
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
                UiExtensionDefinition definition = readPinnedOperation(
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
                                        "已发布接口快照与历史绑定身份不一致");
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
         * 校验新版宿主快照中以 {@code extensionId} 标识的钉版读接口。
         *
         * <p>Provider 路由已包含在独立验哈希的可执行快照中，不再要求
         * 宿主配置重复保存 operationCode，从而保持“一条扩展=一个接口”。</p>
         */
        public void validatePinnedReadExtension(
                        String snapshotDocument,
                        String expectedHash,
                        String extensionId,
                        String extensionKey,
                        Integer extensionRevision,
                        String ownerType) {
                UiExtensionDefinition definition = readPinnedOperation(
                                snapshotDocument,
                                expectedHash);
                if (!Objects.equals(extensionId, definition.getId())
                                || !Objects.equals(
                                                extensionKey,
                                                definition.getSourceCode())
                                || !Objects.equals(
                                                extensionRevision,
                                                definition.getRevision())) {
                        throw new BusinessConflictException(
                                        "UI_INTERFACE_PINNED_IDENTITY_CONFLICT",
                                        "已发布接口快照与扩展标识或修订号不一致");
                }
                requirePinnedReadFailurePolicy(definition);
                requirePinnedProviderAvailable(definition);
                if (!normalize(ownerType).equals(normalize(
                                definition.getOperationContextType()))) {
                        throw new BusinessForbiddenException(
                                        "UI_INTERFACE_PINNED_OPERATION_FORBIDDEN",
                                        "当前宿主不允许执行该钉版 READ 接口");
                }
        }

        /** 校验 extensionId 引用的关联动作接口是否可安全发布。 */
        public ActionOperationDescriptor validateActionExtension(
                        String extensionId,
                        String ownerType) {
                UiExtensionDefinition definition = requireExecutableDefinition(
                                extensionId);
                requireActionOperation(definition, ownerType);
                return descriptor(definition);
        }

        /**
         * 校验历史关联动作引用。
         *
         * <p>仅用于读取 {@code serviceId + operationCode} 的旧配置；新草稿应调用
         * {@link #validateActionExtension(String, String)}。</p>
         */
        public ActionOperationDescriptor validateActionOperation(
                        String serviceId,
                        String operationCode,
                        String ownerType) {
                UiExtensionDefinition definition = resolveOperationDefinition(
                                requireExecutableDefinition(
                                                serviceId, operationCode),
                                operationCode);
                requireActionOperation(definition, ownerType);
                return descriptor(definition);
        }

        /** 固定 extensionId 引用且已通过动作治理校验的接口。 */
        public PublishedOperationSnapshot freezeActionExtension(
                        String extensionId) {
                UiExtensionDefinition definition = requireExecutableDefinition(
                                extensionId);
                requireActionOperation(definition, null);
                return freezeDefinition(definition);
        }

        /**
         * 固定历史动作接口引用；新草稿应调用
         * {@link #freezeActionExtension(String)}。
         */
        public PublishedOperationSnapshot freezeActionOperation(
                        String serviceId,
                        String operationCode) {
                UiExtensionDefinition definition = resolveOperationDefinition(
                                requireExecutableDefinition(
                                                serviceId, operationCode),
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
                UiExtensionDefinition definition = readPinnedOperation(
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

        /** 校验只保存 extensionId 的新版关联动作钉版引用。 */
        public ActionOperationDescriptor validatePinnedActionExtension(
                        String snapshotDocument,
                        String expectedHash,
                        String extensionId,
                        String extensionKey,
                        Integer extensionRevision,
                        String ownerType) {
                UiExtensionDefinition definition = readPinnedOperation(
                                snapshotDocument, expectedHash);
                if (!Objects.equals(extensionId, definition.getId())
                                || !Objects.equals(
                                                extensionKey,
                                                definition.getSourceCode())
                                || !Objects.equals(
                                                extensionRevision,
                                                definition.getRevision())) {
                        throw new BusinessConflictException(
                                        "UI_INTERFACE_PINNED_IDENTITY_CONFLICT",
                                        "已发布动作接口快照与扩展标识或修订号不一致");
                }
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
                        UiExtensionExecuteRequest request) {
                UiExtensionDefinition definition = readPinnedOperation(
                                snapshotDocument, expectedHash);
                requireActionOperation(
                                definition,
                                request == null ? null : request.getConfigType());
                if (!"WRITE".equals(normalize(definition.getOperationKind()))) {
                        throw new BusinessForbiddenException(
                                        "UI_INTERFACE_ACTION_KIND_INVALID",
                                        "当前接口不是本地受控写入接口");
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
                                "接口输入Schema");
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
                                                "接口扩展配置")),
                                Collections.unmodifiableMap(
                                                new LinkedHashMap<>(input)));
                if (plan == null) {
                        throw new BusinessConflictException(
                                        "UI_INTERFACE_ACTION_PLAN_EMPTY",
                                        "本地受控写入 Provider 未返回命令计划");
                }
                Map<String, Object> outputSchema = read(
                                definition.getOperationOutputSchemaDocument(),
                                "接口输出Schema");
                definitionValidator.validateSchemaDefinition(
                                outputSchema, "数据源输出Schema");
                definitionValidator.validateSchemaValue(
                                outputSchema, plan.result(), "数据源输出");
                return plan;
        }

        private void requirePinnedIdentity(
                        UiExtensionDefinition definition,
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
                                        "已发布接口快照与历史绑定身份不一致");
                }
        }

        /**
         * 发布时选择当前最高版本 Provider，并把版本与制品摘要写入宿主快照。
         * 同一编码和版本若出现不同摘要会直接失败，避免依赖注入顺序决定行为。
         */
        private ProviderIdentity currentProviderIdentity(
                        UiExtensionDefinition definition) {
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
                        UiExtensionDefinition definition) {
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
                        UiExtensionDefinition definition,
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
                        UiExtensionDefinition definition,
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
                        UiExtensionDefinition definition,
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
                        UiExtensionDefinition definition,
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
                        UiExtensionDefinition definition) {
                Map<String, Object> policy = read(
                                definition.getExecutionPolicyDocument(),
                                "已发布接口策略");
                if (!"FAIL".equals(normalize(text(
                                policy.getOrDefault("failurePolicy", "FAIL"))))) {
                        throw new BusinessForbiddenException(
                                        "UI_INTERFACE_PINNED_FALLBACK_FORBIDDEN",
                                        "关联内容接口必须使用失败即终止策略");
                }
        }

        private ActionOperationDescriptor descriptor(
                        UiExtensionDefinition definition) {
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
                        UiExtensionDefinition definition) {
                if (!"READ".equals(normalize(
                                definition.getOperationKind()))) {
                        throw new BusinessForbiddenException(
                                        "UI_INTERFACE_PINNED_OPERATION_FORBIDDEN",
                                        "关联内容只允许执行钉版 READ 操作");
                }
                requirePinnedReadFailurePolicyValue(definition);
        }

        private UiExtensionDefinition readPinnedOperation(
                        String snapshotDocument,
                        String expectedHash) {
                if (!StringUtils.hasText(snapshotDocument)
                                || !StringUtils.hasText(expectedHash)) {
                        throw new BusinessConflictException(
                                        "UI_INTERFACE_PINNED_SNAPSHOT_REQUIRED",
                                        "已发布接口缺少不可变快照或哈希");
                }
                String canonical = codec.canonicalize(
                                snapshotDocument,
                                "已发布接口快照");
                if (!constantTimeEquals(
                                expectedHash.trim().toLowerCase(Locale.ROOT),
                                sha256(canonical))) {
                        throw new BusinessConflictException(
                                        "UI_INTERFACE_PINNED_SNAPSHOT_TAMPERED",
                                        "已发布接口快照完整性校验失败");
                }
                Map<String, Object> value = codec.readObject(
                                canonical,
                                "已发布接口快照");
                Set<String> legacyAllowed = Set.of(
                                "schemaVersion", "id", "sourceCode", "sourceName",
                                "sourceType", "providerCode", "providerVersion",
                                "providerArtifactDigest", "scopeType", "scopeId",
                                "revision", "operationCode", "operationContextType",
                                "operationKind", "configDocument",
                                "executionPolicyDocument", "inputSchemaDocument",
                                "outputSchemaDocument");
                Set<String> extensionAllowed = Set.of(
                                "schemaVersion", "extensionType", "extensionId",
                                "extensionKey", "displayName", "implementationType",
                                "providerCode", "providerVersion",
                                "providerArtifactDigest", "scopeType", "scopeId",
                                "extensionRevision", "providerOperationCode",
                                "interfaceContextType", "interfaceKind",
                                "implementationConfigDocument",
                                "executionPolicyDocument", "inputSchemaDocument",
                                "outputSchemaDocument");
                int schemaVersion = integer(value.get("schemaVersion"), 0);
                boolean extensionSchema = schemaVersion == 3;
                Set<String> allowed = extensionSchema
                                ? extensionAllowed : legacyAllowed;
                if (!allowed.containsAll(value.keySet())
                                || !Set.of(1, 2, 3).contains(schemaVersion)) {
                        throw new BusinessConflictException(
                                        "UI_INTERFACE_PINNED_SNAPSHOT_INVALID",
                                        "已发布接口快照版本或字段不受支持");
                }
                UiExtensionDefinition definition = new UiExtensionDefinition();
                if (extensionSchema
                                && !"INTERFACE".equals(normalize(
                                requiredSnapshotText(value,
                                                "extensionType")))) {
                        throw new BusinessConflictException(
                                        "UI_INTERFACE_PINNED_SNAPSHOT_INVALID",
                                        "已发布接口快照类型无效");
                }
                definition.setId(requiredSnapshotText(
                                value, extensionSchema
                                                ? "extensionId" : "id"));
                definition.setExtensionKey(requiredSnapshotText(
                                value, extensionSchema
                                                ? "extensionKey" : "sourceCode"));
                definition.setDisplayName(text(value.get(extensionSchema
                                ? "displayName" : "sourceName")));
                definition.setImplementationType(normalize(
                                requiredSnapshotText(value, extensionSchema
                                                ? "implementationType" : "sourceType")));
                if (!SOURCE_TYPES.contains(
                                definition.getImplementationType())) {
                        throw new BusinessConflictException(
                                        "UI_INTERFACE_PINNED_SNAPSHOT_INVALID",
                                        "已发布接口实现类型不受支持");
                }
                definition.setProviderCode(text(value.get("providerCode")));
                if (schemaVersion >= 2
                                && "REGISTERED_PROVIDER".equals(
                                definition.getImplementationType())) {
                        int providerVersion = integer(
                                        value.get("providerVersion"), 0);
                        String artifactDigest = normalizeDigest(text(
                                        value.get("providerArtifactDigest")));
                        if (providerVersion < 1 || artifactDigest == null) {
                                throw new BusinessConflictException(
                                                "UI_INTERFACE_PINNED_PROVIDER_IDENTITY_INVALID",
                                                "已发布接口缺少有效的 Provider 版本或制品摘要");
                        }
                        definition.setProviderVersion(providerVersion);
                        definition.setProviderArtifactDigest(artifactDigest);
                }
                definition.setScopeType(normalize(requiredSnapshotText(
                                value, "scopeType")));
                if (!SCOPE_TYPES.contains(definition.getScopeType())) {
                        throw new BusinessConflictException(
                                        "UI_INTERFACE_PINNED_SNAPSHOT_INVALID",
                                        "已发布接口作用域不受支持");
                }
                definition.setScopeId(text(value.get("scopeId")));
                definition.setRevision(integer(value.get(extensionSchema
                                ? "extensionRevision" : "revision"), 0));
                if (definition.getRevision() < 1) {
                        throw new BusinessConflictException(
                                        "UI_INTERFACE_PINNED_SNAPSHOT_INVALID",
                                        "已发布接口修订号无效");
                }
                definition.setProviderOperationCode(requiredSnapshotText(
                                value, extensionSchema
                                                ? "providerOperationCode"
                                                : "operationCode"));
                definition.setInterfaceContextType(normalize(
                                requiredSnapshotText(value, extensionSchema
                                                ? "interfaceContextType"
                                                : "operationContextType")));
                if (!CONTEXT_TYPES.contains(
                                definition.getInterfaceContextType())) {
                        throw new BusinessConflictException(
                                        "UI_INTERFACE_PINNED_SNAPSHOT_INVALID",
                                        "已发布接口上下文不受支持");
                }
                definition.setInterfaceKind(normalize(requiredSnapshotText(
                                value, extensionSchema
                                                ? "interfaceKind"
                                                : "operationKind")));
                definition.setImplementationConfigDocument(
                                requiredJsonDocument(value, extensionSchema
                                                ? "implementationConfigDocument"
                                                : "configDocument"));
                definition.setExecutionPolicyDocument(requiredJsonDocument(
                                value, "executionPolicyDocument"));
                definition.setInputSchemaDocument(
                                requiredJsonDocument(
                                        value, "inputSchemaDocument"));
                definition.setOutputSchemaDocument(
                                requiredJsonDocument(
                                        value, "outputSchemaDocument"));
                definition.setExtensionType("INTERFACE");
                definition.setEnabled(true);
                definition.setDeleted(0);
                definitionValidator.validateNoForbiddenKeys(
                                read(definition.getImplementationConfigDocument(),
                                        "已发布接口扩展配置"),
                                "config");
                definitionValidator.validateExecutionPolicy(read(
                                definition.getExecutionPolicyDocument(),
                                "已发布接口扩展策略"));
                return definition;
        }

        private String requiredSnapshotText(
                        Map<String, Object> value,
                        String key) {
                String result = text(value.get(key));
                if (!StringUtils.hasText(result)) {
                        throw new BusinessConflictException(
                                        "UI_INTERFACE_PINNED_SNAPSHOT_INVALID",
                                        "已发布接口快照缺少字段: " + key);
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
                codec.readObject(document, "已发布接口 " + key);
                return codec.canonicalize(
                                document,
                                "已发布接口 " + key);
        }

        private String canonicalDocument(String document, String label) {
                return StringUtils.hasText(document)
                                ? codec.canonicalize(document, label) : "{}";
        }

        private UiExtensionDefinition requireExecutableDefinition(String id) {
                return requireExecutableDefinition(id, null);
        }

        /**
         * 解析当前扩展 ID，或为不可变历史快照解析迁移前的 serviceId +
         * operationCode。新配置永远直接传 extensionId，不进入兼容查询。
         */
        UiExtensionDefinition requireExecutableDefinition(
                        String id,
                        String legacyOperationCode) {
                UiExtensionDefinition definition = resolveDefinitionReference(
                                id, legacyOperationCode);
                if (!Boolean.TRUE.equals(definition.getEnabled())) {
                        throw new BusinessConflictException(
                                        "UI_INTERFACE_EXTENSION_NOT_EXECUTABLE",
                                        "接口扩展已停用");
                }
                return definition;
        }

        /**
         * 解析当前 extensionId 或历史 serviceId + operationCode，允许返回已停用定义。
         *
         * <p>仅供草稿回显和保存前规范化使用；真正执行必须继续调用
         * {@link #requireExecutableDefinition(String, String)} 检查 ACTIVE 状态。</p>
         */
        UiExtensionDefinition resolveDefinitionReference(
                        String id,
                        String legacyOperationCode) {
                UiExtensionDefinition definition = StringUtils.hasText(id)
                                ? mapper.selectById(id.trim()) : null;
                if ((definition == null
                                || !"INTERFACE".equals(normalize(
                                                definition.getExtensionType())))
                                && StringUtils.hasText(id)
                                && StringUtils.hasText(legacyOperationCode)) {
                        definition = mapper.selectOne(
                                        new LambdaQueryWrapper<UiExtensionDefinition>()
                                                        .eq(UiExtensionDefinition::getExtensionType,
                                                                        "INTERFACE")
                                                        .eq(UiExtensionDefinition::getLegacyServiceId,
                                                                        id.trim())
                                                        .eq(UiExtensionDefinition::getProviderOperationCode,
                                                                        legacyOperationCode.trim())
                                                        .eq(UiExtensionDefinition::getDeleted, 0));
                }
                if (definition == null
                                || !"INTERFACE".equals(normalize(
                                                definition.getExtensionType()))
                                || Integer.valueOf(1).equals(definition.getDeleted())) {
                        throw new BusinessConflictException(
                                        "UI_INTERFACE_EXTENSION_NOT_FOUND",
                                        "接口扩展不存在或已删除");
                }
                return definition;
        }

        private Object executeAuthorized(
                        UiExtensionDefinition definition,
                        UiExtensionExecuteRequest request,
                        UiDataSourceExecutionAuthorization authorization) {
                Map<String, Object> config = read(
                                definition.getConfigDocument(), "数据源配置");
                Map<String, Object> input = request == null || request.getInput() == null
                                ? Map.of()
                                : request.getInput();
                Map<String, Object> inputSchema = read(
                                definition.getOperationInputSchemaDocument(),
                                "接口输入Schema");
                Map<String, Object> outputSchema = read(
                                definition.getOperationOutputSchemaDocument(),
                                "接口输出Schema");
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
                                authorization,
                                request);
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

        private Object executeInternal(
                        UiExtensionDefinition definition,
                        UiExtensionExecuteRequest request,
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

        /**
         * 校验一条完整接口扩展。接口上下文、读写类型和 Schema 均直接位于
         * 扩展本身，不再校验或展开操作数组。
         */
        private void validateRequest(
                        UiExtensionDefinitionSaveRequest request) {
                if (request == null
                                || !"INTERFACE".equals(normalize(
                                                request.getExtensionType()))
                                || !StringUtils.hasText(request.getExtensionKey())
                                || !StringUtils.hasText(request.getDisplayName())) {
                        throw new IllegalArgumentException(
                                        "接口扩展类型、编码和名称不能为空");
                }
                if (!EXTENSION_KEY.matcher(
                                request.getExtensionKey().trim()).matches()) {
                        throw new IllegalArgumentException(
                                        "接口扩展编码不合法，必须以字母开头且最多 255 个字符");
                }
                String sourceType = normalize(
                                request.getImplementationType());
                if (!SOURCE_TYPES.contains(sourceType)) {
                        throw new IllegalArgumentException(
                                        "不支持的接口实现类型: " + sourceType);
                }
                String scopeType = normalize(
                                StringUtils.hasText(request.getScopeType())
                                                ? request.getScopeType()
                                                : "GLOBAL");
                if (!SCOPE_TYPES.contains(scopeType)) {
                        throw new IllegalArgumentException(
                                        "不支持的接口作用域: " + scopeType);
                }
                if (!"GLOBAL".equals(scopeType) && !StringUtils.hasText(request.getScopeId())) {
                        throw new IllegalArgumentException(
                                        "非全局接口必须指定 scopeId");
                }
                if ("GLOBAL".equals(scopeType)
                                && "REGISTERED_PROVIDER".equals(sourceType)) {
                        throw new IllegalArgumentException(
                                        "Provider 必须绑定实体、表单或列表范围");
                }
                definitionValidator.validateNoForbiddenKeys(
                                request.getImplementationConfig(),
                                "implementationConfig");
                definitionValidator.validateExecutionPolicy(
                                request.getExecutionPolicy());
                definitionValidator.validateSchemaDefinition(
                                request.getInputSchema() == null
                                                ? Map.of() : request.getInputSchema(),
                                "接口输入Schema");
                definitionValidator.validateSchemaDefinition(
                                request.getOutputSchema() == null
                                                ? Map.of() : request.getOutputSchema(),
                                "接口输出Schema");
                String kind = normalize(request.getInterfaceKind());
                if (!Set.of("READ", "WRITE").contains(kind)) {
                        throw new IllegalArgumentException(
                                        "接口类型仅支持 READ/WRITE");
                }
                String contextType = normalize(
                                request.getInterfaceContextType());
                if (!CONTEXT_TYPES.contains(contextType)) {
                        throw new IllegalArgumentException(
                                        "接口必须声明 FORM/LIST/ENTITY 上下文");
                }
                validateInterfaceScope(contextType, scopeType);
                if ("REGISTERED_PROVIDER".equals(sourceType)
                                && !StringUtils.hasText(request.getProviderCode())) {
                        throw new IllegalArgumentException("Provider 编码不能为空");
                }
                String status = StringUtils.hasText(request.getStatus())
                                ? normalize(request.getStatus()) : "ACTIVE";
                if (!Set.of("ACTIVE", "DISABLED").contains(status)) {
                        throw new IllegalArgumentException("接口扩展状态不合法");
                }
                requireScopeAccess(scopeType, request.getScopeId());
        }

        private void validateInterfaceScope(
                        String contextType,
                        String scopeType) {
                boolean compatible = switch (scopeType) {
                        case "ENTITY" -> true;
                        case "FORM" -> "FORM".equals(contextType);
                        case "LIST" -> "LIST".equals(contextType);
                        case "GLOBAL" -> !"ENTITY".equals(contextType);
                        default -> false;
                };
                if (!compatible) {
                        throw new IllegalArgumentException(
                                        "接口上下文 " + contextType
                                                        + " 与作用范围 "
                                                        + scopeType + " 不兼容");
                }
        }

        private UiExtensionDefinition resolveOperationDefinition(
                        UiExtensionDefinition definition,
                        String operationCode) {
                if (StringUtils.hasText(operationCode)
                                && StringUtils.hasText(
                                                definition.getProviderOperationCode())
                                && !Objects.equals(
                                                operationCode.trim(),
                                                definition.getProviderOperationCode())) {
                        throw new IllegalArgumentException(
                                        "历史接口操作与扩展不匹配: "
                                                        + operationCode);
                }
                return definition;
        }

        private UiExtensionDefinition copyDefinition(
                        UiExtensionDefinition source) {
                UiExtensionDefinition target = new UiExtensionDefinition();
                target.setId(source.getId());
                target.setSourceCode(source.getSourceCode());
                target.setSourceName(source.getSourceName());
                target.setSourceType(source.getSourceType());
                target.setProviderCode(source.getProviderCode());
                target.setScopeType(source.getScopeType());
                target.setScopeId(source.getScopeId());
                target.setConfigDocument(source.getConfigDocument());
                target.setExecutionPolicyDocument(source.getExecutionPolicyDocument());
                target.setOperationInputSchemaDocument(
                                source.getOperationInputSchemaDocument());
                target.setOperationOutputSchemaDocument(
                                source.getOperationOutputSchemaDocument());
                target.setOperationCode(source.getOperationCode());
                target.setOperationContextType(source.getOperationContextType());
                target.setOperationKind(source.getOperationKind());
                target.setExtensionType(source.getExtensionType());
                target.setVersion(source.getVersion());
                target.setSnapshotVersion(source.getSnapshotVersion());
                target.setLegacyServiceId(source.getLegacyServiceId());
                target.setProviderVersion(source.getProviderVersion());
                target.setProviderArtifactDigest(
                                source.getProviderArtifactDigest());
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
                        UiExtensionDefinition definition,
                        Map<String, Object> input,
                        UiDataSourceExecutionAuthorization authorization,
                        UiExtensionExecuteRequest request) {
                Map<String, Object> key = new LinkedHashMap<>();
                key.put("serviceId", definition.getId());
                key.put("revision", definition.getRevision());
                key.put("providerVersion", definition.getProviderVersion());
                key.put("providerArtifactDigest",
                                definition.getProviderArtifactDigest());
                key.put("operationCode", definition.getOperationCode());
                // revision 只能说明草稿版本，钉版宿主还可能在同一服务/修订下
                // 固定不同操作或不同完整定义；独立指纹防止跨操作/跨制品串缓存。
                key.put("operationDefinitionFingerprint",
                                operationDefinitionFingerprint(definition));
                key.put("usage", authorization.usage());
                key.put("configType", authorization.configType());
                key.put("configId", authorization.configId());
                key.put("releaseId", authorization.releaseId());
                key.put("releaseVersion", authorization.releaseVersion());
                key.put("entityCode", authorization.entityCode());
                key.put("listKey", authorization.listKey());
                key.put("userId", authorization.user().getId());
                key.put("tenantId", authorization.user().getOrgId());
                // FORM_BUTTON_CLICK 的强类型 Provider 上下文还包含服务端完成鉴权后
                // 注入的表单/记录/待办坐标。它们不一定出现在 input/requestContext，
                // 必须独立参与缓存键，避免不同审批任务或记录复用上一上下文的结果。
                key.put("serverRecordId",
                                request == null ? null
                                                : request.getServerRecordId());
                key.put("serverFormMode",
                                request == null ? null
                                                : request.getServerFormMode());
                key.put("serverTaskId",
                                request == null ? null
                                                : request.getServerTaskId());
                key.put("serverProcessInstanceId",
                                request == null ? null
                                                : request.getServerProcessInstanceId());
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

        private String operationDefinitionFingerprint(
                        UiExtensionDefinition definition) {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("serviceId", definition.getId());
                value.put("sourceCode", definition.getSourceCode());
                value.put("sourceType", definition.getSourceType());
                value.put("providerCode", definition.getProviderCode());
                value.put("providerVersion", definition.getProviderVersion());
                value.put("providerArtifactDigest",
                                definition.getProviderArtifactDigest());
                value.put("scopeType", definition.getScopeType());
                value.put("scopeId", definition.getScopeId());
                value.put("revision", definition.getRevision());
                value.put("operationCode", definition.getOperationCode());
                value.put("operationContextType",
                                definition.getOperationContextType());
                value.put("operationKind", definition.getOperationKind());
                value.put("configDocument", definition.getConfigDocument());
                value.put("executionPolicyDocument",
                                definition.getExecutionPolicyDocument());
                value.put("inputSchemaDocument",
                                definition.getOperationInputSchemaDocument());
                value.put("outputSchemaDocument",
                                definition.getOperationOutputSchemaDocument());
                return sha256(codec.canonicalize(
                                codec.write(value, "数据源操作定义缓存指纹"),
                                "数据源操作定义缓存指纹"));
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
                return exception instanceof UiExtensionDefinitionValidator.ValidationException
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
                        UiExtensionExecuteRequest request) {
                if (request == null) {
                        throw new IllegalArgumentException(
                                        "接口执行请求不能为空");
                }
        }

        private void requireOperationContext(
                        UiExtensionDefinition definition,
                        String ownerType) {
                String expected = normalize(definition.getOperationContextType());
                String actual = normalize(ownerType);
                if (!Objects.equals(expected, actual)) {
                        throw new BusinessForbiddenException(
                                        "UI_INTERFACE_CONTEXT_MISMATCH",
                                        "接口上下文与绑定对象类型不一致");
                }
        }

        private void requireRevision(
                        Integer expected,
                        UiExtensionDefinition current) {
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

        /** 可存入宿主发布快照的接口扩展钉版结果。 */
        public record PublishedOperationSnapshot(
                        String extensionId,
                        String extensionKey,
                        Integer extensionRevision,
                        String providerOperationCode,
                        String document,
                        String hash) {
        }

        /** 设计态和发布态均可使用的受控动作接口描述。 */
        public record ActionOperationDescriptor(
                        String extensionId,
                        String extensionKey,
                        Integer extensionRevision,
                        String providerOperationCode,
                        String interfaceKind,
                        String implementationType,
                        String providerCode,
                        String interfaceContextType) {
        }

        private record CacheEntry(Object value, long expiresAt) {
        }

        private record ProviderIdentity(
                        int version,
                        String artifactDigest) {
        }

}
