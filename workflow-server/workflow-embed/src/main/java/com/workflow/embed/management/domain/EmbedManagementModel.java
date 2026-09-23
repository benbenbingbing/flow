package com.workflow.embed.management.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Embed 管理上下文使用的稳定领域类型。
 *
 * <p>这些类型不携带 MyBatis 或 Web 注解，Application Service 因而可以使用内存端口独立测试。</p>
 */
public final class EmbedManagementModel {

    /**
     * 初始化嵌入式管理模型，保存构造参数供后续方法使用。
     */
    private EmbedManagementModel() {
    }

    /**
     * 定义界面类型的可选值；调用方据此选择对应的处理分支。
     */
    public enum SurfaceType { LIST, FORM }

    /**
     * 定义视图状态的可选值；调用方据此选择对应的处理分支。
     */
    public enum ViewStatus { DRAFT, ACTIVE, DISABLED, RETIRED }

    /**
     * 定义安全状态的可选值；调用方据此选择对应的处理分支。
     */
    public enum SecurityStatus { ACTIVE, DISABLED, REVOKED }

    /**
     * 定义提供者类型的可选值；调用方据此选择对应的处理分支。
     */
    public enum ProviderType { SIGNED_JWT, TRUSTED_EXTERNAL_ID }

    /**
     * 定义JWKS模式的可选值；调用方据此选择对应的处理分支。
     */
    public enum JwksMode { STATIC_JWK_SET, REMOTE_JWKS }

    /**
     * 定义修订版本模式的可选值；调用方据此选择对应的处理分支。
     */
    public enum RevisionMode { FOLLOW_ACTIVE, PINNED }

    /**
     * 定义能力的可选值；调用方据此选择对应的处理分支。
     */
    public enum Capability {
        LIST_QUERY,
        SELECTION_RETURN,
        RECORD_VIEW,
        RECORD_CREATE,
        RECORD_UPDATE,
        ACTION_EXECUTE,
        PROCESS_START,
        RECORD_DELETE,
        BATCH_DELETE,
        EXPORT,
        FILE_UPLOAD,
        FILE_DOWNLOAD
    }

    /**
     * View 聚合；JSON 字段保持原文，避免持久化适配器污染领域层。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param viewKey 视图键，后续用于授权校验、关联或幂等去重
     * @param name 展示名称，供界面或日志识别
     * @param description 描述，保存在对象中供后续校验、查询或展示
     * @param surfaceType 界面类型标识，决定后续视图状态采用的处理分支
     * @param status 状态标识，决定后续视图状态采用的处理分支
     * @param draftConfigJson 草稿配置JSON，保存在对象中供后续校验、查询或展示
     * @param draftRevision 草稿修订版本，保存在对象中供后续校验、查询或展示
     * @param publishedReleaseId 已发布发布版本ID，后续用于处理视图状态时定位或关联目标
     * @param publishedRevision 已发布修订版本，保存在对象中供后续校验、查询或展示
     * @param lockVersion 锁定版本，保存在对象中供后续校验、查询或展示
     * @param securityVersion 安全版本，保存在对象中供后续校验、查询或展示
     * @param createBy 创建，保存在对象中供后续校验、查询或展示
     * @param createTime 创建时间，后续用于判断有效期或展示该事件的发生时间
     * @param updateBy 更新，保存在对象中供后续校验、查询或展示
     * @param updateTime 更新时间，后续用于判断有效期或展示该事件的发生时间
     */
    public record ViewState(
            String id,
            String viewKey,
            String name,
            String description,
            SurfaceType surfaceType,
            ViewStatus status,
            String draftConfigJson,
            long draftRevision,
            String publishedReleaseId,
            Long publishedRevision,
            long lockVersion,
            long securityVersion,
            String createBy,
            LocalDateTime createTime,
            String updateBy,
            LocalDateTime updateTime) {
    }

