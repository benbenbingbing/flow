package com.workflow.embed.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.embed.runtime.context.EmbedDelegatedRequestContext;
import com.workflow.contracts.embed.runtime.annotation.EmbedDelegatedRuntimeApi;
import com.workflow.core.web.CorrelationContext;
import com.workflow.embed.domain.AuthenticatedEmbedSession;
import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 对已认证 Embed 委托请求执行声明式原生 runtime scope 授权。
 *
 * <p>该拦截器在普通 JWT 拦截器之后、EndpointAuthorization 之前运行。
 * 原生 Flow 数据面不再要求逐端点登记；只有显式声明
 * {@link EmbedDelegatedRuntimeApi} 的历史 Release/target binding 端点执行额外
 * 固定快照校验。随后普通功能权限、对象权限和 DataScope 仍按映射
 * 用户执行。</p>
 */
@Component
@ConditionalOnProperty(prefix = "workflow.embed", name = "enabled", havingValue = "true")
public class EmbedDelegatedRuntimeAuthorizationInterceptor
        implements HandlerInterceptor {

    private final EmbedDelegatedRuntimePolicy policy;
    private final ObjectMapper objectMapper;

    /**
     * 初始化嵌入式委托运行时授权{@code interceptor}，保存构造参数供后续方法使用。
     *
     * @param policy 策略依赖，保存到当前对象供后续业务方法调用
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     */
    public EmbedDelegatedRuntimeAuthorizationInterceptor(
            EmbedDelegatedRuntimePolicy policy,
            ObjectMapper objectMapper) {
        this.policy = policy;
        this.objectMapper = objectMapper;
    }

    /**
     * 判断{@code pre}{@code handle}条件是否成立，供调用方选择后续分支。
     *
     * @param request 本次请求，后续经校验后用于处理{@code pre}{@code handle}
     * @param response 响应，作为 {@code writeError} 的输入影响后续处理
     * @param handler 处理器，供本方法处理{@code pre}{@code handle}时使用
     * @return {@code pre}{@code handle}条件成立时为 true，否则为 false
     * @throws Exception 下游操作失败时向调用方传递
     */
    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws Exception {
        if (!Boolean.TRUE.equals(request.getAttribute(
                EmbedDelegatedRequestContext.VERIFIED_ATTRIBUTE))) {
            return true;
        }
        try {
            if (!(handler instanceof HandlerMethod method)) {
                throw denied();
            }
            EmbedDelegatedRuntimeApi declaration =
                    AnnotatedElementUtils.findMergedAnnotation(
                            method.getMethod(), EmbedDelegatedRuntimeApi.class);
            if (declaration == null) {
                declaration = AnnotatedElementUtils.findMergedAnnotation(
                        method.getBeanType(), EmbedDelegatedRuntimeApi.class);
            }
            Object authenticated = request.getAttribute(
                    EmbedDelegatedRequestContext
                            .AUTHENTICATED_SESSION_ATTRIBUTE);
            if (!(authenticated instanceof AuthenticatedEmbedSession session)) {
                throw denied();
            }
            if (declaration == null) {
                // 未声明端点是普通 Flow 原生数据面；这里不维护
                // 组件/接口白名单，继续交给 EndpointAuthorization 和
                // 业务对象授权。
                return true;
            }
            Object parsed = request.getAttribute(
                    EmbedDelegatedRequestContext.JSON_BODY_ATTRIBUTE);
            JsonNode body = parsed instanceof JsonNode value ? value : null;
            var target = policy.authorize(
                    request,
                    session,
                    body,
                    declaration);
            request.setAttribute(
                    EmbedDelegatedRequestContext.TARGET_ATTRIBUTE,
                    new EmbedDelegatedRequestContext.AuthorizedTarget(
                            target.entityCode(), target.formId(),
                            target.formReleaseId(),
                            target.formReleaseVersion(), target.entryMode(),
                            target.recordId()));
            return true;
        } catch (EmbedException error) {
            writeError(request, response, error);
            return false;
        }
    }

    /**
     * 写入错误；后续读取或执行将使用更新后的状态。
     *
     * @param request 本次请求，后续经校验后用于写入错误
     * @param response 响应，作为 {@code objectMapper.writeValue} 的输入影响后续处理
     * @param error 错误，作为 {@code response.setStatus} 的输入影响后续处理
     * @throws Exception 下游操作失败时向调用方传递
     */
    private void writeError(
            HttpServletRequest request,
            HttpServletResponse response,
            EmbedException error) throws Exception {
        response.setStatus(error.getStatus());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", error.getStatus());
        body.put("message", error.getMessage());
        body.put("errorCode", error.getErrorCode().name());
        body.put("data", null);
        body.put("traceId", CorrelationContext.businessTraceId(request));
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    /**
     * 构造已拒绝异常，供调用方区分失败原因并终止后续处理。
     *
     * @return 处理后的已拒绝结果，供调用方继续处理
     */
    private static EmbedException denied() {
        return new EmbedException(
                403,
                EmbedErrorCode.EMBED_OPERATION_NOT_ALLOWED,
                "Embed delegated runtime operation is not allowed");
    }
}
