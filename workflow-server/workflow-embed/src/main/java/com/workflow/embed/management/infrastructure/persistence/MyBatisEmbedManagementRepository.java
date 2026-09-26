package com.workflow.embed.management.infrastructure.persistence;

import com.workflow.core.database.jdbc.JdbcWriteAttempt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.form.port.EntityNewDataFormRuntimePort.ResolvedForm;
import com.workflow.contracts.entity.form.port.EntityNewDataFormRuntimePort;
import com.workflow.embed.management.domain.EmbedManagementModel.ApplicationOption;
import com.workflow.embed.management.domain.EmbedManagementModel.IdentityProviderOption;
import com.workflow.embed.management.domain.EmbedManagementModel.OptionsFilter;
import com.workflow.embed.management.domain.EmbedManagementModel.BindingFilter;
import com.workflow.embed.management.domain.EmbedManagementModel.BindingState;
import com.workflow.embed.management.domain.EmbedManagementModel.GrantState;
import com.workflow.embed.management.domain.EmbedManagementModel.JwksMode;
import com.workflow.embed.management.domain.EmbedManagementModel.Page;
import com.workflow.embed.management.domain.EmbedManagementModel.ProviderFilter;
import com.workflow.embed.management.domain.EmbedManagementModel.ProviderState;
import com.workflow.embed.management.domain.EmbedManagementModel.ProviderType;
import com.workflow.embed.management.domain.EmbedManagementModel.ReleaseState;
import com.workflow.embed.management.domain.EmbedManagementModel.ResolvedResource;
import com.workflow.embed.management.domain.EmbedManagementModel.RevisionMode;
import com.workflow.embed.management.domain.EmbedManagementModel.SecurityStatus;
import com.workflow.embed.management.domain.EmbedManagementModel.SurfaceType;
import com.workflow.embed.management.domain.EmbedManagementModel.ViewFilter;
import com.workflow.embed.management.domain.EmbedManagementModel.ViewState;
import com.workflow.embed.management.domain.EmbedManagementModel.ViewStatus;
import com.workflow.embed.management.infrastructure.persistence.ManagementPersistenceRows.BindingRow;
import com.workflow.embed.management.infrastructure.persistence.ManagementPersistenceRows.FormTargetRow;
import com.workflow.embed.management.infrastructure.persistence.ManagementPersistenceRows.GrantRow;
import com.workflow.embed.management.infrastructure.persistence.ManagementPersistenceRows.ListTargetRow;
import com.workflow.embed.management.infrastructure.persistence.ManagementPersistenceRows.ProviderRow;
import com.workflow.embed.management.infrastructure.persistence.ManagementPersistenceRows.ReleaseRow;
import com.workflow.embed.management.infrastructure.persistence.ManagementPersistenceRows.ViewRow;
import com.workflow.embed.management.application.port.EmbedManagementRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** MyBatis 驱动的 Embed 管理持久化适配器。 */
@Repository
public class MyBatisEmbedManagementRepository implements EmbedManagementRepository {

    private final EmbedManagementMapper mapper;
    private final PublishedUiResourceSnapshotParser snapshotParser;
    private final EntityNewDataFormRuntimePort newDataFormRuntimePort;
    private final JdbcWriteAttempt writeAttempt;

    /**
     * 初始化MyBatis嵌入式管理仓储，保存构造参数供后续方法使用。
     *
     * @param mapper 映射器依赖，保存到当前对象供后续业务方法调用
     * @param objectMapper 对象映射器，保存在对象中供后续校验、查询或展示
     * @param newDataFormRuntimePort 新数据表单运行时端口依赖，保存到当前对象供后续业务方法调用
     * @param writeAttempt 写入{@code attempt}依赖，保存到当前对象供后续业务方法调用
     */
    public MyBatisEmbedManagementRepository(
            EmbedManagementMapper mapper,
            ObjectMapper objectMapper,
            EntityNewDataFormRuntimePort newDataFormRuntimePort,
            JdbcWriteAttempt writeAttempt) {
        this.mapper = mapper;
        this.snapshotParser = new PublishedUiResourceSnapshotParser(objectMapper);
        this.newDataFormRuntimePort = newDataFormRuntimePort;
        this.writeAttempt = writeAttempt;
    }

