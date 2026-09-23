package com.workflow.embed.management.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.workflow.embed.management.domain.EmbedManagementModel.Violation;
import java.time.LocalDateTime;
import java.util.List;

/** 管理 API 响应投影；敏感摘要、内部权限和原始 Subject 均不在契约中。 */
public final class EmbedManagementViews {

    /**
     * 初始化嵌入式管理视图，保存构造参数供后续方法使用。
     */
    private EmbedManagementViews() {
    }

    /**
     * 封装应用选项视图的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param name 展示名称，供界面或日志识别
     * @param clientId 客户端ID，后续用于处理应用选项视图时定位或关联目标
     * @param status 状态标识，决定后续应用选项视图采用的处理分支
     * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
     * @param embedLaunchReady 嵌入式启动记录就绪，保存在对象中供后续校验、查询或展示
     */
    public record ApplicationOptionView(
            String id, String name, String clientId, String status,
            java.time.Instant expiresAt, boolean embedLaunchReady) {
    }

    /**
     * 封装身份提供者选项视图的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param name 展示名称，供界面或日志识别
     * @param type 类型标识，决定后续身份提供者选项视图采用的处理分支
     * @param status 状态标识，决定后续身份提供者选项视图采用的处理分支
     */
    public record IdentityProviderOptionView(
            String id, String name, String type, String status) {
    }

    /**
     * 封装视图摘要的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param viewKey 视图键，后续用于授权校验、关联或幂等去重
     * @param name 展示名称，供界面或日志识别
     * @param description 描述，保存在对象中供后续校验、查询或展示
     * @param surfaceType 界面类型标识，决定后续视图摘要采用的处理分支
     * @param status 状态标识，决定后续视图摘要采用的处理分支
     * @param version 版本，保存在对象中供后续校验、查询或展示
     * @param securityVersion 安全版本，保存在对象中供后续校验、查询或展示
     * @param createTime 创建时间，后续用于判断有效期或展示该事件的发生时间
     * @param updateTime 更新时间，后续用于判断有效期或展示该事件的发生时间
     */
    public record ViewSummary(
            String id, String viewKey, String name, String description,
            String surfaceType, String status, long version, long securityVersion,
            LocalDateTime createTime, LocalDateTime updateTime) {
    }

    /**
     * 封装视图草稿的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param viewId 视图ID，后续用于处理视图草稿时定位或关联目标
     * @param version 版本，保存在对象中供后续校验、查询或展示
     * @param draft 草稿，保存在对象中供后续校验、查询或展示
     */
    public record ViewDraft(
            String viewId, long version, JsonNode draft) {
    }

    /**
     * 接入向导使用的只读校验结果，不暴露解析快照或完整 canonical 配置。
     *
     * @param viewStatus 视图状态标识，决定后续视图校验采用的处理分支
     * @param valid 有效，后续用于处理视图校验时定位或关联目标
     * @param violations 违规项，保存在对象中供后续校验、查询或展示
     */
    public record ViewValidation(
            String viewStatus, boolean valid, List<Violation> violations) {
    }

    /**
     * 封装状态的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param view 视图，保存在对象中供后续校验、查询或展示
     * @param affectedActiveSessions {@code affected}活动会话，保存在对象中供后续校验、查询或展示
     */
    public record StatusResult(ViewSummary view, long affectedActiveSessions) {
    }

