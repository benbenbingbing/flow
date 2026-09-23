package com.workflow.entity.form.application;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 表单提交业务追踪键解析服务。
 *
 * <p>优先从请求头 X-Business-Trace-Key 获取并校验，缺失时生成服务端追踪键，
 * 单次请求内复用同一追踪键以串联幂等操作。</p>
 */
@Service
public class FormSubmissionTraceService {

    /** 业务追踪键请求头名称。 */
    public static final String BUSINESS_TRACE_HEADER =
            "X-Business-Trace-Key";

    private static final String REQUEST_ATTRIBUTE =
            FormSubmissionTraceService.class.getName()
                    + ".businessTraceKey";
    private static final Pattern VALID_TRACE_KEY =
            Pattern.compile("[A-Za-z0-9._:@-]{1,160}");

    /**
     * 构建当前提交的执行上下文，自动解析业务追踪键。
     *
     * @param operation         操作类型
     * @param fallbackTraceKey  回退追踪键，请求头缺失时使用
     * @param attributes        附加属性
     * @return 表单提交执行上下文
     */
    public FormSubmissionExecutionContext current(
            String operation,
            String fallbackTraceKey,
            Map<String, Object> attributes) {
        return new FormSubmissionExecutionContext(
                resolveTraceKey(fallbackTraceKey),
                operation,
                attributes);
    }

    /**
     * 解析追踪键；输出作为后续校验或处理的输入。
     *
     * @param fallbackTraceKey 兜底追踪键，主值不可用时供后续处理兜底
     * @return 解析后的追踪键文本，供调用方比较或展示
     */
    String resolveTraceKey(String fallbackTraceKey) {
        ServletRequestAttributes requestAttributes =
                RequestContextHolder.getRequestAttributes()
                        instanceof ServletRequestAttributes servlet
                                ? servlet : null;
        if (requestAttributes != null) {
            HttpServletRequest request =
                    requestAttributes.getRequest();
            Object cached =
                    request.getAttribute(REQUEST_ATTRIBUTE);
            if (cached != null) {
                return String.valueOf(cached);
            }
            String header = request.getHeader(
                    BUSINESS_TRACE_HEADER);
            if (StringUtils.hasText(header)) {
                String validated = validate(header.trim());
                request.setAttribute(
                        REQUEST_ATTRIBUTE,
                        validated);
                return validated;
            }
        }

        String resolved =
                StringUtils.hasText(fallbackTraceKey)
                        ? fallbackTraceKey.trim()
                        : "srv_" + UUID.randomUUID();
        resolved = validate(resolved);
        if (requestAttributes != null) {
            requestAttributes.getRequest().setAttribute(
                    REQUEST_ATTRIBUTE,
                    resolved);
        }
        return resolved;
    }

    /**
     * 校验表单提交追踪；不满足约束时阻止后续处理。
     *
     * @param value 待校验表单提交追踪的原始输入，结果供调用方继续使用
     * @return 校验后的表单提交追踪文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private String validate(String value) {
        if (!VALID_TRACE_KEY.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "业务追踪键格式不合法");
        }
        return value;
    }
}
