package com.workflow.config;

import com.workflow.common.ForbiddenException;
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
    void shouldReturnNotFoundForUnknownApi() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleNoResourceFoundException();

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().getCode());
        assertEquals("资源不存在", response.getBody().getMessage());
    }
}