    /**
     * Launch 内部 Runtime Snapshot；记录 canonical 完整文档以及运行时高频字段。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param viewId 视图ID，后续用于处理发布版本状态时定位或关联目标
     * @param revision 修订版本，保存在对象中供后续校验、查询或展示
     * @param surfaceType 界面类型标识，决定后续发布版本状态采用的处理分支
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param defaultFormId 默认表单ID，后续用于处理发布版本状态时定位或关联目标
     * @param listReleaseId 列表发布版本ID，后续用于处理发布版本状态时定位或关联目标
     * @param listReleaseVersion 列表发布版本，保存在对象中供后续校验、查询或展示
     * @param formReleaseId 表单发布版本ID，后续用于处理发布版本状态时定位或关联目标
     * @param formReleaseVersion 表单发布版本，保存在对象中供后续校验、查询或展示
     * @param entryModesJson 入口模式集合JSON，保存在对象中供后续校验、查询或展示
     * @param capabilitiesJson 能力集合JSON，保存在对象中供后续校验、查询或展示
     * @param fieldPolicyJson 字段策略JSON，保存在对象中供后续校验、查询或展示
     * @param actionPolicyJson 动作策略JSON，保存在对象中供后续校验、查询或展示
     * @param contextSchemaJson 上下文结构JSON，保存在对象中供后续校验、查询或展示
     * @param contextBindingsJson 上下文绑定集合JSON，保存在对象中供后续校验、查询或展示
     * @param uiConfigJson 界面配置JSON，保存在对象中供后续校验、查询或展示
     * @param configJson 配置JSON，保存在对象中供后续校验、查询或展示
     * @param configHash 配置哈希，保存在对象中供后续校验、查询或展示
     * @param releaseNote 发布版本{@code note}，保存在对象中供后续校验、查询或展示
     * @param publishedBy 已发布，保存在对象中供后续校验、查询或展示
     * @param publishedAt 已发布时间，后续用于判断有效期或展示该事件的发生时间
     */
    public record ReleaseState(
            String id,
            String viewId,
            long revision,
            SurfaceType surfaceType,
            String entityCode,
            String listKey,
            String defaultFormId,
            String listReleaseId,
            Long listReleaseVersion,
            String formReleaseId,
            Long formReleaseVersion,
            String entryModesJson,
            String capabilitiesJson,
            String fieldPolicyJson,
            String actionPolicyJson,
            String contextSchemaJson,
            String contextBindingsJson,
            String uiConfigJson,
            String configJson,
            String configHash,
            String releaseNote,
            String publishedBy,
            LocalDateTime publishedAt) {
    }

    /**
     * 封装授权状态的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param applicationId 应用ID，后续用于处理授权状态时定位或关联目标
     * @param viewId 视图ID，后续用于处理授权状态时定位或关联目标
     * @param identityProviderId 身份提供者ID，后续用于处理授权状态时定位或关联目标
     * @param status 状态标识，决定后续授权状态采用的处理分支
     * @param trustedSubjectAssertion 可信主体断言，保存在对象中供后续校验、查询或展示
     * @param revisionMode 修订版本模式标识，决定后续授权状态采用的处理分支
     * @param pinnedRevision 固定修订版本，保存在对象中供后续校验、查询或展示
     * @param capabilityCeilingJson 能力{@code ceiling}JSON，保存在对象中供后续校验、查询或展示
     * @param maxActiveSessionsPerUser 最大活动会话每用户，保存在对象中供后续校验、查询或展示
     * @param maxSessionSeconds 最大会话秒数，保存在对象中供后续校验、查询或展示
     * @param launchLimitPerMinute 启动记录上限每分钟，保存在对象中供后续校验、查询或展示
     * @param runtimeLimitPerMinute 运行时上限每分钟，保存在对象中供后续校验、查询或展示
     * @param maxConcurrency 最大{@code concurrency}，保存在对象中供后续校验、查询或展示
     * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
     * @param lockVersion 锁定版本，保存在对象中供后续校验、查询或展示
     * @param securityVersion 安全版本，保存在对象中供后续校验、查询或展示
     * @param allowedOrigins 允许来源，保存在对象中供后续校验、查询或展示
     * @param createBy 创建，保存在对象中供后续校验、查询或展示
     * @param createTime 创建时间，后续用于判断有效期或展示该事件的发生时间
     * @param updateBy 更新，保存在对象中供后续校验、查询或展示
     * @param updateTime 更新时间，后续用于判断有效期或展示该事件的发生时间
     * @param revokedBy 已撤销，保存在对象中供后续校验、查询或展示
     * @param revokedAt 已撤销时间，后续用于判断有效期或展示该事件的发生时间
     */
    public record GrantState(
            String id,
            String applicationId,
            String viewId,
            String identityProviderId,
            SecurityStatus status,
            boolean trustedSubjectAssertion,
            RevisionMode revisionMode,
            Long pinnedRevision,
            String capabilityCeilingJson,
            int maxActiveSessionsPerUser,
            int maxSessionSeconds,
            int launchLimitPerMinute,
            int runtimeLimitPerMinute,
            int maxConcurrency,
            LocalDateTime expiresAt,
            long lockVersion,
            long securityVersion,
            List<String> allowedOrigins,
            String createBy,
            LocalDateTime createTime,
            String updateBy,
            LocalDateTime updateTime,
            String revokedBy,
            LocalDateTime revokedAt) {
    }

