package com.workflow.web;

import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.form.application.error.FormCrossFieldValidationException;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.core.error.ForbiddenException;
import com.workflow.core.error.RevisionConflictException;
import com.workflow.core.error.RateLimitExceededException;
import com.workflow.core.result.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataAccessException;
import com.workflow.core.database.jdbc.DatabaseExceptionClassifier;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;

/**
 * 全局异常处理器。
 *
 * <p>请求处理失败时以 ERROR 记录异常对象，保留调用堆栈和根因，避免内部发布错误
 * 被归类为参数或业务异常后丢失诊断信息；客户端响应仍使用各分支约定的提示。
 * 限流、资源不存在和客户端断连沿用各自的常规处理。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    private final DatabaseExceptionClassifier databaseErrors;

    /**
     * 初始化全局异常处理器，保存构造参数供后续方法使用。
     *
     * @param databaseErrors 数据库{@code errors}依赖，保存到当前对象供后续业务方法调用
     */
    public GlobalExceptionHandler(DatabaseExceptionClassifier databaseErrors) {
        this.databaseErrors = databaseErrors;
    }


    /**
     * 处理无法反序列化的 JSON 请求体。
     *
     * <p>只返回稳定的客户端提示，不向页面暴露 Jackson 类型名、字段实现和 coercion 配置。</p>
     *
     * @param exception 请求体解析异常
     * @return 400 响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableRequestBody(
            HttpMessageNotReadableException exception) {
        log.error("请求体格式不正确: {}", exception.getClass().getSimpleName(), exception);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, "请求体格式不正确"));
    }

    /**
     * 处理频率上限，并将结果传给后续步骤。
     *
     * @param exception 异常，供本方法处理频率上限时使用
     * @return 处理后的频率上限结果，供调用方继续处理
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimit(
            RateLimitExceededException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(
                        "Retry-After",
                        String.valueOf(
                                exception.getRetryAfterSeconds()))
                .body(ApiResponse.error(
                        429,
                        "RATE_LIMIT_EXCEEDED",
                        exception.getMessage()));
    }

    /**
     * 处理业务状态冲突异常，返回 409 状态码。
     *
     * @param e 业务冲突异常
     * @return 包含错误码与错误信息的 409 响应
     */
    @ExceptionHandler(BusinessConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessConflictException(BusinessConflictException e) {
        log.error("业务状态冲突: errorCode={}, message={}", e.getErrorCode(), e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(409, e.getErrorCode(), e.getMessage()));
    }

    /**
     * 跨字段错误保留字段与规则标识，前端可定位原表单并保留用户输入。
     *
     * @param e {@code e}，作为 {@code ApiResponse.error} 的输入影响后续处理
     * @return 处理后的表单跨字段校验异常结果，供调用方继续处理
     */
    @ExceptionHandler(FormCrossFieldValidationException.class)
    public ResponseEntity<ApiResponse<Object>> handleFormCrossFieldValidationException(FormCrossFieldValidationException e) {
        ApiResponse<Object> response = ApiResponse.error(409, e.getErrorCode(), e.getMessage());
        response.setData(java.util.Map.of("fieldErrors", e.getFieldErrors()));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * 处理配置修订版本冲突异常，返回 409 状态码并附带当前最新数据。
     *
     * @param e 修订冲突异常
     * @return 包含错误信息与当前最新数据的 409 响应
     */
    @ExceptionHandler(RevisionConflictException.class)
    public ResponseEntity<ApiResponse<Object>> handleRevisionConflictException(
            RevisionConflictException e) {
        log.error("配置修订冲突: {}", e.getMessage(), e);
        ApiResponse<Object> response =
                ApiResponse.error(409, "CONFIG_REVISION_CONFLICT", e.getMessage());
        response.setData(e.getCurrentData());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * 处理访问拒绝异常，返回 403 状态码。
     *
     * <p>当异常为 {@link BusinessForbiddenException} 时附带业务错误码，否则返回通用 403。
     *
     * @param e 访问拒绝异常
     * @return 403 响应
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbiddenException(ForbiddenException e) {
        log.error("访问拒绝: {}", e.getMessage(), e);
        if (e instanceof BusinessForbiddenException businessException) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(
                            403,
                            businessException.getErrorCode(),
                            businessException.getMessage()));
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(403, e.getMessage()));
    }

    /**
     * 处理查询参数或路径参数的类型转换失败。
     *
     * <p>客户端只获得稳定提示，不暴露 Spring 类型名、目标类型或原始异常；
     * 参数名仅写入服务端日志以便排查。</p>
     *
     * @param exception 方法参数类型转换异常
     * @return HTTP 400 及统一错误响应
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception) {
        log.error("请求参数格式不正确: parameter={}", exception.getName(), exception);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, "请求参数格式不正确"));
    }

    /**
     * 处理非法参数异常，返回 400 状态码。
     *
     * @param e 非法参数异常
     * @return 包含错误信息的 400 响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("请求参数异常: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, e.getMessage()));
    }

    /**
     * 处理静态资源未找到异常，返回 404 状态码。
     *
     * @return 404 响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, "资源不存在"));
    }

    /**
     * 所有数据库访问错误使用统一分类；不向客户端拼接 SQL、约束名或驱动原文。
     *
     * @param error 错误，作为 {@code databaseFailure} 的输入影响后续处理
     * @return 处理后的数据访问异常结果，供调用方继续处理
     */
    @ExceptionHandler(DataAccessException.class)
    public ApiResponse<Void> handleDataAccessException(DataAccessException error) {
        log.error("数据库访问异常: ", error);
        return databaseFailure(error);
    }

    /**
     * 处理数据库失败，并将结果传给后续步骤。
     *
     * @param error 错误，供本方法处理数据库失败时使用
     * @return 处理后的数据库失败结果，供调用方继续处理
     */
    private ApiResponse<Void> databaseFailure(Throwable error) {
        String message = switch (databaseErrors.classify(error)) {
            case UNIQUE -> "数据重复，请检查唯一字段后重试";
            case NOT_NULL -> "必填字段不能为空，请检查后重试";
            case MISSING_DEFAULT -> "必填字段没有填写且没有默认值，请检查表单配置";
            case VALUE_TOO_LONG -> "字段内容过长，请缩短后重试";
            case FOREIGN_KEY -> "数据关联约束不满足，请检查关联数据后重试";
            case CHECK -> "数据不满足校验约束，请检查后重试";
            case NUMERIC_RANGE -> "数值超出字段允许范围，请检查后重试";
            case DEADLOCK, LOCK_TIMEOUT, TRANSACTION_ROLLBACK -> "数据正在被其他请求修改，请刷新后重试";
            case CONNECTION, UNKNOWN -> "数据库操作失败，请稍后重试";
        };
        return ApiResponse.error(message);
    }

    /**
     * 处理业务异常（RuntimeException）
     *
     * @param e {@code e}，作为 {@code ApiResponse.error} 的输入影响后续处理
     * @return 处理后的运行时异常结果，供调用方继续处理
     */
    @ExceptionHandler(RuntimeException.class)
    public ApiResponse<Void> handleRuntimeException(RuntimeException e) {
        log.error("业务异常: {}", e.getMessage(), e);
        return ApiResponse.error(e.getMessage());
    }

    /**
     * 处理数据库异常
     *
     * @param e {@code e}，作为 {@code databaseFailure} 的输入影响后续处理
     * @return 处理后的SQL异常结果，供调用方继续处理
     */
    @ExceptionHandler(SQLException.class)
    public ApiResponse<Void> handleSQLException(SQLException e) {
        log.error("数据库异常: ", e);
        return databaseFailure(e);
    }

    /**
     * 客户端主动断开或异步响应流已不可写时，不再包装业务响应体。
     *
     * <p>典型场景是 Prometheus 或浏览器在服务端写出响应时取消请求。
     * 这类异常不是服务端业务错误，若继续走通用异常处理会在已设置
     * OpenMetrics 等 Content-Type 的响应上写入 JSON，造成额外 ERROR 噪声。</p>
     *
     * @param e {@code e}，供本方法处理{@code async}请求非{@code usable}异常时使用
     * @return 处理后的{@code async}请求非{@code usable}异常结果，供调用方继续处理
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public ResponseEntity<Void> handleAsyncRequestNotUsableException(
            AsyncRequestNotUsableException e) {
        log.debug("客户端连接已断开或响应流不可写: {}", e.getMessage());
        return ResponseEntity.noContent().build();
    }

    /**
     * 处理其他所有异常
     *
     * @param e {@code e}，供本方法处理异常时使用
     * @return 处理后的异常结果，供调用方继续处理
     */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("系统异常: ", e);
        return ApiResponse.error("系统繁忙，请稍后重试");
    }
}
