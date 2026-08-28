package com.workflow.embed.management.api;

import static com.workflow.embed.management.api.EmbedManagementRequests.ChangeStatusRequest;
import static com.workflow.embed.management.api.EmbedManagementRequests.CreateViewRequest;
import static com.workflow.embed.management.api.EmbedManagementRequests.PublishViewRequest;
import static com.workflow.embed.management.api.EmbedManagementRequests.UpdateDraftRequest;
import static com.workflow.embed.management.api.EmbedManagementRequests.VersionRequest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.result.ApiResponse;
import com.workflow.core.result.PageResult;
import com.workflow.core.security.RequiresPermission;
import com.workflow.embed.management.application.EmbedViewAdministrationService;
import com.workflow.embed.management.application.EmbedViewAdministrationService.StatusChangeResult;
import com.workflow.embed.management.domain.EmbedManagementModel.ChangeStatusCommand;
import com.workflow.embed.management.domain.EmbedManagementModel.CreateViewCommand;
import com.workflow.embed.management.domain.EmbedManagementModel.Page;
import com.workflow.embed.management.domain.EmbedManagementModel.PublishViewCommand;
import com.workflow.embed.management.domain.EmbedManagementModel.ReleaseState;
import com.workflow.embed.management.domain.EmbedManagementModel.SurfaceType;
import com.workflow.embed.management.domain.EmbedManagementModel.UpdateDraftCommand;
import com.workflow.embed.management.domain.EmbedManagementModel.ValidationResult;
import com.workflow.embed.management.domain.EmbedManagementModel.ViewFilter;
import com.workflow.embed.management.domain.EmbedManagementModel.ViewState;
import com.workflow.embed.management.domain.EmbedManagementModel.ViewStatus;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Embed View 管理 API；继续由普通 Flow JWT 与 Endpoint 权限拦截器保护。 */
@RestController
@RequestMapping("/api/embed-management/v1/views")
@RequiresPermission("system:embed:view")
public class EmbedViewManagementController {

    private final EmbedViewAdministrationService service;
    private final ObjectMapper objectMapper;

    public EmbedViewManagementController(
            EmbedViewAdministrationService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ApiResponse<PageResult<EmbedManagementViews.ViewSummary>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String surfaceType,
            @RequestParam(required = false) String applicationId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        validatePage(keyword, pageNum, pageSize);
        Page<ViewState> page = service.page(new ViewFilter(
                trim(keyword), enumOrNull(ViewStatus.class, status),
                enumOrNull(SurfaceType.class, surfaceType), trim(applicationId),
                pageNum, pageSize));
        return ApiResponse.success(new PageResult<>(
                page.records().stream().map(this::view).toList(), page.total(),
                page.pageNum(), page.pageSize()));
    }