    /**
     * 查询视图；查询结果供调用方展示或继续处理。
     *
     * @param filter 过滤，作为 {@code mapper.countViews} 的输入影响后续处理
     * @return 符合条件的{@code page<view}{@code state>}结果，供调用方继续处理
     */
    @Override
    public Page<ViewState> findViews(ViewFilter filter) {
        List<ViewState> records = mapper.findViews(
                filter.keyword(), name(filter.status()), name(filter.surfaceType()),
                filter.applicationId(), filter.pageSize(), offset(filter.pageNum(), filter.pageSize()))
                .stream().map(this::view).toList();
        long total = mapper.countViews(filter.keyword(), name(filter.status()),
                name(filter.surfaceType()), filter.applicationId());
        return new Page<>(records, total, filter.pageNum(), filter.pageSize());
    }

    /**
     * 查询视图；查询结果供调用方展示或继续处理。
     *
     * @param viewId 视图ID，后续用于查询视图时定位或关联目标
     * @return 符合条件的视图状态结果，供调用方继续处理
     */
    @Override
    public ViewState findView(String viewId) {
        return view(mapper.findView(viewId));
    }

    /**
     * 锁定视图；避免后续并发处理覆盖状态。
     *
     * @param viewId 视图ID，后续用于锁定视图时定位或关联目标
     * @return 锁定后的视图结果，供调用方继续处理
     */
    @Override
    public ViewState lockView(String viewId) {
        return view(mapper.lockView(viewId));
    }

    /**
     * 按键查询视图状态；结果供后续展示或处理。
     *
     * @param viewKey 视图键，后续用于授权校验、关联或幂等去重
     * @return 符合条件的视图状态结果，供调用方继续处理
     */
    @Override
    public ViewState findViewByKey(String viewKey) {
        return view(mapper.findViewByKey(viewKey));
    }

    /**
     * 插入视图；后续读取或执行将使用更新后的状态。
     *
     * @param view 视图，供本方法插入视图时使用
     */
    @Override
    public void insertView(ViewState view) {
        mapper.insertView(row(view));
    }

    /**
     * 更新草稿；后续读取或执行将使用更新后的状态。
     *
     * @param viewId 视图ID，后续用于更新草稿时定位或关联目标
     * @param expectedVersion 预期版本，供本方法更新草稿时使用
     * @param draftJson 草稿JSON，供本方法更新草稿时使用
     * @param actorId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param now 当前时间，供本方法更新草稿时使用
     * @return 更新后的草稿结果，供调用方继续处理
     */
    @Override
    public int updateDraft(String viewId, long expectedVersion, String draftJson,
                           String actorId, LocalDateTime now) {
        return mapper.updateDraft(viewId, expectedVersion, draftJson, actorId, now);
    }

    /**
     * 处理下一步发布版本修订版本，并将结果传给后续步骤。
     *
     * @param viewId 视图ID，后续用于处理下一步发布版本修订版本时定位或关联目标
     * @return 处理后的下一步发布版本修订版本结果，供调用方继续处理
     */
    @Override
    public long nextReleaseRevision(String viewId) {
        return mapper.nextReleaseRevision(viewId);
    }

    /**
     * 插入发布版本；后续读取或执行将使用更新后的状态。
     *
     * @param release 发布版本，供本方法插入发布版本时使用
     */
    @Override
    public void insertRelease(ReleaseState release) {
        mapper.insertRelease(row(release));
    }

    /**
     * 更新视图状态；后续读取或执行将使用更新后的状态。
     *
     * @param viewId 视图ID，后续用于更新视图状态时定位或关联目标
     * @param expectedVersion 预期版本，供本方法更新视图状态时使用
     * @param status 目标状态，写入记录后供流程分支或列表查询使用
     * @param actorId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param now 当前时间，供本方法更新视图状态时使用
     * @return 更新后的视图状态结果，供调用方继续处理
     */
    @Override
    public int updateViewStatus(String viewId, long expectedVersion, String status,
                                String actorId, LocalDateTime now) {
        return mapper.updateViewStatus(viewId, expectedVersion, status, actorId, now);
    }

    /**
     * 按配置哈希查询发布版本状态；结果供后续展示或处理。
     *
     * @param viewId 视图ID，后续用于查询发布版本配置哈希时定位或关联目标
     * @param configHash 配置哈希，作为 {@code release} 的输入影响后续处理
     * @return 符合条件的发布版本状态结果，供调用方继续处理
     */
    @Override
    public ReleaseState findReleaseByConfigHash(
            String viewId, String configHash) {
        return release(mapper.findReleaseByConfigHash(viewId, configHash));
    }

