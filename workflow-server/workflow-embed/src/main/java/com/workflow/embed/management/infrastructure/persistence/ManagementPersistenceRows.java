package com.workflow.embed.management.infrastructure.persistence;

import java.time.LocalDateTime;

/** MyBatis 行模型，仅由管理持久化适配器使用。 */
final class ManagementPersistenceRows {

    /**
     * 初始化管理持久化行，保存构造参数供后续方法使用。
     */
    private ManagementPersistenceRows() {
    }

    /**
     * 封装应用选项行的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param name 展示名称，供界面或日志识别
     * @param clientId 客户端ID，后续用于处理应用选项行时定位或关联目标
     * @param status 状态标识，决定后续应用选项行采用的处理分支
     * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
     * @param embedLaunchReady 嵌入式启动记录就绪，保存在对象中供后续校验、查询或展示
     */
    record ApplicationOptionRow(
            String id, String name, String clientId, String status,
            LocalDateTime expiresAt, boolean embedLaunchReady) {
    }

    /**
     * 封装身份提供者选项行的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param name 展示名称，供界面或日志识别
     * @param type 类型标识，决定后续身份提供者选项行采用的处理分支
     * @param status 状态标识，决定后续身份提供者选项行采用的处理分支
     */
    record IdentityProviderOptionRow(String id, String name, String type, String status) {
    }

    /**
     * 封装视图行的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param viewKey 视图键，后续用于授权校验、关联或幂等去重
     * @param name 展示名称，供界面或日志识别
     * @param description 描述，保存在对象中供后续校验、查询或展示
     * @param surfaceType 界面类型标识，决定后续视图行采用的处理分支
     * @param status 状态标识，决定后续视图行采用的处理分支
     * @param draftConfigJson 草稿配置JSON，保存在对象中供后续校验、查询或展示
     * @param draftRevision 草稿修订版本，保存在对象中供后续校验、查询或展示
     * @param publishedReleaseId 已发布发布版本ID，后续用于处理视图行时定位或关联目标
     * @param publishedRevision 已发布修订版本，保存在对象中供后续校验、查询或展示
     * @param lockVersion 锁定版本，保存在对象中供后续校验、查询或展示
     * @param securityVersion 安全版本，保存在对象中供后续校验、查询或展示
     * @param createBy 创建，保存在对象中供后续校验、查询或展示
     * @param createTime 创建时间，后续用于判断有效期或展示该事件的发生时间
     * @param updateBy 更新，保存在对象中供后续校验、查询或展示
     * @param updateTime 更新时间，后续用于判断有效期或展示该事件的发生时间
     */
    record ViewRow(
            String id, String viewKey, String name, String description,
            String surfaceType, String status, String draftConfigJson,
            long draftRevision, String publishedReleaseId, Long publishedRevision,
            long lockVersion, long securityVersion, String createBy,
            LocalDateTime createTime, String updateBy, LocalDateTime updateTime) {
    }

    /**
     * 封装发布版本行的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param viewId 视图ID，后续用于处理发布版本行时定位或关联目标
     * @param revision 修订版本，保存在对象中供后续校验、查询或展示
     * @param surfaceType 界面类型标识，决定后续发布版本行采用的处理分支
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param defaultFormId 默认表单ID，后续用于处理发布版本行时定位或关联目标
     * @param listReleaseId 列表发布版本ID，后续用于处理发布版本行时定位或关联目标
     * @param listReleaseVersion 列表发布版本，保存在对象中供后续校验、查询或展示
     * @param formReleaseId 表单发布版本ID，后续用于处理发布版本行时定位或关联目标
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
    record ReleaseRow(
            String id, String viewId, long revision, String surfaceType,
            String entityCode, String listKey, String defaultFormId,
            String listReleaseId, Long listReleaseVersion,
            String formReleaseId, Long formReleaseVersion,
            String entryModesJson, String capabilitiesJson, String fieldPolicyJson,
            String actionPolicyJson, String contextSchemaJson,
            String contextBindingsJson, String uiConfigJson, String configJson,
            String configHash, String releaseNote, String publishedBy,
            LocalDateTime publishedAt) {
    }

    /**
     * 封装授权行的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param applicationId 应用ID，后续用于处理授权行时定位或关联目标
     * @param viewId 视图ID，后续用于处理授权行时定位或关联目标
     * @param identityProviderId 身份提供者ID，后续用于处理授权行时定位或关联目标
     * @param status 状态标识，决定后续授权行采用的处理分支
     * @param trustedSubjectAssertion 可信主体断言，保存在对象中供后续校验、查询或展示
     * @param revisionMode 修订版本模式标识，决定后续授权行采用的处理分支
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
     * @param createBy 创建，保存在对象中供后续校验、查询或展示
     * @param createTime 创建时间，后续用于判断有效期或展示该事件的发生时间
     * @param updateBy 更新，保存在对象中供后续校验、查询或展示
     * @param updateTime 更新时间，后续用于判断有效期或展示该事件的发生时间
     * @param revokedBy 已撤销，保存在对象中供后续校验、查询或展示
     * @param revokedAt 已撤销时间，后续用于判断有效期或展示该事件的发生时间
     */
    record GrantRow(
            String id, String applicationId, String viewId,
            String identityProviderId, String status,
            boolean trustedSubjectAssertion, String revisionMode,
            Long pinnedRevision, String capabilityCeilingJson,
            int maxActiveSessionsPerUser, int maxSessionSeconds,
            int launchLimitPerMinute, int runtimeLimitPerMinute,
            int maxConcurrency, LocalDateTime expiresAt,
            long lockVersion, long securityVersion,
            String createBy, LocalDateTime createTime,
            String updateBy, LocalDateTime updateTime,
            String revokedBy, LocalDateTime revokedAt) {
    }