    /**
     * 封装授权视图的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param applicationId 应用ID，后续用于处理授权视图时定位或关联目标
     * @param viewId 视图ID，后续用于处理授权视图时定位或关联目标
     * @param identityProviderId 身份提供者ID，后续用于处理授权视图时定位或关联目标
     * @param status 状态标识，决定后续授权视图采用的处理分支
     * @param trustedSubjectAssertion 可信主体断言，保存在对象中供后续校验、查询或展示
     * @param capabilityCeiling 能力{@code ceiling}，保存在对象中供后续校验、查询或展示
     * @param allowedOrigins 允许来源，保存在对象中供后续校验、查询或展示
     * @param maxActiveSessionsPerUser 最大活动会话每用户，保存在对象中供后续校验、查询或展示
     * @param maxSessionSeconds 最大会话秒数，保存在对象中供后续校验、查询或展示
     * @param launchLimitPerMinute 启动记录上限每分钟，保存在对象中供后续校验、查询或展示
     * @param runtimeLimitPerMinute 运行时上限每分钟，保存在对象中供后续校验、查询或展示
     * @param maxConcurrency 最大{@code concurrency}，保存在对象中供后续校验、查询或展示
     * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
     * @param version 版本，保存在对象中供后续校验、查询或展示
     * @param securityVersion 安全版本，保存在对象中供后续校验、查询或展示
     * @param createTime 创建时间，后续用于判断有效期或展示该事件的发生时间
     * @param updateTime 更新时间，后续用于判断有效期或展示该事件的发生时间
     */
    public record GrantView(
            String id, String applicationId, String viewId,
            String identityProviderId, String status,
            boolean trustedSubjectAssertion, JsonNode capabilityCeiling,
            List<String> allowedOrigins, int maxActiveSessionsPerUser,
            int maxSessionSeconds, int launchLimitPerMinute,
            int runtimeLimitPerMinute, int maxConcurrency,
            LocalDateTime expiresAt, long version, long securityVersion,
            LocalDateTime createTime, LocalDateTime updateTime) {
    }

    /**
     * 封装提供者视图的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param name 展示名称，供界面或日志识别
     * @param type 类型标识，决定后续提供者视图采用的处理分支
     * @param status 状态标识，决定后续提供者视图采用的处理分支
     * @param issuer 签发方，保存在对象中供后续校验、查询或展示
     * @param subjectNamespace 主体命名空间，保存在对象中供后续校验、查询或展示
     * @param audiences {@code audiences}，保存在对象中供后续校验、查询或展示
     * @param algorithms {@code algorithms}，保存在对象中供后续校验、查询或展示
     * @param jwksMode JWKS模式标识，决定后续提供者视图采用的处理分支
     * @param jwks JWKS，保存在对象中供后续校验、查询或展示
     * @param jwksUrl JWKSURL，保存在对象中供后续校验、查询或展示
     * @param clockSkewSeconds 时钟{@code skew}秒数，保存在对象中供后续校验、查询或展示
     * @param maxAssertionLifetimeSeconds 最大断言{@code lifetime}秒数，保存在对象中供后续校验、查询或展示
     * @param keyVersion 键版本，保存在对象中供后续校验、查询或展示
     * @param version 版本，保存在对象中供后续校验、查询或展示
     * @param securityVersion 安全版本，保存在对象中供后续校验、查询或展示
     * @param createTime 创建时间，后续用于判断有效期或展示该事件的发生时间
     * @param updateTime 更新时间，后续用于判断有效期或展示该事件的发生时间
     */
    public record ProviderView(
            String id, String name, String type, String status, String issuer,
            String subjectNamespace, JsonNode audiences, JsonNode algorithms,
            String jwksMode, JsonNode jwks, String jwksUrl,
            int clockSkewSeconds, int maxAssertionLifetimeSeconds,
            long keyVersion, long version, long securityVersion,
            LocalDateTime createTime, LocalDateTime updateTime) {
    }

    /**
     * 封装绑定视图的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param applicationId 应用ID，后续用于处理绑定视图时定位或关联目标
     * @param identityProviderId 身份提供者ID，后续用于处理绑定视图时定位或关联目标
     * @param subjectHint 主体{@code hint}，保存在对象中供后续校验、查询或展示
     * @param flowUserId 流程用户ID，后续用于处理绑定视图时定位或关联目标
     * @param flowUserReady 流程用户就绪，保存在对象中供后续校验、查询或展示
     * @param status 状态标识，决定后续绑定视图采用的处理分支
     * @param version 版本，保存在对象中供后续校验、查询或展示
     * @param effectiveAt 有效时间，后续用于判断有效期或展示该事件的发生时间
     * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
     * @param createTime 创建时间，后续用于判断有效期或展示该事件的发生时间
     * @param updateTime 更新时间，后续用于判断有效期或展示该事件的发生时间
     */
    public record BindingView(
            String id, String applicationId, String identityProviderId,
            String subjectHint, String flowUserId, boolean flowUserReady, String status,
            long version, LocalDateTime effectiveAt, LocalDateTime expiresAt,
            LocalDateTime createTime, LocalDateTime updateTime) {
    }
}
