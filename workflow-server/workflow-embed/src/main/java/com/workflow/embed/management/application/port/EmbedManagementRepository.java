package com.workflow.embed.management.application.port;

import com.workflow.embed.management.domain.EmbedManagementModel.ApplicationOption;
import com.workflow.embed.management.domain.EmbedManagementModel.IdentityProviderOption;
import com.workflow.embed.management.domain.EmbedManagementModel.OptionsFilter;
import com.workflow.embed.management.domain.EmbedManagementModel.BindingFilter;
import com.workflow.embed.management.domain.EmbedManagementModel.BindingState;
import com.workflow.embed.management.domain.EmbedManagementModel.GrantState;
import com.workflow.embed.management.domain.EmbedManagementModel.Page;
import com.workflow.embed.management.domain.EmbedManagementModel.ProviderFilter;
import com.workflow.embed.management.domain.EmbedManagementModel.ProviderState;
import com.workflow.embed.management.domain.EmbedManagementModel.ReleaseState;
import com.workflow.embed.management.domain.EmbedManagementModel.ResolvedResource;
import com.workflow.embed.management.domain.EmbedManagementModel.SurfaceType;
import com.workflow.embed.management.domain.EmbedManagementModel.ViewFilter;
import com.workflow.embed.management.domain.EmbedManagementModel.ViewState;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.List;

/** Embed 管理用例访问持久化和已发布资源目录的端口。 */
public interface EmbedManagementRepository {

    /**
     * 查询视图；查询结果供调用方展示或继续处理。
     *
     * @param filter 过滤，供本方法查询视图时使用
     * @return 符合条件的{@code page<view}{@code state>}结果，供调用方继续处理
     */
    Page<ViewState> findViews(ViewFilter filter);

    /**
     * 查询视图；查询结果供调用方展示或继续处理。
     *
     * @param viewId 视图ID，后续用于查询视图时定位或关联目标
     * @return 符合条件的视图状态结果，供调用方继续处理
     */
    ViewState findView(String viewId);

    /**
     * 锁定视图；避免后续并发处理覆盖状态。
     *
     * @param viewId 视图ID，后续用于锁定视图时定位或关联目标
     * @return 锁定后的视图结果，供调用方继续处理
     */
    ViewState lockView(String viewId);

    /**
     * 按键查询视图状态；结果供后续展示或处理。
     *
     * @param viewKey 视图键，后续用于授权校验、关联或幂等去重
     * @return 符合条件的视图状态结果，供调用方继续处理
     */
    ViewState findViewByKey(String viewKey);

    /**
     * 插入视图；后续读取或执行将使用更新后的状态。
     *
     * @param view 视图，供本方法插入视图时使用
     */
    void insertView(ViewState view);

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
    int updateDraft(String viewId, long expectedVersion, String draftJson,
                    String actorId, LocalDateTime now);

    /**
     * 处理下一步发布版本修订版本，并将结果传给后续步骤。
     *
     * @param viewId 视图ID，后续用于处理下一步发布版本修订版本时定位或关联目标
     * @return 处理后的下一步发布版本修订版本结果，供调用方继续处理
     */
    long nextReleaseRevision(String viewId);

    /**
     * 插入发布版本；后续读取或执行将使用更新后的状态。
     *
     * @param release 发布版本，供本方法插入发布版本时使用
     */
    void insertRelease(ReleaseState release);

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
    int updateViewStatus(String viewId, long expectedVersion, String status,
                         String actorId, LocalDateTime now);

    /**
     * View 行锁内按完整 canonical hash 复用相同的内部 Runtime Snapshot。
     *
     * @param viewId 视图ID，后续用于查询发布版本配置哈希时定位或关联目标
     * @param configHash 配置哈希，供本方法查询发布版本配置哈希时使用
     * @return 符合条件的发布版本状态结果，供调用方继续处理
     */
    ReleaseState findReleaseByConfigHash(String viewId, String configHash);

    /**
     * 解析已发布资源；输出作为后续校验或处理的输入。
     *
     * @param surfaceType 界面类型标识，决定后续已发布资源采用的处理分支
     * @param target 目标，供本方法解析已发布资源时使用
     * @param releasePolicy 发布版本策略，供本方法解析已发布资源时使用
     * @return 解析后的已发布资源结果，供调用方继续处理
     */
    ResolvedResource resolvePublishedResource(
            SurfaceType surfaceType, JsonNode target, JsonNode releasePolicy);