    /**
     * 封装提供者行的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param name 展示名称，供界面或日志识别
     * @param type 类型标识，决定后续提供者行采用的处理分支
     * @param status 状态标识，决定后续提供者行采用的处理分支
     * @param issuer 签发方，保存在对象中供后续校验、查询或展示
     * @param subjectNamespace 主体命名空间，保存在对象中供后续校验、查询或展示
     * @param audiencesJson {@code audiences}JSON，保存在对象中供后续校验、查询或展示
     * @param algorithmsJson {@code algorithms}JSON，保存在对象中供后续校验、查询或展示
     * @param jwksMode JWKS模式标识，决定后续提供者行采用的处理分支
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
    record ProviderRow(
            String id, String name, String type, String status, String issuer,
            String subjectNamespace, String audiencesJson, String algorithmsJson,
            String jwksMode, String jwksJson, String jwksUrl,
            int clockSkewSeconds, int maxAssertionLifetimeSeconds,
            long keyVersion, long lockVersion, long securityVersion,
            String createBy, LocalDateTime createTime,
            String updateBy, LocalDateTime updateTime,
            String revokedBy, LocalDateTime revokedAt) {
    }

    /**
     * 封装绑定行的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param applicationId 应用ID，后续用于处理绑定行时定位或关联目标
     * @param identityProviderId 身份提供者ID，后续用于处理绑定行时定位或关联目标
     * @param subjectDigest 主体摘要，保存在对象中供后续校验、查询或展示
     * @param subjectDigestKeyVersion 主体摘要键版本，保存在对象中供后续校验、查询或展示
     * @param subjectHint 主体{@code hint}，保存在对象中供后续校验、查询或展示
     * @param flowUserId 流程用户ID，后续用于处理绑定行时定位或关联目标
     * @param flowUserReady 流程用户就绪，保存在对象中供后续校验、查询或展示
     * @param status 状态标识，决定后续绑定行采用的处理分支
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
    record BindingRow(
            String id, String applicationId, String identityProviderId,
            String subjectDigest, String subjectDigestKeyVersion,
            String subjectHint, String flowUserId, boolean flowUserReady, String status,
            long bindingVersion, LocalDateTime effectiveAt, LocalDateTime expiresAt,
            String createBy, LocalDateTime createTime,
            String updateBy, LocalDateTime updateTime,
            String revokedBy, LocalDateTime revokedAt) {
    }

    /**
     * 封装列表目标行的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param configId 配置ID，后续用于处理列表目标行时定位或关联目标
     * @param entityId 实体ID，后续用于处理列表目标行时定位或关联目标
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param releaseId 发布版本 ID，后续用于解析固定配置
     * @param releaseVersion 发布版本号，后续用于校验快照一致性
     * @param snapshotDocument 快照文档，保存在对象中供后续校验、查询或展示
     * @param contentHash 内容哈希，保存在对象中供后续校验、查询或展示
     */
    record ListTargetRow(
            String configId, String entityId, String entityCode, String listKey,
            String releaseId, Long releaseVersion,
            String snapshotDocument, String contentHash) {
    }

    /**
     * 封装表单目标行的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param formId 表单 ID，后续用于定位已发布表单
     * @param entityId 实体ID，后续用于处理表单目标行时定位或关联目标
     * @param releaseId 发布版本 ID，后续用于解析固定配置
     * @param releaseVersion 发布版本号，后续用于校验快照一致性
     * @param snapshotDocument 快照文档，保存在对象中供后续校验、查询或展示
     * @param contentHash 内容哈希，保存在对象中供后续校验、查询或展示
     */
    record FormTargetRow(
            String formId, String entityId, String releaseId, Long releaseVersion,
            String snapshotDocument, String contentHash) {
    }

    /**
     * 封装字段行的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param fieldCode 字段编码，后续用于处理字段行时定位或关联目标
     * @param editable 可编辑，保存在对象中供后续校验、查询或展示
     * @param fieldType 字段类型标识，决定后续字段行采用的处理分支
     */
    record FieldRow(
            String fieldCode, boolean editable, String fieldType) {
    }
}
