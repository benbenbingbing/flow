package com.workflow.openapi.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.openapi.api.response.OpenApiResponse;
import com.workflow.openapi.web.OpenRequestTrace;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;

public class OpenApiSecurityResponseWriter {

    private final ObjectMapper objectMapper;

    public OpenApiSecurityResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

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