    /**
     * 解析已发布资源；输出作为后续校验或处理的输入。
     *
     * @param surfaceType 界面类型标识，决定后续已发布资源采用的处理分支
     * @param target 目标，作为 {@code text} 的输入影响后续处理
     * @param releasePolicy 发布版本策略，作为 {@code text} 的输入影响后续处理
     * @return 解析后的已发布资源结果，供调用方继续处理
     */
    @Override
    public ResolvedResource resolvePublishedResource(
            SurfaceType surfaceType, JsonNode target, JsonNode releasePolicy) {
        String strategy = text(releasePolicy, "strategy");
        if (!Set.of("PINNED", "FOLLOW_ACTIVE").contains(strategy)) {
            return null;
        }
        String entityCode = text(target, "entityCode");
        String listReleaseId = "PINNED".equals(strategy)
                ? text(releasePolicy, "listReleaseId") : null;
        String formReleaseId = "PINNED".equals(strategy)
                ? text(releasePolicy, "formReleaseId") : null;
        ListTargetRow list = null;
        String listKey = null;
        if (surfaceType == SurfaceType.LIST) {
            listKey = text(target, "listKey");
            if ("PINNED".equals(strategy) && !StringUtils.hasText(listReleaseId)) {
                return null;
            }
            list = mapper.findListTarget(entityCode, listKey, listReleaseId);
            if (list == null) {
                return null;
            }
        }
        String formId = firstText(target, "formId", "defaultFormId");
        FormTargetRow form = null;
        if (surfaceType == SurfaceType.LIST
                && !StringUtils.hasText(formId)
                && "FOLLOW_ACTIVE".equals(strategy)) {
            // 管理员只选实体和列表；每次 Launch 复用 Flow 原生
            // new-data 解析（默认表单，无默认时回退流程首个可达用户任务）。
            // 只将当次解析出的精确发布坐标写入 Runtime Snapshot，
            // 后续 ACTIVE 变化不影响已打开 Session。
            ResolvedForm resolvedForm =
                    newDataFormRuntimePort.resolveForNewData(entityCode)
                            .orElse(null);
            if (resolvedForm != null) {
                formId = resolvedForm.formId();
                formReleaseId = resolvedForm.releaseId();
                form = mapper.findFormTarget(
                        entityCode, formId, formReleaseId);
                if (form == null
                        || !java.util.Objects.equals(
                                form.releaseVersion(),
                                resolvedForm.releaseVersion().longValue())) {
                    return null;
                }
            }
        }
        if (surfaceType == SurfaceType.FORM || StringUtils.hasText(formId)) {
            if (!StringUtils.hasText(formId)
                    || ("PINNED".equals(strategy) && !StringUtils.hasText(formReleaseId))) {
                return null;
            }
            if (form == null) {
                form = mapper.findFormTarget(
                        entityCode, formId, formReleaseId);
            }
            if (form == null) {
                return null;
            }
        }

        return snapshotParser.parse(surfaceType, entityCode, listKey, formId,
                list, form, mapper.findFields(entityCode));
    }

    /**
     * 查询授权；查询结果供调用方展示或继续处理。
     *
     * @param viewId 视图ID，后续用于查询授权时定位或关联目标
     * @param applicationId 应用ID，后续用于查询授权时定位或关联目标
     * @return 符合条件的授权状态结果，供调用方继续处理
     */
    @Override
    public GrantState findGrant(String viewId, String applicationId) {
        return grant(mapper.findGrant(viewId, applicationId));
    }

    /**
     * 锁定授权；避免后续并发处理覆盖状态。
     *
     * @param viewId 视图ID，后续用于锁定授权时定位或关联目标
     * @param applicationId 应用ID，后续用于锁定授权时定位或关联目标
     * @return 锁定后的授权结果，供调用方继续处理
     */
    @Override
    public GrantState lockGrant(String viewId, String applicationId) {
        return grant(mapper.lockGrant(viewId, applicationId));
    }

    /**
     * 查询{@code grants}；查询结果供调用方展示或继续处理。
     *
     * @param viewId 视图ID，后续用于查询{@code grants}时定位或关联目标
     * @return 授权状态集合，供调用方遍历或展示
     */
    @Override
    public List<GrantState> findGrants(String viewId) {
        return mapper.findGrants(viewId).stream().map(this::grant).toList();
    }

    /**
     * 插入授权；后续读取或执行将使用更新后的状态。
     *
     * @param grant 授权，供本方法插入授权时使用
     */
    @Override
    public void insertGrant(GrantState grant) {
        writeAttempt.execute(() -> mapper.insertGrant(row(grant)));
    }

