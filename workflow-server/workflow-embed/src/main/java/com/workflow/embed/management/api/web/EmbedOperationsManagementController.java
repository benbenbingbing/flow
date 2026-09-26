package com.workflow.embed.management.api.web;

import com.workflow.embed.management.api.request.EmbedOperationsRequests;
import com.workflow.embed.management.api.response.EmbedOperationsViews;

import com.workflow.core.result.ApiResponse;
import com.workflow.core.security.RequiresPermission;
import com.workflow.core.web.CorrelationContext;
import com.workflow.embed.application.audit.EmbedAuditCorrelation;
import com.workflow.embed.management.application.EmbedOperationsAdministrationService;
import com.workflow.embed.management.domain.EmbedOperationsModel.BulkSessionRevocation;
import com.workflow.embed.management.domain.EmbedOperationsModel.LaunchQuery;
import com.workflow.embed.management.domain.EmbedOperationsModel.LaunchRevocation;
import com.workflow.embed.management.domain.EmbedOperationsModel.LaunchSummary;
import com.workflow.embed.management.domain.EmbedOperationsModel.Page;
import com.workflow.embed.management.domain.EmbedOperationsModel.SessionQuery;
import com.workflow.embed.management.domain.EmbedOperationsModel.SessionRevocation;
import com.workflow.embed.management.domain.EmbedOperationsModel.SessionSummary;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Embed Launch/Session 安全查询和撤销管理 API。 */
@RestController
@RequestMapping("/api/embed-management/v1")
@RequiresPermission("system:embed:view")
@ConditionalOnProperty(prefix = "workflow.embed", name = "enabled", havingValue = "true")
public class EmbedOperationsManagementController {

    private final EmbedOperationsAdministrationService service;

    /**
     * 初始化嵌入式操作集合管理控制器，保存构造参数供后续方法使用。
     *
     * @param service 服务依赖，保存到当前对象供后续业务方法调用
     */
    public EmbedOperationsManagementController(EmbedOperationsAdministrationService service) {
        this.service = service;
    }

    /**
     * 按应用、视图、状态和时间分页查询嵌入式启动记录，供管理端查看。
     *
     * @param applicationId 应用 ID，用于限定管理端查询或批量撤销的应用范围
     * @param viewId 视图 ID，用于限定管理端查询或批量撤销的视图范围
     * @param status 状态筛选值，用于限定返回的启动记录或会话
     * @param createdFrom 创建时间下界，用于筛选该时间之后的记录
     * @param createdTo 创建时间上界，用于筛选该时间之前的记录
     * @param cursor 分页游标，用于从上一页结束位置继续查询
     * @param limit 单页数量上限，用于限制本次返回的记录数
     * @return 处理后的启动记录结果，供调用方继续处理
     */
    @GetMapping("/launches")
    public ApiResponse<EmbedOperationsViews.Page<EmbedOperationsViews.LaunchView>> launches(
            @RequestParam(required = false) String applicationId,
            @RequestParam(required = false) String viewId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdTo,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        Page<LaunchSummary> page = service.findLaunches(new LaunchQuery(
                applicationId, viewId, status, createdFrom, createdTo, cursor, limit));
        return ApiResponse.success(new EmbedOperationsViews.Page<>(
                page.items().stream().map(EmbedOperationsManagementController::launch).toList(),
                page.nextCursor()));
    }

    /**
     * 撤销指定启动记录，使关联授权不再可用。
     *
     * @param launchId 启动记录 ID，用于定位待撤销的启动授权
     * @param request 当前 HTTP 请求，用于读取请求关联 ID 或审计上下文
     * @return 撤销后的启动记录结果，供调用方继续处理
     */
    @PostMapping("/launches/{launchId}/revoke")
    @RequiresPermission("system:embed:session-revoke")
    public ApiResponse<EmbedOperationsViews.LaunchRevocationView> revokeLaunch(
            @PathVariable String launchId,
            HttpServletRequest request) {
        LaunchRevocation result = service.revokeLaunch(launchId, correlation(request));
        return ApiResponse.success(new EmbedOperationsViews.LaunchRevocationView(
                launch(result.launch()), result.revoked(), result.idempotent()));
    }

