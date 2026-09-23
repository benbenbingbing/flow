package com.workflow.embed.management.api;

import static com.workflow.embed.management.api.EmbedManagementRequests.ChangeStatusRequest;
import static com.workflow.embed.management.api.EmbedManagementRequests.CreateViewRequest;
import static com.workflow.embed.management.api.EmbedManagementRequests.UpdateDraftRequest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.result.ApiResponse;
import com.workflow.core.result.PageResult;
import com.workflow.core.security.RequiresPermission;
import com.workflow.embed.management.application.EmbedViewAdministrationService;
import com.workflow.embed.management.application.EmbedViewAdministrationService.CurrentValidation;
import com.workflow.embed.management.application.EmbedViewAdministrationService.StatusChangeResult;
import com.workflow.embed.management.domain.EmbedManagementModel.ChangeStatusCommand;
import com.workflow.embed.management.domain.EmbedManagementModel.CreateViewCommand;
import com.workflow.embed.management.domain.EmbedManagementModel.Page;
import com.workflow.embed.management.domain.EmbedManagementModel.SurfaceType;
import com.workflow.embed.management.domain.EmbedManagementModel.UpdateDraftCommand;
import com.workflow.embed.management.domain.EmbedManagementModel.ViewFilter;
import com.workflow.embed.management.domain.EmbedManagementModel.ViewState;
import com.workflow.embed.management.domain.EmbedManagementModel.ViewStatus;
import jakarta.validation.Valid;
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

    /**
     * 初始化嵌入式视图管理控制器，保存构造参数供后续方法使用。
     *
     * @param service 服务依赖，保存到当前对象供后续业务方法调用
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     */
    public EmbedViewManagementController(
            EmbedViewAdministrationService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    /**
     * 分页查询嵌入式视图管理；查询结果供调用方展示或继续处理。
     *
     * @param keyword 关键字，作为 {@code validatePage} 的输入影响后续处理
     * @param status 状态标识，决定后续嵌入式视图管理采用的处理分支
     * @param surfaceType 界面类型标识，决定后续嵌入式视图管理采用的处理分支
     * @param applicationId 应用ID，后续用于分页查询嵌入式视图管理时定位或关联目标
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @return 符合条件的嵌入式管理视图结果，供调用方继续处理
     */
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

    /**
     * 创建嵌入式视图管理；结果供后续流程传递或持久化。
     *
     * @param request 本次请求，后续经校验后用于创建嵌入式视图管理
     * @return 创建后的嵌入式视图管理结果，供调用方继续处理
     */
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

    /**
     * 读取API{@code response<embed}管理{@code views.view}{@code summary>}；结果供调用方展示或继续处理。
     *
     * @param viewId 视图ID，后续用于读取嵌入式视图管理时定位或关联目标
     * @return 符合条件的API{@code response<embed}管理{@code views.view}{@code summary>}结果，供调用方继续处理
     */
    @GetMapping("/{viewId}")
    public ApiResponse<EmbedManagementViews.ViewSummary> get(@PathVariable String viewId) {
        return ApiResponse.success(view(service.get(viewId)));
    }

    /**
     * 处理草稿，并将结果传给后续步骤。
     *
     * @param viewId 视图ID，后续用于处理草稿时定位或关联目标
     * @return 处理后的草稿结果，供调用方继续处理
     */
    @GetMapping("/{viewId}/draft")
    public ApiResponse<EmbedManagementViews.ViewDraft> draft(@PathVariable String viewId) {
        ViewState view = service.get(viewId);
        return ApiResponse.success(new EmbedManagementViews.ViewDraft(
                view.id(), view.lockVersion(), json(view.draftConfigJson())));
    }

    /**
     * 返回与保存和 Launch 相同规则的当前校验结论，供只读接入向导展示。
     *
     * @param viewId 视图ID，后续用于校验当前活动时定位或关联目标
     * @return 校验后的当前活动结果，供调用方继续处理
     */
    @GetMapping("/{viewId}/validation")
    public ApiResponse<EmbedManagementViews.ViewValidation> validateCurrentActive(
            @PathVariable String viewId) {
        CurrentValidation current = service.validateCurrentActive(viewId);
        var validation = current.validation();
        return ApiResponse.success(new EmbedManagementViews.ViewValidation(
                current.viewStatus().name(), validation.valid(), validation.violations()));
    }

    /**
     * 更新草稿；后续读取或执行将使用更新后的状态。
     *
     * @param viewId 视图ID，后续用于更新草稿时定位或关联目标
     * @param request 本次请求，后续经校验后用于更新草稿
     * @return 更新后的草稿结果，供调用方继续处理
     */
    @PatchMapping("/{viewId}/draft")
    @RequiresPermission("system:embed:manage")
    public ApiResponse<EmbedManagementViews.ViewDraft> updateDraft(
            @PathVariable String viewId,
            @Valid @RequestBody UpdateDraftRequest request) {
        ViewState view = service.updateDraft(viewId,
                new UpdateDraftCommand(request.expectedVersion(), request.draft()));
        return ApiResponse.success(new EmbedManagementViews.ViewDraft(
                view.id(), view.lockVersion(), json(view.draftConfigJson())));
    }

    /**
     * 处理变更状态，并将结果传给后续步骤。
     *
     * @param viewId 视图ID，后续用于处理变更状态时定位或关联目标
     * @param request 本次请求，后续经校验后用于处理变更状态
     * @return 处理后的变更状态结果，供调用方继续处理
     */
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

    /**
     * 处理视图，并将结果传给后续步骤。
     *
     * @param value 待处理视图的原始输入，结果供调用方继续使用
     * @return 处理后的视图结果，供调用方继续处理
     */
    private EmbedManagementViews.ViewSummary view(ViewState value) {
        return new EmbedManagementViews.ViewSummary(
                value.id(), value.viewKey(), value.name(), value.description(),
                value.surfaceType().name(), value.status().name(),
                value.lockVersion(), value.securityVersion(),
                value.createTime(), value.updateTime());
    }

    /**
     * 处理JSON，并将结果传给后续步骤。
     *
     * @param value 待处理JSON的原始输入，结果供调用方继续使用
     * @return 处理后的JSON结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private JsonNode json(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Embed 配置 JSON 数据损坏", exception);
        }
    }

    /**
     * 校验嵌入式视图管理分页；不满足约束时阻止后续处理。
     *
     * @param keyword 关键字，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private static void validatePage(String keyword, int pageNum, int pageSize) {
        if (keyword != null && keyword.length() > 100) {
            throw new IllegalArgumentException("keyword 最大长度为 100");
        }
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageNum 必须大于 0，pageSize 必须为 1 到 100");
        }
    }

    /**
     * 处理枚举或空值，并将结果传给后续步骤。
     *
     * @param type 类型标识，决定后续枚举或空值采用的处理分支
     * @param value 待处理枚举或空值的原始输入，结果供调用方继续使用
     * @return 处理后的枚举或空值结果，供调用方继续处理
     */
    static <E extends Enum<E>> E enumOrNull(Class<E> type, String value) {
        return StringUtils.hasText(value) ? enumRequired(type, value) : null;
    }

    /**
     * 处理枚举必填，并将结果传给后续步骤。
     *
     * @param type 类型标识，决定后续枚举必填采用的处理分支
     * @param value 待处理枚举必填的原始输入，结果供调用方继续使用
     * @return 处理后的枚举必填结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    static <E extends Enum<E>> E enumRequired(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的 " + type.getSimpleName() + " 值");
        }
    }

    /**
     * 清理嵌入式视图管理；后续读取或执行将使用更新后的状态。
     *
     * @param value 待清理嵌入式视图管理的原始输入，结果供调用方继续使用
     * @return 清理后的嵌入式视图管理文本，供调用方比较或展示
     */
    static String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