    /**
     * 更新授权；后续读取或执行将使用更新后的状态。
     *
     * @param grant 授权，供本方法更新授权时使用
     * @param expectedVersion 预期版本，供本方法更新授权时使用
     * @param actorId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param now 当前时间，供本方法更新授权时使用
     * @return 更新后的授权结果，供调用方继续处理
     */
    @Override
    public int updateGrant(GrantState grant, long expectedVersion,
                           String actorId, LocalDateTime now) {
        return mapper.updateGrant(row(grant), expectedVersion, actorId, now);
    }

    /**
     * 处理替换来源，并将结果传给后续步骤。
     *
     * @param grantId 授权ID，后续用于处理替换来源时定位或关联目标
     * @param origins 来源，作为 {@code mapper.insertOrigins} 的输入影响后续处理
     */
    @Override
    public void replaceOrigins(String grantId, List<String> origins) {
        mapper.deleteOrigins(grantId);
        if (!origins.isEmpty()) {
            mapper.insertOrigins(grantId, origins);
        }
    }

    /**
     * 处理变更授权状态，并将结果传给后续步骤。
     *
     * @param grantId 授权ID，后续用于处理变更授权状态时定位或关联目标
     * @param expectedVersion 预期版本，供本方法处理变更授权状态时使用
     * @param status 状态标识，决定后续变更授权状态采用的处理分支
     * @param actorId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param now 当前时间，供本方法处理变更授权状态时使用
     * @param revoked 已撤销，供本方法处理变更授权状态时使用
     * @return 处理后的变更授权状态结果，供调用方继续处理
     */
    @Override
    public int changeGrantStatus(String grantId, long expectedVersion, String status,
                                 String actorId, LocalDateTime now, boolean revoked) {
        return mapper.changeGrantStatus(
                grantId, expectedVersion, status, actorId, now, revoked);
    }

    /**
     * 判断应用存在与启用条件是否成立，供调用方选择后续分支。
     *
     * @param applicationId 应用ID，后续用于处理应用存在与启用时定位或关联目标
     * @return 应用存在与启用条件成立时为 true，否则为 false
     */
    @Override
    public boolean applicationExistsAndEnabled(String applicationId) {
        return mapper.applicationExistsAndEnabled(applicationId);
    }

    /**
     * 查询应用选项；查询结果供调用方展示或继续处理。
     *
     * @param filter 过滤，作为 {@code mapper.countApplicationOptions} 的输入影响后续处理
     * @return 符合条件的{@code page<application}{@code option>}结果，供调用方继续处理
     */
    @Override
    public Page<ApplicationOption> findApplicationOptions(OptionsFilter filter) {
                List<ApplicationOption> records = mapper.findApplicationOptions(
                        filter.keyword(), name(filter.status()), filter.pageSize(),
                        offset(filter.pageNum(), filter.pageSize()))
                .stream().map(row -> new ApplicationOption(row.id(), row.name(), row.clientId(),
                        SecurityStatus.valueOf(row.status()), row.expiresAt(),
                        row.embedLaunchReady())).toList();
        return new Page<>(records, mapper.countApplicationOptions(
                filter.keyword(), name(filter.status())), filter.pageNum(), filter.pageSize());
    }

    /**
     * 查询身份提供者选项；查询结果供调用方展示或继续处理。
     *
     * @param filter 过滤，作为 {@code mapper.countIdentityProviderOptions} 的输入影响后续处理
     * @return 符合条件的{@code page<identity}提供者{@code option>}结果，供调用方继续处理
     */
    @Override
    public Page<IdentityProviderOption> findIdentityProviderOptions(OptionsFilter filter) {
        List<IdentityProviderOption> records = mapper.findIdentityProviderOptions(
                        filter.keyword(), name(filter.status()), filter.pageSize(),
                        offset(filter.pageNum(), filter.pageSize()))
                .stream().map(row -> new IdentityProviderOption(row.id(), row.name(),
                        ProviderType.valueOf(row.type()), SecurityStatus.valueOf(row.status())))
                .toList();
        return new Page<>(records, mapper.countIdentityProviderOptions(
                filter.keyword(), name(filter.status())), filter.pageNum(), filter.pageSize());
    }

    /**
     * 查询提供者集合；查询结果供调用方展示或继续处理。
     *
     * @param filter 过滤，作为 {@code mapper.countProviders} 的输入影响后续处理
     * @return 符合条件的{@code page<provider}{@code state>}结果，供调用方继续处理
     */
    @Override
    public Page<ProviderState> findProviders(ProviderFilter filter) {
        List<ProviderState> records = mapper.findProviders(
                filter.keyword(), name(filter.status()), name(filter.type()),
                filter.pageSize(), offset(filter.pageNum(), filter.pageSize()))
                .stream().map(this::provider).toList();
        long total = mapper.countProviders(filter.keyword(), name(filter.status()),
                name(filter.type()));
        return new Page<>(records, total, filter.pageNum(), filter.pageSize());
    }

