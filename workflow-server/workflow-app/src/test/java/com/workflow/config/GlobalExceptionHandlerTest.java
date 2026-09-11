package com.workflow.config;

import com.workflow.core.error.ForbiddenException;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.core.error.RevisionConflictException;
import com.workflow.core.result.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import com.workflow.core.error.RateLimitExceededException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 全局异常处理器单元测试。
 *
 * <p>被测对象为 {@link GlobalExceptionHandler}，验证各类业务异常
 * 被转换为正确的 HTTP 状态码与结构化响应体(含稳定业务错误码)。</p>
 */
class GlobalExceptionHandlerTest {

    /** 被测异常处理器实例 */
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    /** 畸形 JSON 应返回稳定 400，不能把 Jackson 内部反序列化详情暴露给页面。 */
    @Test
    void shouldReturnSafeBadRequestForUnreadableJsonBody() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleUnreadableRequestBody(
                        new HttpMessageNotReadableException(
                                "Cannot coerce empty String to internal.SysGroup",
                                new MockHttpInputMessage(new byte[0])));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("请求体格式不正确", response.getBody().getMessage());
    }

    @Test
    void returnsRetryAfterForRateLimit() {
        var response = handler.handleRateLimit(
                new RateLimitExceededException(
                        "稍后重试",
                        42));

        assertEquals(
                HttpStatus.TOO_MANY_REQUESTS,
                response.getStatusCode());
        assertEquals(
                "42",
                response.getHeaders().getFirst("Retry-After"));
    }

    /** 非法参数异常应返回 400 BAD_REQUEST 且消息正确 */
    @Test
    void shouldReturnBadRequestForInvalidPermissionConfiguration() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleIllegalArgumentException(
                        new IllegalArgumentException("结构化条件不能为空"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("结构化条件不能为空", response.getBody().getMessage());
    }

    /** enabled/pageNum 转换失败必须返回真实 HTTP 400 与稳定响应体。 */
    @Test
    void shouldReturnStableBadRequestForQueryParameterTypeMismatch()
            throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new QueryParameterController())
                .setControllerAdvice(handler)
                .build();

        mockMvc.perform(get("/test/query-parameters")
                        .param("enabled", "not-a-boolean"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message")
                        .value("请求参数格式不正确"));

        mockMvc.perform(get("/test/query-parameters")
                        .param("pageNum", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message")
                        .value("请求参数格式不正确"));
    }

    /** 权限拒绝异常应返回 403 FORBIDDEN 且消息正确 */
    @Test
    void shouldReturnForbiddenForPermissionDenial() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleForbiddenException(
                        new ForbiddenException("数据不存在或无权访问"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(403, response.getBody().getCode());
        assertEquals("数据不存在或无权访问", response.getBody().getMessage());
    }

    /** 业务权限拒绝异常应返回 403 且携带稳定业务错误码 */
    @Test
    void shouldReturnForbiddenWithStableBusinessCode() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleForbiddenException(
                        new BusinessForbiddenException(
                                "UI_DATA_SOURCE_PUBLISHED_BINDING_REQUIRED",
                                "当前发布版本未绑定该数据源"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(403, response.getBody().getCode());
        assertEquals(
                "UI_DATA_SOURCE_PUBLISHED_BINDING_REQUIRED",
                response.getBody().getErrorCode());
    }

    /** 未知 API 请求应返回 404 NOT_FOUND 且消息为"资源不存在" */
    @Test
    void shouldReturnNotFoundForUnknownApi() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleNoResourceFoundException();

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().getCode());
        assertEquals("资源不存在", response.getBody().getMessage());
    }

    /** 客户端断开连接不应包装业务 JSON 响应，避免污染监控端点日志。 */
    @Test
    void shouldNotWrapDisconnectedClientResponse() {
        ResponseEntity<Void> response =
                handler.handleAsyncRequestNotUsableException(
                        new AsyncRequestNotUsableException("Broken pipe"));

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    /** 业务冲突异常应返回 409 CONFLICT 且携带稳定业务错误码 */
    @Test
    void shouldReturnConflictWithStableBusinessCode() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBusinessConflictException(
                        new BusinessConflictException(
                                "ENTITY_WORKFLOW_NOT_SUPPORTED",
                                "独立业务实体不支持发起流程"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().getCode());
        assertEquals("ENTITY_WORKFLOW_NOT_SUPPORTED", response.getBody().getErrorCode());
    }

    /**
     * 版本冲突异常应返回 409 且响应体携带当前服务器数据。
     *
     * <p>场景：RevisionConflictException 含当前数据快照，
     * 断言响应体 errorCode 为 CONFIG_REVISION_CONFLICT 且 data 含服务器当前版本。</p>
     */
    @Test
    void shouldReturnCurrentServerDataForRevisionConflict() {
        Object current = java.util.Map.of(
                "id", "node-1",
                "revision", 9);

        ResponseEntity<ApiResponse<Object>> response =
                handler.handleRevisionConflictException(
                        new RevisionConflictException(
                                "节点已被其他管理员修改",
                                current));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().getCode());
        assertEquals(
                "CONFIG_REVISION_CONFLICT",
                response.getBody().getErrorCode());
        assertEquals(current, response.getBody().getData());
    }

    /** 仅用于验证 Spring MVC 查询参数绑定与全局 advice 的集成行为。 */
    @RestController
    static final class QueryParameterController {

        @GetMapping("/test/query-parameters")
        public ApiResponse<Void> query(
                @RequestParam(required = false) Boolean enabled,
                @RequestParam(required = false) Integer pageNum) {
            return ApiResponse.success();
        }
    }
}
