package com.workflow.embed.management.api.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** 管理 API 请求契约集合。 */
public final class EmbedManagementRequests {

    /**
     * 初始化嵌入式管理{@code requests}，保存构造参数供后续方法使用。
     */
    private EmbedManagementRequests() {
    }

    /**
     * 封装创建视图的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param viewKey 视图键，后续用于授权校验、关联或幂等去重
     * @param name 展示名称，供界面或日志识别
     * @param surfaceType 界面类型标识，决定后续创建视图请求采用的处理分支
     * @param description 描述，保存在对象中供后续校验、查询或展示
     */
    public record CreateViewRequest(
            @NotBlank @Size(max = 100) String viewKey,
            @NotBlank @Size(max = 128) String name,
            @NotBlank String surfaceType,
            @Size(max = 500) String description) {
    }

    /**
     * 封装更新草稿的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param expectedVersion 预期版本，保存在对象中供后续校验、查询或展示
     * @param draft 草稿，保存在对象中供后续校验、查询或展示
     */
    public record UpdateDraftRequest(
            @NotNull Long expectedVersion,
            @NotNull JsonNode draft) {
    }

    /**
     * 封装变更状态的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param expectedVersion 预期版本，保存在对象中供后续校验、查询或展示
     * @param status 状态标识，决定后续变更状态请求采用的处理分支
     * @param reason 原因，保存在对象中供后续校验、查询或展示
     */
    public record ChangeStatusRequest(
            @NotNull Long expectedVersion,
            String status,
            @Size(max = 500) String reason) {
    }

    /**
     * 封装新增或更新授权的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param expectedVersion 预期版本，保存在对象中供后续校验、查询或展示
     * @param status 状态标识，决定后续新增或更新授权请求采用的处理分支
     * @param identityProviderId 身份提供者ID，后续用于处理新增或更新授权请求时定位或关联目标
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
    public record UpsertGrantRequest(
            Long expectedVersion,
            String status,
            @NotBlank String identityProviderId,
            Boolean trustedSubjectAssertion,
            @NotEmpty @Size(max = 20) List<String> allowedOrigins,
            @NotEmpty List<String> capabilityCeiling,
            @NotNull @Min(1) @Max(10_000) Integer maxActiveSessionsPerUser,
            @NotNull @Min(60) @Max(86_400) Integer maxSessionSeconds,
            @NotNull @Min(1) @Max(10_000) Integer launchLimitPerMinute,
            @NotNull @Min(1) @Max(100_000) Integer runtimeLimitPerMinute,
            @NotNull @Min(1) @Max(1_000) Integer maxConcurrency,
            Instant expiresAt) {
    }

    /**
     * 封装创建提供者的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param name 展示名称，供界面或日志识别
     * @param type 类型标识，决定后续创建提供者请求采用的处理分支
     * @param issuer 签发方，保存在对象中供后续校验、查询或展示
     * @param audiences {@code audiences}，保存在对象中供后续校验、查询或展示
     * @param subjectNamespace 主体命名空间，保存在对象中供后续校验、查询或展示
     * @param algorithms {@code algorithms}，保存在对象中供后续校验、查询或展示
     * @param jwksMode JWKS模式标识，决定后续创建提供者请求采用的处理分支
     * @param jwks JWKS，保存在对象中供后续校验、查询或展示
     * @param jwksUrl JWKSURL，保存在对象中供后续校验、查询或展示
     * @param clockSkewSeconds 时钟{@code skew}秒数，保存在对象中供后续校验、查询或展示
     * @param maxAssertionLifetimeSeconds 最大断言{@code lifetime}秒数，保存在对象中供后续校验、查询或展示
     */
    public record CreateProviderRequest(
            @NotBlank @Size(max = 128) String name,
            @NotBlank String type,
            @Size(max = 500) String issuer,
            List<String> audiences,
            @NotBlank @Size(max = 128) String subjectNamespace,
            List<String> algorithms,
            String jwksMode,
            JsonNode jwks,
            @Size(max = 2048) String jwksUrl,
            @NotNull @Min(0) @Max(300) Integer clockSkewSeconds,
            @NotNull @Min(1) @Max(300) Integer maxAssertionLifetimeSeconds) {
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
     * @param jwksMode JWKS模式标识，决定后续更新提供者请求采用的处理分支
     * @param jwks JWKS，保存在对象中供后续校验、查询或展示
     * @param jwksUrl JWKSURL，保存在对象中供后续校验、查询或展示
     * @param clockSkewSeconds 时钟{@code skew}秒数，保存在对象中供后续校验、查询或展示
     * @param maxAssertionLifetimeSeconds 最大断言{@code lifetime}秒数，保存在对象中供后续校验、查询或展示
     */
    public record UpdateProviderRequest(
            @NotNull Long expectedVersion,
            @Size(max = 128) String name,
            @Size(max = 500) String issuer,
            List<String> audiences,
            @Size(max = 128) String subjectNamespace,
            List<String> algorithms,
            String jwksMode,
            JsonNode jwks,
            @Size(max = 2048) String jwksUrl,
            @Min(0) @Max(300) Integer clockSkewSeconds,
            @Min(1) @Max(300) Integer maxAssertionLifetimeSeconds) {
    }

    /**
     * 封装轮换提供者键的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param expectedVersion 预期版本，保存在对象中供后续校验、查询或展示
     * @param jwks JWKS，保存在对象中供后续校验、查询或展示
     */
    public record RotateProviderKeyRequest(
            @NotNull Long expectedVersion,
            @NotNull JsonNode jwks) {
    }

    /**
     * 封装创建绑定的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param applicationId 应用ID，后续用于处理创建绑定请求时定位或关联目标
     * @param identityProviderId 身份提供者ID，后续用于处理创建绑定请求时定位或关联目标
     * @param externalSubject 外部主体，保存在对象中供后续校验、查询或展示
     * @param flowUserId 流程用户ID，后续用于处理创建绑定请求时定位或关联目标
     * @param effectiveAt 有效时间，后续用于判断有效期或展示该事件的发生时间
     * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
     * @param remark {@code remark}，保存在对象中供后续校验、查询或展示
     */
    public record CreateBindingRequest(
            @NotBlank String applicationId,
            @NotBlank String identityProviderId,
            @NotBlank @Size(max = 128) String externalSubject,
            @NotBlank String flowUserId,
            Instant effectiveAt,
            Instant expiresAt,
            @Size(max = 500) String remark) {
    }

    /**
     * 封装查找绑定的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param applicationId 应用ID，后续用于处理查找绑定请求时定位或关联目标
     * @param identityProviderId 身份提供者ID，后续用于处理查找绑定请求时定位或关联目标
     * @param externalSubject 外部主体，保存在对象中供后续校验、查询或展示
     */
    public record LookupBindingRequest(
            @NotBlank String applicationId,
            @NotBlank String identityProviderId,
            @NotBlank @Size(max = 128) String externalSubject) {
    }
}