    /**
     * 封装提供者状态的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param name 展示名称，供界面或日志识别
     * @param type 类型标识，决定后续提供者状态采用的处理分支
     * @param status 状态标识，决定后续提供者状态采用的处理分支
     * @param issuer 签发方，保存在对象中供后续校验、查询或展示
     * @param subjectNamespace 主体命名空间，保存在对象中供后续校验、查询或展示
     * @param audiencesJson {@code audiences}JSON，保存在对象中供后续校验、查询或展示
     * @param algorithmsJson {@code algorithms}JSON，保存在对象中供后续校验、查询或展示
     * @param jwksMode JWKS模式标识，决定后续提供者状态采用的处理分支
     * @param jwksJson JWKSJSON，保存在对象中供后续校验、查询或展示
     * @param jwksUrl JWKSURL，保存在对象中供后续校验、查询或展示
     * @param clockSkewSeconds 时钟{@code skew}秒数，保存在对象中供后续校验、查询或展示
     * @param maxAssertionLifetimeSeconds 最大断言{@code lifetime}秒数，保存在对象中供后续校验、查询或展示
     * @param keyVersion 键版本，保存在对象中供后续校验、查询或展示
     * @param lockVersion 锁定版本，保存在对象中供后续校验、查询或展示
     * @param securityVersion 安全版本，保存在对象中供后续校验、查询或展示
     * @param createBy 创建，保存在对象中供后续校验、查询或展示
     * @param createTime 创建时间，后续用于判断有效期或展示该事件的发生时间
     * @param updateBy 更新，保存在对象中供后续校验、查询或展示
     * @param updateTime 更新时间，后续用于判断有效期或展示该事件的发生时间
     * @param revokedBy 已撤销，保存在对象中供后续校验、查询或展示
     * @param revokedAt 已撤销时间，后续用于判断有效期或展示该事件的发生时间
     */
    public record ProviderState(
            String id,
            String name,
            ProviderType type,
            SecurityStatus status,
            String issuer,
            String subjectNamespace,
            String audiencesJson,
            String algorithmsJson,
            JwksMode jwksMode,
            String jwksJson,
            String jwksUrl,
            int clockSkewSeconds,
            int maxAssertionLifetimeSeconds,
            long keyVersion,
            long lockVersion,
            long securityVersion,
            String createBy,
            LocalDateTime createTime,
            String updateBy,
            LocalDateTime updateTime,
            String revokedBy,
            LocalDateTime revokedAt) {
    }

    /**
     * 封装绑定状态的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param applicationId 应用ID，后续用于处理绑定状态时定位或关联目标
     * @param identityProviderId 身份提供者ID，后续用于处理绑定状态时定位或关联目标
     * @param subjectDigest 主体摘要，保存在对象中供后续校验、查询或展示
     * @param subjectDigestKeyVersion 主体摘要键版本，保存在对象中供后续校验、查询或展示
     * @param subjectHint 主体{@code hint}，保存在对象中供后续校验、查询或展示
     * @param flowUserId 流程用户ID，后续用于处理绑定状态时定位或关联目标
     * @param flowUserReady 流程用户就绪，保存在对象中供后续校验、查询或展示
     * @param status 状态标识，决定后续绑定状态采用的处理分支
     * @param bindingVersion 绑定版本，保存在对象中供后续校验、查询或展示
     * @param effectiveAt 有效时间，后续用于判断有效期或展示该事件的发生时间
     * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
     * @param createBy 创建，保存在对象中供后续校验、查询或展示
     * @param createTime 创建时间，后续用于判断有效期或展示该事件的发生时间
     * @param updateBy 更新，保存在对象中供后续校验、查询或展示
     * @param updateTime 更新时间，后续用于判断有效期或展示该事件的发生时间
     * @param revokedBy 已撤销，保存在对象中供后续校验、查询或展示
     * @param revokedAt 已撤销时间，后续用于判断有效期或展示该事件的发生时间
     */
    public record BindingState(
            String id,
            String applicationId,
            String identityProviderId,
            String subjectDigest,
            String subjectDigestKeyVersion,
            String subjectHint,
            String flowUserId,
            boolean flowUserReady,
            SecurityStatus status,
            long bindingVersion,
            LocalDateTime effectiveAt,
            LocalDateTime expiresAt,
            String createBy,
            LocalDateTime createTime,
            String updateBy,
            LocalDateTime updateTime,
            String revokedBy,
            LocalDateTime revokedAt) {
    }