    /**
     * 查询提供者；查询结果供调用方展示或继续处理。
     *
     * @param providerId 提供者ID，后续用于查询提供者时定位或关联目标
     * @return 符合条件的提供者状态结果，供调用方继续处理
     */
    @Override
    public ProviderState findProvider(String providerId) {
        return provider(mapper.findProvider(providerId));
    }

    /**
     * 锁定提供者；避免后续并发处理覆盖状态。
     *
     * @param providerId 提供者ID，后续用于锁定提供者时定位或关联目标
     * @return 锁定后的提供者结果，供调用方继续处理
     */
    @Override
    public ProviderState lockProvider(String providerId) {
        return provider(mapper.lockProvider(providerId));
    }

    /**
     * 按签发方与命名空间查询提供者状态；结果供后续展示或处理。
     *
     * @param issuer 签发方，作为 {@code provider} 的输入影响后续处理
     * @param namespace 命名空间，作为 {@code provider} 的输入影响后续处理
     * @return 符合条件的提供者状态结果，供调用方继续处理
     */
    @Override
    public ProviderState findProviderByIssuerAndNamespace(String issuer, String namespace) {
        return provider(mapper.findProviderByIssuerAndNamespace(issuer, namespace));
    }

    /**
     * 锁定提供者签发方与命名空间；避免后续并发处理覆盖状态。
     *
     * @param issuer 签发方，作为 {@code provider} 的输入影响后续处理
     * @param namespace 命名空间，作为 {@code provider} 的输入影响后续处理
     * @return 锁定后的提供者签发方与命名空间结果，供调用方继续处理
     */
    @Override
    public ProviderState lockProviderByIssuerAndNamespace(String issuer, String namespace) {
        return provider(mapper.lockProviderByIssuerAndNamespace(issuer, namespace));
    }

    /**
     * 插入提供者；后续读取或执行将使用更新后的状态。
     *
     * @param provider 提供者，供本方法插入提供者时使用
     */
    @Override
    public void insertProvider(ProviderState provider) {
        mapper.insertProvider(row(provider));
    }

    /**
     * 更新提供者；后续读取或执行将使用更新后的状态。
     *
     * @param provider 提供者，供本方法更新提供者时使用
     * @param expectedVersion 预期版本，供本方法更新提供者时使用
     * @param actorId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param now 当前时间，供本方法更新提供者时使用
     * @return 更新后的提供者结果，供调用方继续处理
     */
    @Override
    public int updateProvider(ProviderState provider, long expectedVersion,
                              String actorId, LocalDateTime now) {
        return mapper.updateProvider(row(provider), expectedVersion, actorId, now);
    }

    /**
     * 处理变更提供者状态，并将结果传给后续步骤。
     *
     * @param providerId 提供者ID，后续用于处理变更提供者状态时定位或关联目标
     * @param expectedVersion 预期版本，供本方法处理变更提供者状态时使用
     * @param status 状态标识，决定后续变更提供者状态采用的处理分支
     * @param actorId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param now 当前时间，供本方法处理变更提供者状态时使用
     * @param revoked 已撤销，供本方法处理变更提供者状态时使用
     * @return 处理后的变更提供者状态结果，供调用方继续处理
     */
    @Override
    public int changeProviderStatus(String providerId, long expectedVersion, String status,
                                    String actorId, LocalDateTime now, boolean revoked) {
        return mapper.changeProviderStatus(
                providerId, expectedVersion, status, actorId, now, revoked);
    }

    /**
     * 处理轮换提供者键，并将结果传给后续步骤。
     *
     * @param providerId 提供者ID，后续用于处理轮换提供者键时定位或关联目标
     * @param expectedVersion 预期版本，供本方法处理轮换提供者键时使用
     * @param jwksJson JWKSJSON，供本方法处理轮换提供者键时使用
     * @param actorId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param now 当前时间，供本方法处理轮换提供者键时使用
     * @return 处理后的轮换提供者键结果，供调用方继续处理
     */
    @Override
    public int rotateProviderKey(String providerId, long expectedVersion, String jwksJson,
                                 String actorId, LocalDateTime now) {
        return mapper.rotateProviderKey(
                providerId, expectedVersion, jwksJson, actorId, now);
    }