    /**
     * 按应用、视图、状态和时间分页查询嵌入式会话，供管理端查看。
     *
     * @param applicationId 应用 ID，用于限定管理端查询或批量撤销的应用范围
     * @param viewId 视图 ID，用于限定管理端查询或批量撤销的视图范围
     * @param status 状态筛选值，用于限定返回的启动记录或会话
     * @param createdFrom 创建时间下界，用于筛选该时间之后的记录
     * @param createdTo 创建时间上界，用于筛选该时间之前的记录
     * @param cursor 分页游标，用于从上一页结束位置继续查询
     * @param limit 单页数量上限，用于限制本次返回的记录数
     * @return 处理后的会话结果，供调用方继续处理
     */
    @GetMapping("/sessions")
    public ApiResponse<EmbedOperationsViews.Page<EmbedOperationsViews.SessionView>> sessions(
            @RequestParam(required = false) String applicationId,
            @RequestParam(required = false) String viewId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdTo,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        Page<SessionSummary> page = service.findSessions(new SessionQuery(
                applicationId, viewId, status, createdFrom, createdTo, cursor, limit));
        return ApiResponse.success(new EmbedOperationsViews.Page<>(
                page.items().stream().map(EmbedOperationsManagementController::session).toList(),
                page.nextCursor()));
    }

    /**
     * 撤销指定会话，阻止后续使用该会话访问嵌入式视图。
     *
     * @param sessionId 会话 ID，用于定位待撤销的会话
     * @param body 撤销请求体，用于读取操作原因和分页参数
     * @param request 当前 HTTP 请求，用于读取请求关联 ID 或审计上下文
     * @return 撤销后的会话结果，供调用方继续处理
     */
    @PostMapping("/sessions/{sessionId}/revoke")
    @RequiresPermission("system:embed:session-revoke")
    public ApiResponse<EmbedOperationsViews.SessionRevocationView> revokeSession(
            @PathVariable String sessionId,
            @Valid @RequestBody(required = false) EmbedOperationsRequests.RevokeRequest body,
            HttpServletRequest request) {
        SessionRevocation result = service.revokeSession(
                sessionId, body == null ? null : body.reason(), correlation(request));
        return ApiResponse.success(sessionRevocation(result));
    }

    /**
     * 批量撤销指定视图的会话，供管理端执行紧急下线。
     *
     * @param viewId 视图 ID，用于限定管理端查询或批量撤销的视图范围
     * @param body 撤销请求体，用于读取操作原因和分页参数
     * @param request 当前 HTTP 请求，用于读取请求关联 ID 或审计上下文
     * @return 撤销后的视图会话结果，供调用方继续处理
     */
    @PostMapping("/views/{viewId}/sessions/revoke")
    @RequiresPermission("system:embed:session-revoke")
    public ApiResponse<EmbedOperationsViews.BulkSessionRevocationView> revokeViewSessions(
            @PathVariable String viewId,
            @Valid @RequestBody(required = false) EmbedOperationsRequests.BulkRevokeRequest body,
            HttpServletRequest request) {
        BulkSessionRevocation result = service.revokeViewSessions(
                viewId, reason(body), cursor(body), limit(body), correlation(request));
        return ApiResponse.success(bulk(result));
    }

    /**
     * 批量撤销指定应用的会话，供管理端执行紧急下线。
     *
     * @param applicationId 应用 ID，用于限定管理端查询或批量撤销的应用范围
     * @param body 撤销请求体，用于读取操作原因和分页参数
     * @param request 当前 HTTP 请求，用于读取请求关联 ID 或审计上下文
     * @return 撤销后的应用会话结果，供调用方继续处理
     */
    @PostMapping("/applications/{applicationId}/sessions/revoke")
    @RequiresPermission("system:embed:session-revoke")
    public ApiResponse<EmbedOperationsViews.BulkSessionRevocationView> revokeApplicationSessions(
            @PathVariable String applicationId,
            @Valid @RequestBody(required = false) EmbedOperationsRequests.BulkRevokeRequest body,
            HttpServletRequest request) {
        BulkSessionRevocation result = service.revokeApplicationSessions(
                applicationId, reason(body), cursor(body), limit(body), correlation(request));
        return ApiResponse.success(bulk(result));
    }