    /**
     * 查询授权；查询结果供调用方展示或继续处理。
     *
     * @param viewId 视图ID，后续用于查询授权时定位或关联目标
     * @param applicationId 应用ID，后续用于查询授权时定位或关联目标
     * @return 符合条件的授权状态结果，供调用方继续处理
     */
    GrantState findGrant(String viewId, String applicationId);

    /**
     * 锁定授权；避免后续并发处理覆盖状态。
     *
     * @param viewId 视图ID，后续用于锁定授权时定位或关联目标
     * @param applicationId 应用ID，后续用于锁定授权时定位或关联目标
     * @return 锁定后的授权结果，供调用方继续处理
     */
    GrantState lockGrant(String viewId, String applicationId);

    /**
     * 查询{@code grants}；查询结果供调用方展示或继续处理。
     *
     * @param viewId 视图ID，后续用于查询{@code grants}时定位或关联目标
     * @return 授权状态集合，供调用方遍历或展示
     */
    List<GrantState> findGrants(String viewId);

    /**
     * 插入授权；后续读取或执行将使用更新后的状态。
     *
     * @param grant 授权，供本方法插入授权时使用
     */
    void insertGrant(GrantState grant);

    /**
     * 更新授权；后续读取或执行将使用更新后的状态。
     *
     * @param grant 授权，供本方法更新授权时使用
     * @param expectedVersion 预期版本，供本方法更新授权时使用
     * @param actorId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param now 当前时间，供本方法更新授权时使用
     * @return 更新后的授权结果，供调用方继续处理
     */
    int updateGrant(GrantState grant, long expectedVersion,
                    String actorId, LocalDateTime now);

    /**
     * 处理替换来源，并将结果传给后续步骤。
     *
     * @param grantId 授权ID，后续用于处理替换来源时定位或关联目标
     * @param origins 来源，供本方法处理替换来源时使用
     */
    void replaceOrigins(String grantId, List<String> origins);

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
    int changeGrantStatus(String grantId, long expectedVersion, String status,
                          String actorId, LocalDateTime now, boolean revoked);

    /**
     * 判断应用存在与启用条件是否成立，供调用方选择后续分支。
     *
     * @param applicationId 应用ID，后续用于处理应用存在与启用时定位或关联目标
     * @return 应用存在与启用条件成立时为 true，否则为 false
     */
    boolean applicationExistsAndEnabled(String applicationId);

    /**
     * 按 ID、名称或 clientId 搜索最小应用选项；不读取凭据和授权配置。
     *
     * @param filter 过滤，供本方法查询应用选项时使用
     * @return 符合条件的{@code page<application}{@code option>}结果，供调用方继续处理
     */
    Page<ApplicationOption> findApplicationOptions(OptionsFilter filter);

    /**
     * 按 ID 或名称搜索最小身份源选项；不读取 issuer、JWKS 等验证配置。
     *
     * @param filter 过滤，供本方法查询身份提供者选项时使用
     * @return 符合条件的{@code page<identity}提供者{@code option>}结果，供调用方继续处理
     */
    Page<IdentityProviderOption> findIdentityProviderOptions(OptionsFilter filter);

    /**
     * 查询提供者集合；查询结果供调用方展示或继续处理。
     *
     * @param filter 过滤，供本方法查询提供者集合时使用
     * @return 符合条件的{@code page<provider}{@code state>}结果，供调用方继续处理
     */
    Page<ProviderState> findProviders(ProviderFilter filter);

    /**
     * 查询提供者；查询结果供调用方展示或继续处理。
     *
     * @param providerId 提供者ID，后续用于查询提供者时定位或关联目标
     * @return 符合条件的提供者状态结果，供调用方继续处理
     */
    ProviderState findProvider(String providerId);

    /**
     * 锁定提供者；避免后续并发处理覆盖状态。
     *
     * @param providerId 提供者ID，后续用于锁定提供者时定位或关联目标
     * @return 锁定后的提供者结果，供调用方继续处理
     */
    ProviderState lockProvider(String providerId);

