package com.workflow.embed.management.api;

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

    public EmbedOperationsManagementController(EmbedOperationsAdministrationService service) {
        this.service = service;
    }

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

    @PostMapping("/launches/{launchId}/revoke")
    @RequiresPermission("system:embed:session-revoke")
    public ApiResponse<EmbedOperationsViews.LaunchRevocationView> revokeLaunch(
            @PathVariable String launchId,
            HttpServletRequest request) {
        LaunchRevocation result = service.revokeLaunch(launchId, correlation(request));
        return ApiResponse.success(new EmbedOperationsViews.LaunchRevocationView(
                launch(result.launch()), result.revoked(), result.idempotent()));
    }

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

    private static EmbedOperationsViews.LaunchView launch(LaunchSummary value) {
        return new EmbedOperationsViews.LaunchView(
                value.id(), value.applicationId(), value.grantId(), value.viewId(),
                value.viewReleaseId(), value.status(), value.entryMode(), value.expiresAt(),
                value.consumedAt(), value.revokedAt(), value.createTime());
    }

    private static EmbedOperationsViews.SessionView session(SessionSummary value) {
        return new EmbedOperationsViews.SessionView(
                value.id(), value.launchId(), value.applicationId(), value.grantId(), value.viewId(),
                value.viewReleaseId(), value.status(), value.entryMode(), value.issuedAt(),
                value.lastSeenAt(), value.idleExpiresAt(), value.absoluteExpiresAt(),
                value.revokedAt(), value.revokeReason());
    }

    private static EmbedOperationsViews.SessionRevocationView sessionRevocation(
            SessionRevocation value) {
        return new EmbedOperationsViews.SessionRevocationView(
                value.sessionId(), value.status(), value.revoked(), value.idempotent());
    }

    private static EmbedOperationsViews.BulkSessionRevocationView bulk(
            BulkSessionRevocation value) {
        return new EmbedOperationsViews.BulkSessionRevocationView(
                value.processed(), value.revoked(), value.alreadyTerminal(), value.nextCursor());
    }

    private static String reason(EmbedOperationsRequests.BulkRevokeRequest request) {
        return request == null ? null : request.reason();
    }

    private static String cursor(EmbedOperationsRequests.BulkRevokeRequest request) {
        return request == null ? null : request.cursor();
    }

    private static Integer limit(EmbedOperationsRequests.BulkRevokeRequest request) {
        return request == null ? null : request.limit();
    }

    /** 管理端撤销事件复用当前 HTTP 请求关联 ID，不以撤销目标 ID 伪造 requestId。 */
    private static EmbedAuditCorrelation correlation(HttpServletRequest request) {
        return EmbedAuditCorrelation.of(
                CorrelationContext.businessTraceId(request),
                CorrelationContext.requestId(request));
    }
}