    /**
     * 发布校验解析出的不可变 UI 资源和字段能力。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param defaultFormId 默认表单ID，后续用于处理已解析资源时定位或关联目标
     * @param listReleaseId 列表发布版本ID，后续用于处理已解析资源时定位或关联目标
     * @param listReleaseVersion 列表发布版本，保存在对象中供后续校验、查询或展示
     * @param formReleaseId 表单发布版本ID，后续用于处理已解析资源时定位或关联目标
     * @param formReleaseVersion 表单发布版本，保存在对象中供后续校验、查询或展示
     * @param fields 字段集合，后续逐项校验、转换或持久化
     * @param queryableFields {@code queryable}字段，保存在对象中供后续校验、查询或展示
     * @param writableFields {@code writable}字段，保存在对象中供后续校验、查询或展示
     * @param sensitiveFields {@code sensitive}字段，保存在对象中供后续校验、查询或展示
     * @param actionKeys 动作键集合，保存在对象中供后续校验、查询或展示
     * @param trustedComponentsOnly 可信{@code components}仅，保存在对象中供后续校验、查询或展示
     */
    public record ResolvedResource(
            String entityCode,
            String listKey,
            String defaultFormId,
            String listReleaseId,
            Long listReleaseVersion,
            String formReleaseId,
            Long formReleaseVersion,
            List<String> fields,
            List<String> queryableFields,
            List<String> writableFields,
            List<String> sensitiveFields,
            List<String> actionKeys,
            boolean trustedComponentsOnly) {
    }

    /**
     * 封装冲突的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param path 路径，保存在对象中供后续校验、查询或展示
     * @param code 业务编码，供后续匹配和引用
     * @param message 消息，保存在对象中供后续校验、查询或展示
     */
    public record Violation(String path, String code, String message) {
    }

    /**
     * 封装校验的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param valid 有效，后续用于处理校验结果时定位或关联目标
     * @param resolved 已解析，保存在对象中供后续校验、查询或展示
     * @param violations 违规项，保存在对象中供后续校验、查询或展示
     * @param warnings {@code warnings}，保存在对象中供后续校验、查询或展示
     * @param canonicalConfig 规范配置内容，决定后续校验结果的处理规则
     * @param configHash 配置哈希，保存在对象中供后续校验、查询或展示
     */
    public record ValidationResult(
            boolean valid,
            ResolvedResource resolved,
            List<Violation> violations,
            List<String> warnings,
            String canonicalConfig,
            String configHash) {
    }

    /**
     * 封装分页的不可变数据；各分量供后续校验、传递或结果展示使用。
     */
    public record Page<T>(List<T> records, long total, int pageNum, int pageSize) {
    }

    /**
     * 嵌入管理名称选择所需的应用投影；就绪态不包含凭据内容或授权明细。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param name 展示名称，供界面或日志识别
     * @param clientId 客户端ID，后续用于处理应用选项时定位或关联目标
     * @param status 状态标识，决定后续应用选项采用的处理分支
     * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
     * @param embedLaunchReady 嵌入式启动记录就绪，保存在对象中供后续校验、查询或展示
     */
    public record ApplicationOption(
            String id, String name, String clientId, SecurityStatus status,
            LocalDateTime expiresAt, boolean embedLaunchReady) {
    }