    @PostMapping
    @RequiresPermission("system:embed:manage")
    public ResponseEntity<ApiResponse<EmbedManagementViews.ViewSummary>> create(
            @Valid @RequestBody CreateViewRequest request) {
        ViewState view = service.create(new CreateViewCommand(
                request.viewKey(), request.name(),
                enumRequired(SurfaceType.class, request.surfaceType()),
                request.description()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(view(view)));
    }

    @GetMapping("/{viewId}")
    public ApiResponse<EmbedManagementViews.ViewSummary> get(@PathVariable String viewId) {
        return ApiResponse.success(view(service.get(viewId)));
    }

    @GetMapping("/{viewId}/draft")
    public ApiResponse<EmbedManagementViews.ViewDraft> draft(@PathVariable String viewId) {
        ViewState view = service.get(viewId);
        return ApiResponse.success(new EmbedManagementViews.ViewDraft(
                view.id(), view.draftRevision(), view.lockVersion(), json(view.draftConfigJson())));
    }

    @PatchMapping("/{viewId}/draft")
    @RequiresPermission("system:embed:manage")
    public ApiResponse<EmbedManagementViews.ViewDraft> updateDraft(
            @PathVariable String viewId,
            @Valid @RequestBody UpdateDraftRequest request) {
        ViewState view = service.updateDraft(viewId,
                new UpdateDraftCommand(request.expectedVersion(), request.draft()));
        return ApiResponse.success(new EmbedManagementViews.ViewDraft(
                view.id(), view.draftRevision(), view.lockVersion(), json(view.draftConfigJson())));
    }

    @PostMapping("/{viewId}/validate")
    @RequiresPermission("system:embed:manage")
    public ApiResponse<ValidationResult> validate(
            @PathVariable String viewId, @Valid @RequestBody VersionRequest request) {
        ValidationResult validation = service.validate(viewId, request.expectedVersion());
        if (!validation.valid()) {
            throw new EmbedManagementException(422, "EMBED_VIEW_VALIDATION_FAILED",
                    "Embed view validation failed",
                    java.util.Map.of("violations", validation.violations()));
        }
        return ApiResponse.success(validation);
    }

    @PostMapping("/{viewId}/publish")
    @RequiresPermission("system:embed:publish")
    public ApiResponse<EmbedManagementViews.PublishResult> publish(
            @PathVariable String viewId,
            @Valid @RequestBody PublishViewRequest request) {
        ReleaseState release = service.publish(viewId,
                new PublishViewCommand(request.expectedVersion(), request.releaseNote()));
        return ApiResponse.success(new EmbedManagementViews.PublishResult(
                release.viewId(), release.revision(), release.id(),
                release.configHash(), release.publishedAt()));
    }

    @PostMapping("/{viewId}/status")
    @RequiresPermission("system:embed:manage")
    public ApiResponse<EmbedManagementViews.StatusResult> changeStatus(
            @PathVariable String viewId,
            @Valid @RequestBody ChangeStatusRequest request) {
        StatusChangeResult result = service.changeStatus(viewId,
                new ChangeStatusCommand(
                        request.expectedVersion(), request.status(), request.reason()));
        return ApiResponse.success(new EmbedManagementViews.StatusResult(
                view(result.view()), result.affectedActiveSessions()));
    }

    @GetMapping("/{viewId}/releases")
    public ApiResponse<List<EmbedManagementViews.ReleaseView>> releases(
            @PathVariable String viewId) {
        return ApiResponse.success(service.releases(viewId).stream().map(this::release).toList());
    }

    @GetMapping("/{viewId}/releases/{revision}")
    public ApiResponse<EmbedManagementViews.ReleaseView> release(
            @PathVariable String viewId, @PathVariable long revision) {
        return ApiResponse.success(release(service.release(viewId, revision)));
    }

    private EmbedManagementViews.ViewSummary view(ViewState value) {
        return new EmbedManagementViews.ViewSummary(
                value.id(), value.viewKey(), value.name(), value.description(),
                value.surfaceType().name(), value.status().name(), value.draftRevision(),
                value.publishedRevision(), value.lockVersion(), value.securityVersion(),
                value.createTime(), value.updateTime());
    }

    private EmbedManagementViews.ReleaseView release(ReleaseState value) {
        return new EmbedManagementViews.ReleaseView(
                value.id(), value.viewId(), value.revision(), value.surfaceType().name(),
                value.entityCode(), value.listKey(), value.defaultFormId(),
                value.listReleaseId(), value.listReleaseVersion(), value.formReleaseId(),
                value.formReleaseVersion(), value.configHash(), value.releaseNote(),
                value.publishedBy(), value.publishedAt(), json(value.configJson()));
    }

    private JsonNode json(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Embed 配置 JSON 数据损坏", exception);
        }
    }

    private static void validatePage(String keyword, int pageNum, int pageSize) {
        if (keyword != null && keyword.length() > 100) {
            throw new IllegalArgumentException("keyword 最大长度为 100");
        }
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageNum 必须大于 0，pageSize 必须为 1 到 100");
        }
    }

    static <E extends Enum<E>> E enumOrNull(Class<E> type, String value) {
        return StringUtils.hasText(value) ? enumRequired(type, value) : null;
    }

    static <E extends Enum<E>> E enumRequired(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的 " + type.getSimpleName() + " 值");
        }
    }

    static String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