    /**
     * 按签发方与命名空间查询提供者状态；结果供后续展示或处理。
     *
     * @param issuer 签发方，供本方法查询提供者签发方与命名空间时使用
     * @param namespace 命名空间，供本方法查询提供者签发方与命名空间时使用
     * @return 符合条件的提供者状态结果，供调用方继续处理
     */
    ProviderState findProviderByIssuerAndNamespace(String issuer, String namespace);

    /**
     * 锁定提供者签发方与命名空间；避免后续并发处理覆盖状态。
     *
     * @param issuer 签发方，供本方法锁定提供者签发方与命名空间时使用
     * @param namespace 命名空间，供本方法锁定提供者签发方与命名空间时使用
     * @return 锁定后的提供者签发方与命名空间结果，供调用方继续处理
     */
    ProviderState lockProviderByIssuerAndNamespace(String issuer, String namespace);

    /**
     * 插入提供者；后续读取或执行将使用更新后的状态。
     *
     * @param provider 提供者，供本方法插入提供者时使用
     */
    void insertProvider(ProviderState provider);

    /**
     * 更新提供者；后续读取或执行将使用更新后的状态。
     *
     * @param provider 提供者，供本方法更新提供者时使用
     * @param expectedVersion 预期版本，供本方法更新提供者时使用
     * @param actorId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param now 当前时间，供本方法更新提供者时使用
     * @return 更新后的提供者结果，供调用方继续处理
     */
    int updateProvider(ProviderState provider, long expectedVersion,
                       String actorId, LocalDateTime now);

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
    int changeProviderStatus(String providerId, long expectedVersion, String status,
                             String actorId, LocalDateTime now, boolean revoked);

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
    int rotateProviderKey(String providerId, long expectedVersion, String jwksJson,
                          String actorId, LocalDateTime now);

    /**
     * 查询绑定集合；查询结果供调用方展示或继续处理。
     *
     * @param filter 过滤，供本方法查询绑定集合时使用
     * @return 符合条件的{@code page<binding}{@code state>}结果，供调用方继续处理
     */
    Page<BindingState> findBindings(BindingFilter filter);

    /**
     * 查询绑定；查询结果供调用方展示或继续处理。
     *
     * @param bindingId 绑定ID，后续用于查询绑定时定位或关联目标
     * @return 符合条件的绑定状态结果，供调用方继续处理
     */
    BindingState findBinding(String bindingId);

    /**
     * 锁定绑定；避免后续并发处理覆盖状态。
     *
     * @param bindingId 绑定ID，后续用于锁定绑定时定位或关联目标
     * @return 锁定后的绑定结果，供调用方继续处理
     */
    BindingState lockBinding(String bindingId);

    /**
     * 按摘要集合查询绑定状态；结果供后续展示或处理。
     *
     * @param applicationId 应用ID，后续用于查询绑定摘要集合时定位或关联目标
     * @param providerId 提供者ID，后续用于查询绑定摘要集合时定位或关联目标
     * @param digests 摘要集合，供本方法查询绑定摘要集合时使用
     * @return 符合条件的绑定状态结果，供调用方继续处理
     */
    BindingState findBindingByDigests(
            String applicationId, String providerId, List<String> digests);

    /**
     * 插入绑定；后续读取或执行将使用更新后的状态。
     *
     * @param binding 绑定，供本方法插入绑定时使用
     */
    void insertBinding(BindingState binding);

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
    int changeBindingStatus(String bindingId, long expectedVersion, String status,
                            String actorId, LocalDateTime now, boolean revoked);

    /**
     * 判断流程用户存在与启用条件是否成立，供调用方选择后续分支。
     *
     * @param flowUserId 流程用户ID，后续用于处理流程用户存在与启用时定位或关联目标
     * @return 流程用户存在与启用条件成立时为 true，否则为 false
     */
    boolean flowUserExistsAndEnabled(String flowUserId);

    /**
     * View 安全版本变化后受影响的活动会话数，仅用于管理响应与审计。
     *
     * @param viewId 视图ID，后续用于统计活动会话视图时定位或关联目标
     * @return 符合条件的活动会话视图数量
     */
    long countActiveSessionsByView(String viewId);
}
