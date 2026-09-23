package com.workflow.openapi.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.openapi.api.response.OpenApiResponse;
import com.workflow.openapi.web.OpenRequestTrace;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;

/**
 * 封装打开API安全响应写入器相关能力和状态；供同一业务流程的后续处理使用。
 */
public class OpenApiSecurityResponseWriter {

    private final ObjectMapper objectMapper;

    /**
     * 初始化打开API安全响应写入器，保存构造参数供后续方法使用。
     *
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     */
    public OpenApiSecurityResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 写入打开API安全响应写入器；后续读取或执行将使用更新后的状态。
     *
     * @param request 本次请求，后续经校验后用于写入打开API安全响应写入器
     * @param response 响应，作为 {@code objectMapper.writeValue} 的输入影响后续处理
     * @param status 目标状态，写入记录后供流程分支或列表查询使用
     * @param errorCode 错误编码，后续用于写入打开API安全响应写入器时定位或关联目标
     * @param message 消息，供本方法写入打开API安全响应写入器时使用
     * @param retryAfterSeconds 重试之后秒数，作为 {@code response.setHeader} 的输入影响后续处理
     * @throws IOException 读取或写入外部资源失败时抛出
     */
    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String errorCode,
            String message,
            Long retryAfterSeconds) throws IOException {
        response.setStatus(status);
        response.setContentType(
                "application/json;charset=UTF-8");
        response.setHeader(
                HttpHeaders.CACHE_CONTROL,
                "no-store");
        if (retryAfterSeconds != null) {
            response.setHeader(
                    HttpHeaders.RETRY_AFTER,
                    String.valueOf(retryAfterSeconds));
        }
        objectMapper.writeValue(
                response.getOutputStream(),
                OpenApiResponse.error(
                        status,
                        message,
                        errorCode,
                        null,
                        OpenRequestTrace.get(request)));
    }
}