    /**
     * 查询绑定集合；查询结果供调用方展示或继续处理。
     *
     * @param filter 过滤，作为 {@code offset} 的输入影响后续处理
     * @return 符合条件的{@code page<binding}{@code state>}结果，供调用方继续处理
     */
    @Override
    public Page<BindingState> findBindings(BindingFilter filter) {
        List<BindingState> records = mapper.findBindings(
                filter.applicationId(), filter.identityProviderId(), filter.flowUserId(),
                name(filter.status()), filter.pageSize(),
                offset(filter.pageNum(), filter.pageSize()))
                .stream().map(this::binding).toList();
        long total = mapper.countBindings(filter.applicationId(),
                filter.identityProviderId(), filter.flowUserId(), name(filter.status()));
        return new Page<>(records, total, filter.pageNum(), filter.pageSize());
    }

    /**
     * 查询绑定；查询结果供调用方展示或继续处理。
     *
     * @param bindingId 绑定ID，后续用于查询绑定时定位或关联目标
     * @return 符合条件的绑定状态结果，供调用方继续处理
     */
    @Override
    public BindingState findBinding(String bindingId) {
        return binding(mapper.findBinding(bindingId));
    }

    /**
     * 锁定绑定；避免后续并发处理覆盖状态。
     *
     * @param bindingId 绑定ID，后续用于锁定绑定时定位或关联目标
     * @return 锁定后的绑定结果，供调用方继续处理
     */
    @Override
    public BindingState lockBinding(String bindingId) {
        return binding(mapper.lockBinding(bindingId));
    }

    /**
     * 按摘要集合查询绑定状态；结果供后续展示或处理。
     *
     * @param applicationId 应用ID，后续用于查询绑定摘要集合时定位或关联目标
     * @param providerId 提供者ID，后续用于查询绑定摘要集合时定位或关联目标
     * @param digests 摘要集合，作为 {@code binding} 的输入影响后续处理
     * @return 符合条件的绑定状态结果，供调用方继续处理
     */
    @Override
    public BindingState findBindingByDigests(
            String applicationId, String providerId, List<String> digests) {
        if (digests == null || digests.isEmpty()) {
            return null;
        }
        return binding(mapper.findBindingByDigests(applicationId, providerId, digests));
    }

    /**
     * 插入绑定；后续读取或执行将使用更新后的状态。
     *
     * @param binding 绑定，供本方法插入绑定时使用
     */
    @Override
    public void insertBinding(BindingState binding) {
        mapper.insertBinding(row(binding));
    }

    /**
     * 处理变更绑定状态，并将结果传给后续步骤。
     *
     * @param bindingId 绑定ID，后续用于处理变更绑定状态时定位或关联目标
     * @param expectedVersion 预期版本，供本方法处理变更绑定状态时使用
     * @param status 状态标识，决定后续变更绑定状态采用的处理分支
     * @param actorId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param now 当前时间，供本方法处理变更绑定状态时使用
     * @param revoked 已撤销，供本方法处理变更绑定状态时使用
     * @return 处理后的变更绑定状态结果，供调用方继续处理
     */
    @Override
    public int changeBindingStatus(String bindingId, long expectedVersion, String status,
                                   String actorId, LocalDateTime now, boolean revoked) {
        return mapper.changeBindingStatus(
                bindingId, expectedVersion, status, actorId, now, revoked);
    }

    /**
     * 判断流程用户存在与启用条件是否成立，供调用方选择后续分支。
     *
     * @param flowUserId 流程用户ID，后续用于处理流程用户存在与启用时定位或关联目标
     * @return 流程用户存在与启用条件成立时为 true，否则为 false
     */
    @Override
    public boolean flowUserExistsAndEnabled(String flowUserId) {
        return mapper.flowUserExistsAndEnabled(flowUserId);
    }

    /**
     * 统计活动会话视图；结果供后续判断或展示使用。
     *
     * @param viewId 视图ID，后续用于统计活动会话视图时定位或关联目标
     * @return 符合条件的活动会话视图数量
     */
    @Override
    public long countActiveSessionsByView(String viewId) {
        return mapper.countActiveSessionsByView(viewId);
    }

    /**
     * 处理视图，并将结果传给后续步骤。
     *
     * @param row 行，作为 {@code ViewState} 的输入影响后续处理
     * @return 处理后的视图结果，供调用方继续处理
     */
    private ViewState view(ViewRow row) {
        return row == null ? null : new ViewState(
                row.id(), row.viewKey(), row.name(), row.description(),
                SurfaceType.valueOf(row.surfaceType()), ViewStatus.valueOf(row.status()),
                row.draftConfigJson(), row.draftRevision(), row.publishedReleaseId(),
                row.publishedRevision(), row.lockVersion(), row.securityVersion(),
                row.createBy(), row.createTime(), row.updateBy(), row.updateTime());
    }