    /**
     * 普通嵌入管理权限可见的身份源投影，不包含身份验证配置。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param name 展示名称，供界面或日志识别
     * @param type 类型标识，决定后续身份提供者选项采用的处理分支
     * @param status 状态标识，决定后续身份提供者选项采用的处理分支
     */
    public record IdentityProviderOption(
            String id, String name, ProviderType type, SecurityStatus status) {
    }

    /**
     * 封装选项过滤的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param keyword 关键字，保存在对象中供后续校验、查询或展示
     * @param status 状态标识，决定后续选项过滤采用的处理分支
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     */
    public record OptionsFilter(
            String keyword, SecurityStatus status, int pageNum, int pageSize) {
    }

    /**
     * 封装视图过滤的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param keyword 关键字，保存在对象中供后续校验、查询或展示
     * @param status 状态标识，决定后续视图过滤采用的处理分支
     * @param surfaceType 界面类型标识，决定后续视图过滤采用的处理分支
     * @param applicationId 应用ID，后续用于处理视图过滤时定位或关联目标
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     */
    public record ViewFilter(
            String keyword,
            ViewStatus status,
            SurfaceType surfaceType,
            String applicationId,
            int pageNum,
            int pageSize) {
    }

    /**
     * 封装提供者过滤的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param keyword 关键字，保存在对象中供后续校验、查询或展示
     * @param status 状态标识，决定后续提供者过滤采用的处理分支
     * @param type 类型标识，决定后续提供者过滤采用的处理分支
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     */
    public record ProviderFilter(
            String keyword,
            SecurityStatus status,
            ProviderType type,
            int pageNum,
            int pageSize) {
    }

    /**
     * 封装绑定过滤的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param applicationId 应用ID，后续用于处理绑定过滤时定位或关联目标
     * @param identityProviderId 身份提供者ID，后续用于处理绑定过滤时定位或关联目标
     * @param flowUserId 流程用户ID，后续用于处理绑定过滤时定位或关联目标
     * @param status 状态标识，决定后续绑定过滤采用的处理分支
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     */
    public record BindingFilter(
            String applicationId,
            String identityProviderId,
            String flowUserId,
            SecurityStatus status,
            int pageNum,
            int pageSize) {
    }

    /**
     * 封装创建视图的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param viewKey 视图键，后续用于授权校验、关联或幂等去重
     * @param name 展示名称，供界面或日志识别
     * @param surfaceType 界面类型标识，决定后续创建视图命令采用的处理分支
     * @param description 描述，保存在对象中供后续校验、查询或展示
     */
    public record CreateViewCommand(
            String viewKey,
            String name,
            SurfaceType surfaceType,
            String description) {
    }

    /**
     * 封装更新草稿的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param expectedVersion 预期版本，保存在对象中供后续校验、查询或展示
     * @param draft 草稿，保存在对象中供后续校验、查询或展示
     */
    public record UpdateDraftCommand(long expectedVersion, JsonNode draft) {
    }

    /**
     * 封装变更状态的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param expectedVersion 预期版本，保存在对象中供后续校验、查询或展示
     * @param status 状态标识，决定后续变更状态命令采用的处理分支
     * @param reason 原因，保存在对象中供后续校验、查询或展示
     */
    public record ChangeStatusCommand(long expectedVersion, String status, String reason) {
    }

    /**
     * 封装新增或更新授权的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param expectedVersion 预期版本，保存在对象中供后续校验、查询或展示
     * @param status 状态标识，决定后续新增或更新授权命令采用的处理分支
     * @param identityProviderId 身份提供者ID，后续用于处理新增或更新授权命令时定位或关联目标
     * @param trustedSubjectAssertion 可信主体断言，保存在对象中供后续校验、查询或展示
     * @param allowedOrigins 允许来源，保存在对象中供后续校验、查询或展示
     * @param capabilityCeiling 能力{@code ceiling}，保存在对象中供后续校验、查询或展示
     * @param maxActiveSessionsPerUser 最大活动会话每用户，保存在对象中供后续校验、查询或展示
     * @param maxSessionSeconds 最大会话秒数，保存在对象中供后续校验、查询或展示
     * @param launchLimitPerMinute 启动记录上限每分钟，保存在对象中供后续校验、查询或展示
     * @param runtimeLimitPerMinute 运行时上限每分钟，保存在对象中供后续校验、查询或展示
     * @param maxConcurrency 最大{@code concurrency}，保存在对象中供后续校验、查询或展示
     * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
     */
    public record UpsertGrantCommand(
            Long expectedVersion,
            SecurityStatus status,
            String identityProviderId,
            boolean trustedSubjectAssertion,
            List<String> allowedOrigins,
            List<Capability> capabilityCeiling,
            int maxActiveSessionsPerUser,
            int maxSessionSeconds,
            int launchLimitPerMinute,
            int runtimeLimitPerMinute,
            int maxConcurrency,
            LocalDateTime expiresAt) {
    }

