package com.workflow.web;

import com.workflow.config.database.MySqlErrorTestConfiguration;

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

    /** 驱动文本与 SQL 不进入响应，同一个码在不同语言下保持相同提示。 */
    @Test
    void databaseErrorsHavePortableMessagesWithoutDriverText() {
        int[] codes = {1062, 1048, 1364, 1406, 1452, 3819, 1264, 1213, 1205, 99999};
        String[] messages = {"数据重复，请检查唯一字段后重试", "必填字段不能为空，请检查后重试",
                "必填字段没有填写且没有默认值，请检查表单配置", "字段内容过长，请缩短后重试",
                "数据关联约束不满足，请检查关联数据后重试", "数据不满足校验约束，请检查后重试",
                "数值超出字段允许范围，请检查后重试", "数据正在被其他请求修改，请刷新后重试",
                "数据正在被其他请求修改，请刷新后重试", "数据库操作失败，请稍后重试"};
        for (int i = 0; i < codes.length; i++) {
            var sql = new java.sql.SQLException("Duplicate entry SECRET in internal_table", "HY000", codes[i]);
            var wrapped = new org.springframework.jdbc.UncategorizedSQLException("write", "INSERT internal_table", sql);
            assertEquals(messages[i], handler.handleDataAccessException(wrapped).getMessage());
            assertEquals(messages[i], handler.handleSQLException(sql).getMessage());
        }
    }

    @Test
    void mixedErrorsDoNotProduceDuplicateMessage() {
        var sql = new java.sql.SQLException("duplicate", "23000", 1062);
        sql.setNextException(new java.sql.SQLException("lost connection", "08006", 0));
        var error = new org.springframework.dao.DuplicateKeyException("misleading wrapper", sql);
        assertEquals("数据库操作失败，请稍后重试", handler.handleDataAccessException(error).getMessage());
    }

    /** MVC 必须命中数据库分支，不能被 RuntimeException 处理器返回底层 SQL。 */
    @Test
    void mvcDispatchesUncategorizedJdbcFailuresToDatabaseHandler() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DatabaseErrorController())
                .setControllerAdvice(handler).build();
        mvc.perform(get("/database-error-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("数据不满足校验约束，请检查后重试"));
    }

    @RestController
    static class DatabaseErrorController {
        @GetMapping("/database-error-test")
        public void fail() {
            throw new org.springframework.jdbc.UncategorizedSQLException("insert", "INSERT private_table",
                    new java.sql.SQLException("SECRET driver text", "HY000", 3819));
        }
    }

    /** 通过 MVC 分派确认专用异常处理器保留字段数据，而非被通用冲突处理器吞掉。 */
    @Test
    void crossFieldErrorsKeepFieldIdentityInConflictResponse() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CrossFieldController())
                .setControllerAdvice(new GlobalExceptionHandler(new MySqlErrorTestConfiguration().databaseExceptionClassifier())).build();
        mvc.perform(get("/cross-field-test"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("FORM_CROSS_FIELD_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.data.fieldErrors[0].fieldCode").value("end"))
                .andExpect(jsonPath("$.data.fieldErrors[0].ruleId").value("range"))
                .andExpect(jsonPath("$.data.fieldErrors[0].targetFieldCode").value("start"));
    }

    @RestController
    static class CrossFieldController {
        @GetMapping("/cross-field-test")
        public void fail() {
            throw new com.workflow.entity.form.application.error.FormCrossFieldValidationException(java.util.List.of(
                    new com.workflow.entity.form.application.error.FormCrossFieldValidationException.FieldError("end", "range", "start", "结束不得早于开始")));
        }
    }

    /** 被测异常处理器实例 */
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(new MySqlErrorTestConfiguration().databaseExceptionClassifier());

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