    /**
     * 处理行，并将结果传给后续步骤。
     *
     * @param value 待处理行的原始输入，结果供调用方继续使用
     * @return 处理后的行结果，供调用方继续处理
     */
    private static ViewRow row(ViewState value) {
        return new ViewRow(value.id(), value.viewKey(), value.name(), value.description(),
                value.surfaceType().name(), value.status().name(), value.draftConfigJson(),
                value.draftRevision(), value.publishedReleaseId(), value.publishedRevision(),
                value.lockVersion(), value.securityVersion(), value.createBy(),
                value.createTime(), value.updateBy(), value.updateTime());
    }

    /**
     * 处理发布版本，并将结果传给后续步骤。
     *
     * @param row 行，作为 {@code ReleaseState} 的输入影响后续处理
     * @return 处理后的发布版本结果，供调用方继续处理
     */
    private ReleaseState release(ReleaseRow row) {
        return row == null ? null : new ReleaseState(
                row.id(), row.viewId(), row.revision(), SurfaceType.valueOf(row.surfaceType()),
                row.entityCode(), row.listKey(), row.defaultFormId(), row.listReleaseId(),
                row.listReleaseVersion(), row.formReleaseId(), row.formReleaseVersion(),
                row.entryModesJson(), row.capabilitiesJson(), row.fieldPolicyJson(),
                row.actionPolicyJson(), row.contextSchemaJson(), row.contextBindingsJson(),
                row.uiConfigJson(), row.configJson(), row.configHash(), row.releaseNote(),
                row.publishedBy(), row.publishedAt());
    }

    /**
     * 处理行，并将结果传给后续步骤。
     *
     * @param value 待处理行的原始输入，结果供调用方继续使用
     * @return 处理后的行结果，供调用方继续处理
     */
    private static ReleaseRow row(ReleaseState value) {
        return new ReleaseRow(value.id(), value.viewId(), value.revision(),
                value.surfaceType().name(), value.entityCode(), value.listKey(),
                value.defaultFormId(), value.listReleaseId(), value.listReleaseVersion(),
                value.formReleaseId(), value.formReleaseVersion(), value.entryModesJson(),
                value.capabilitiesJson(), value.fieldPolicyJson(), value.actionPolicyJson(),
                value.contextSchemaJson(), value.contextBindingsJson(), value.uiConfigJson(),
                value.configJson(), value.configHash(), value.releaseNote(),
                value.publishedBy(), value.publishedAt());
    }

    /**
     * 处理授权，并将结果传给后续步骤。
     *
     * @param row 行，作为 {@code GrantState} 的输入影响后续处理
     * @return 处理后的授权结果，供调用方继续处理
     */
    private GrantState grant(GrantRow row) {
        return row == null ? null : new GrantState(
                row.id(), row.applicationId(), row.viewId(), row.identityProviderId(),
                SecurityStatus.valueOf(row.status()), row.trustedSubjectAssertion(),
                RevisionMode.valueOf(row.revisionMode()), row.pinnedRevision(),
                row.capabilityCeilingJson(), row.maxActiveSessionsPerUser(),
                row.maxSessionSeconds(), row.launchLimitPerMinute(),
                row.runtimeLimitPerMinute(), row.maxConcurrency(), row.expiresAt(),
                row.lockVersion(), row.securityVersion(), mapper.findOrigins(row.id()),
                row.createBy(), row.createTime(), row.updateBy(), row.updateTime(),
                row.revokedBy(), row.revokedAt());
    }

    /**
     * 处理行，并将结果传给后续步骤。
     *
     * @param value 待处理行的原始输入，结果供调用方继续使用
     * @return 处理后的行结果，供调用方继续处理
     */
    private static GrantRow row(GrantState value) {
        return new GrantRow(value.id(), value.applicationId(), value.viewId(),
                value.identityProviderId(), value.status().name(),
                value.trustedSubjectAssertion(), value.revisionMode().name(),
                value.pinnedRevision(), value.capabilityCeilingJson(),
                value.maxActiveSessionsPerUser(), value.maxSessionSeconds(),
                value.launchLimitPerMinute(), value.runtimeLimitPerMinute(),
                value.maxConcurrency(), value.expiresAt(), value.lockVersion(),
                value.securityVersion(), value.createBy(), value.createTime(),
                value.updateBy(), value.updateTime(), value.revokedBy(), value.revokedAt());
    }