    /**
     * 封装创建提供者的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param name 展示名称，供界面或日志识别
     * @param type 类型标识，决定后续创建提供者命令采用的处理分支
     * @param issuer 签发方，保存在对象中供后续校验、查询或展示
     * @param audiences {@code audiences}，保存在对象中供后续校验、查询或展示
     * @param subjectNamespace 主体命名空间，保存在对象中供后续校验、查询或展示
     * @param algorithms {@code algorithms}，保存在对象中供后续校验、查询或展示
     * @param jwksMode JWKS模式标识，决定后续创建提供者命令采用的处理分支
     * @param jwks JWKS，保存在对象中供后续校验、查询或展示
     * @param jwksUrl JWKSURL，保存在对象中供后续校验、查询或展示
     * @param clockSkewSeconds 时钟{@code skew}秒数，保存在对象中供后续校验、查询或展示
     * @param maxAssertionLifetimeSeconds 最大断言{@code lifetime}秒数，保存在对象中供后续校验、查询或展示
     */
    public record CreateProviderCommand(
            String name,
            ProviderType type,
            String issuer,
            List<String> audiences,
            String subjectNamespace,
            List<String> algorithms,
            JwksMode jwksMode,
            JsonNode jwks,
            String jwksUrl,
            int clockSkewSeconds,
            int maxAssertionLifetimeSeconds) {
    }

    /**
     * 封装更新提供者的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param expectedVersion 预期版本，保存在对象中供后续校验、查询或展示
     * @param name 展示名称，供界面或日志识别
     * @param issuer 签发方，保存在对象中供后续校验、查询或展示
     * @param audiences {@code audiences}，保存在对象中供后续校验、查询或展示
     * @param subjectNamespace 主体命名空间，保存在对象中供后续校验、查询或展示
     * @param algorithms {@code algorithms}，保存在对象中供后续校验、查询或展示
     * @param jwksMode JWKS模式标识，决定后续更新提供者命令采用的处理分支
     * @param jwks JWKS，保存在对象中供后续校验、查询或展示
     * @param jwksUrl JWKSURL，保存在对象中供后续校验、查询或展示
     * @param clockSkewSeconds 时钟{@code skew}秒数，保存在对象中供后续校验、查询或展示
     * @param maxAssertionLifetimeSeconds 最大断言{@code lifetime}秒数，保存在对象中供后续校验、查询或展示
     */
    public record UpdateProviderCommand(
            long expectedVersion,
            String name,
            String issuer,
            List<String> audiences,
            String subjectNamespace,
            List<String> algorithms,
            JwksMode jwksMode,
            JsonNode jwks,
            String jwksUrl,
            Integer clockSkewSeconds,
            Integer maxAssertionLifetimeSeconds) {
    }

    /**
     * 封装创建绑定的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param applicationId 应用ID，后续用于处理创建绑定命令时定位或关联目标
     * @param identityProviderId 身份提供者ID，后续用于处理创建绑定命令时定位或关联目标
     * @param externalSubject 外部主体，保存在对象中供后续校验、查询或展示
     * @param flowUserId 流程用户ID，后续用于处理创建绑定命令时定位或关联目标
     * @param effectiveAt 有效时间，后续用于判断有效期或展示该事件的发生时间
     * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
     * @param remark {@code remark}，保存在对象中供后续校验、查询或展示
     */
    public record CreateBindingCommand(
            String applicationId,
            String identityProviderId,
            String externalSubject,
            String flowUserId,
            LocalDateTime effectiveAt,
            LocalDateTime expiresAt,
            String remark) {
    }
}
