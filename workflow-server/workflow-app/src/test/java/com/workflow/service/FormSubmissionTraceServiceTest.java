package com.workflow.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 表单提交链路追踪服务测试。
 *
 * <p>被测对象：{@link FormSubmissionTraceService}，覆盖请求内复用客户端业务链路 key、
 * 不同提交生成不同绑定幂等键、拒绝不安全的业务链路请求头等场景。
 */
class FormSubmissionTraceServiceTest {

    /** 被测追踪服务 */
    private final FormSubmissionTraceService service =
            new FormSubmissionTraceService();

    /** 清理请求上下文，避免用例间污染 */
    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    /** 测试同一请求内复用客户端业务链路 key：验证两次 current 调用返回相同 businessTraceKey */
    @Test
    void reusesClientBusinessTraceKeyWithinRequest() {
        MockHttpServletRequest request =
                new MockHttpServletRequest();
        request.addHeader(
                FormSubmissionTraceService.BUSINESS_TRACE_HEADER,
                "ui_retry_123");
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request));

        FormSubmissionExecutionContext first =
                service.current(
                        "ENTITY_UPDATE",
                        null,
                        Map.of());
        FormSubmissionExecutionContext second =
                service.current(
                        "ENTITY_UPDATE",
                        null,
                        Map.of());

        assertEquals(
                "ui_retry_123",
                first.businessTraceKey());
        assertEquals(
                first.businessTraceKey(),
                second.businessTraceKey());
    }

    /** 测试不同业务提交生成不同绑定幂等键：验证不同 trace 产出的幂等键互不相同 */
    @Test
    void differentBusinessSubmissionsProduceDifferentBindingKeys() {
        FormSubmissionExecutionContext first =
                new FormSubmissionExecutionContext(
                        "trace-1",
                        "ENTITY_UPDATE",
                        Map.of());
        FormSubmissionExecutionContext second =
                new FormSubmissionExecutionContext(
                        "trace-2",
                        "ENTITY_UPDATE",
                        Map.of());

        assertNotEquals(
                first.bindingIdempotencyKey(
                        "form-1",
                        "node:node-1",
                        "source-1",
                        0),
                second.bindingIdempotencyKey(
                        "form-1",
                        "node:node-1",
                        "source-1",
                        0));
    }

    /** 测试拒绝不安全的业务链路请求头：验证含空格的非法 header 触发 IllegalArgumentException */
    @Test
    void rejectsUnsafeBusinessTraceHeader() {
        MockHttpServletRequest request =
                new MockHttpServletRequest();
        request.addHeader(
                FormSubmissionTraceService.BUSINESS_TRACE_HEADER,
                "bad trace key");
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.current(
                        "ENTITY_CREATE",
                        null,
                        Map.of()));
    }
}
