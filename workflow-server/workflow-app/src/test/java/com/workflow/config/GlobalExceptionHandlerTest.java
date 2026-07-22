package com.workflow.config;

import com.workflow.common.ForbiddenException;
import com.workflow.common.BusinessConflictException;
import com.workflow.common.BusinessForbiddenException;
import com.workflow.common.RevisionConflictException;
import com.workflow.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

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

    @Test
    void shouldReturnNotFoundForUnknownApi() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleNoResourceFoundException();

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().getCode());
        assertEquals("资源不存在", response.getBody().getMessage());
    }

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
}