    /**
     * 将启动记录摘要映射为管理端响应视图。
     *
     * @param value 服务层返回的业务对象，转换为管理端响应视图
     * @return 处理后的启动记录结果，供调用方继续处理
     */
    private static EmbedOperationsViews.LaunchView launch(LaunchSummary value) {
        return new EmbedOperationsViews.LaunchView(
                value.id(), value.applicationId(), value.grantId(), value.viewId(),
                value.viewReleaseId(), value.status(), value.entryMode(), value.expiresAt(),
                value.consumedAt(), value.revokedAt(), value.createTime());
    }

    /**
     * 将会话摘要映射为管理端响应视图。
     *
     * @param value 服务层返回的业务对象，转换为管理端响应视图
     * @return 处理后的会话结果，供调用方继续处理
     */
    private static EmbedOperationsViews.SessionView session(SessionSummary value) {
        return new EmbedOperationsViews.SessionView(
                value.id(), value.launchId(), value.applicationId(), value.grantId(), value.viewId(),
                value.viewReleaseId(), value.status(), value.entryMode(), value.issuedAt(),
                value.lastSeenAt(), value.idleExpiresAt(), value.absoluteExpiresAt(),
                value.revokedAt(), value.revokeReason());
    }

    /**
     * 将会话撤销结果映射为管理端响应视图。
     *
     * @param value 服务层返回的业务对象，转换为管理端响应视图
     * @return 处理后的会话撤销结果，供调用方继续处理
     */
    private static EmbedOperationsViews.SessionRevocationView sessionRevocation(
            SessionRevocation value) {
        return new EmbedOperationsViews.SessionRevocationView(
                value.sessionId(), value.status(), value.revoked(), value.idempotent());
    }

    /**
     * 将批量撤销结果映射为管理端响应视图。
     *
     * @param value 服务层返回的业务对象，转换为管理端响应视图
     * @return 处理后的批量操作结果，供调用方继续处理
     */
    private static EmbedOperationsViews.BulkSessionRevocationView bulk(
            BulkSessionRevocation value) {
        return new EmbedOperationsViews.BulkSessionRevocationView(
                value.processed(), value.revoked(), value.alreadyTerminal(), value.nextCursor());
    }

    /**
     * 从撤销请求读取原因；请求为空时返回 null。
     *
     * @param request 当前 HTTP 请求，用于读取请求关联 ID 或审计上下文
     * @return 处理后的原因文本，供调用方比较或展示
     */
    private static String reason(EmbedOperationsRequests.BulkRevokeRequest request) {
        return request == null ? null : request.reason();
    }

    /**
     * 从撤销请求读取分页游标；请求为空时返回 null。
     *
     * @param request 当前 HTTP 请求，用于读取请求关联 ID 或审计上下文
     * @return 处理后的游标文本，供调用方比较或展示
     */
    private static String cursor(EmbedOperationsRequests.BulkRevokeRequest request) {
        return request == null ? null : request.cursor();
    }

    /**
     * 从撤销请求读取单页上限；请求为空时返回 null。
     *
     * @param request 当前 HTTP 请求，用于读取请求关联 ID 或审计上下文
     * @return 处理后的上限结果，供调用方继续处理
     */
    private static Integer limit(EmbedOperationsRequests.BulkRevokeRequest request) {
        return request == null ? null : request.limit();
    }

    /**
     * 管理端撤销事件复用当前 HTTP 请求关联 ID，不以撤销目标 ID 伪造 requestId。
     *
     * @param request 当前 HTTP 请求，用于读取请求关联 ID 或审计上下文
     * @return 处理后的关联结果，供调用方继续处理
     */
    private static EmbedAuditCorrelation correlation(HttpServletRequest request) {
        return EmbedAuditCorrelation.of(
                CorrelationContext.businessTraceId(request),
                CorrelationContext.requestId(request));
    }
}