    /**
     * 处理提供者，并将结果传给后续步骤。
     *
     * @param row 行，作为 {@code ProviderState} 的输入影响后续处理
     * @return 处理后的提供者结果，供调用方继续处理
     */
    private ProviderState provider(ProviderRow row) {
        return row == null ? null : new ProviderState(
                row.id(), row.name(), ProviderType.valueOf(row.type()),
                SecurityStatus.valueOf(row.status()), row.issuer(), row.subjectNamespace(),
                row.audiencesJson(), row.algorithmsJson(),
                row.jwksMode() == null ? null : JwksMode.valueOf(row.jwksMode()),
                row.jwksJson(), row.jwksUrl(), row.clockSkewSeconds(),
                row.maxAssertionLifetimeSeconds(), row.keyVersion(), row.lockVersion(),
                row.securityVersion(), row.createBy(), row.createTime(), row.updateBy(),
                row.updateTime(), row.revokedBy(), row.revokedAt());
    }

    /**
     * 处理行，并将结果传给后续步骤。
     *
     * @param value 待处理行的原始输入，结果供调用方继续使用
     * @return 处理后的行结果，供调用方继续处理
     */
    private static ProviderRow row(ProviderState value) {
        return new ProviderRow(value.id(), value.name(), value.type().name(),
                value.status().name(), value.issuer(), value.subjectNamespace(),
                value.audiencesJson(), value.algorithmsJson(),
                name(value.jwksMode()), value.jwksJson(), value.jwksUrl(),
                value.clockSkewSeconds(), value.maxAssertionLifetimeSeconds(),
                value.keyVersion(), value.lockVersion(), value.securityVersion(),
                value.createBy(), value.createTime(), value.updateBy(), value.updateTime(),
                value.revokedBy(), value.revokedAt());
    }

    /**
     * 处理绑定，并将结果传给后续步骤。
     *
     * @param row 行，作为 {@code BindingState} 的输入影响后续处理
     * @return 处理后的绑定结果，供调用方继续处理
     */
    private BindingState binding(BindingRow row) {
        return row == null ? null : new BindingState(
                row.id(), row.applicationId(), row.identityProviderId(), row.subjectDigest(),
                row.subjectDigestKeyVersion(), row.subjectHint(), row.flowUserId(),
                row.flowUserReady(), SecurityStatus.valueOf(row.status()), row.bindingVersion(),
                row.effectiveAt(),
                row.expiresAt(), row.createBy(), row.createTime(), row.updateBy(),
                row.updateTime(), row.revokedBy(), row.revokedAt());
    }

    /**
     * 处理行，并将结果传给后续步骤。
     *
     * @param value 待处理行的原始输入，结果供调用方继续使用
     * @return 处理后的行结果，供调用方继续处理
     */
    private static BindingRow row(BindingState value) {
        return new BindingRow(value.id(), value.applicationId(), value.identityProviderId(),
                value.subjectDigest(), value.subjectDigestKeyVersion(), value.subjectHint(),
                value.flowUserId(), value.flowUserReady(), value.status().name(),
                value.bindingVersion(),
                value.effectiveAt(), value.expiresAt(), value.createBy(), value.createTime(),
                value.updateBy(), value.updateTime(), value.revokedBy(), value.revokedAt());
    }

    /**
     * 处理偏移，并将结果传给后续步骤。
     *
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @return 处理后的偏移结果，供调用方继续处理
     */
    private static int offset(int pageNum, int pageSize) {
        return Math.max(0, (pageNum - 1) * pageSize);
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param node 节点，供本方法处理文本时使用
     * @param field 字段，作为 {@code node.get} 的输入影响后续处理
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isTextual() && StringUtils.hasText(value.textValue())
                ? value.textValue().trim() : null;
    }

    /**
     * 按候选顺序取首个非空文本，供后续匹配或展示使用。
     *
     * @param node 节点，作为 {@code text} 的输入影响后续处理
     * @param first 首个，作为 {@code text} 的输入影响后续处理
     * @param second {@code second}，作为 {@code text} 的输入影响后续处理
     * @return 处理后的首个文本文本，供调用方比较或展示
     */
    private static String firstText(JsonNode node, String first, String second) {
        String value = text(node, first);
        return value == null ? text(node, second) : value;
    }

    /**
     * 生成名称文本，供后续匹配或展示。
     *
     * @param value 待处理名称的原始输入，结果供调用方继续使用
     * @return 处理后的名称文本，供调用方比较或展示
     */
    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
