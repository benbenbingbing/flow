package com.workflow.entity.ui.api.web;

import com.workflow.core.result.Result;
import com.workflow.core.security.AuthenticatedApi;
import com.workflow.core.security.RequiresPermission;
import com.workflow.entity.ui.api.request.UiConfigPublishRequest;
import com.workflow.entity.ui.api.request.UiHotfixApplyRequest;
import com.workflow.entity.ui.api.request.UiHotfixCancelRequest;
import com.workflow.entity.ui.api.request.UiHotfixObservationMetricRequest;
import com.workflow.entity.ui.api.request.UiHotfixReviewRequest;
import com.workflow.entity.ui.api.response.UiConfigPublishPreviewDTO;
import com.workflow.entity.ui.api.response.UiHotfixRequestDTO;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.entity.ui.application.UiConfigurationAccessService;
import com.workflow.entity.ui.application.UiHotfixGovernanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** UI HOTFIX 申请、复核与观察窗口接口。 */
@AuthenticatedApi(objectAuthorization = true)
@RestController
@RequestMapping("/api/ui-hotfix-requests")
@RequiredArgsConstructor
public class UiHotfixGovernanceController {

    private final UiConfigReleaseService releaseService;
    private final UiHotfixGovernanceService governanceService;
    private final UiConfigurationAccessService accessService;

    @PostMapping
    @RequiresPermission("entity:ui-config:hotfix")
    public Result<UiHotfixRequestDTO> apply(@RequestBody UiHotfixApplyRequest request) {
        requireObjectAccess(request.getConfigType(), request.getConfigId());
        UiConfigPublishRequest previewRequest = new UiConfigPublishRequest();
        previewRequest.setReleaseMode(UiConfigReleaseService.HOTFIX);
        previewRequest.setExpectedDraftHash(request.getExpectedDraftHash());
        previewRequest.setExpectedActiveReleaseId(request.getExpectedActiveReleaseId());
        previewRequest.setImpactToken(request.getImpactToken());
        UiConfigPublishPreviewDTO preview = releaseService.publishPreview(
                request.getConfigType(), request.getConfigId(), previewRequest);
        return Result.success(governanceService.apply(preview, request));
    }

    @PostMapping("/{id}/review")
    @RequiresPermission("entity:ui-config:hotfix:review")
    public Result<UiHotfixRequestDTO> review(
            @PathVariable String id,
            @RequestBody UiHotfixReviewRequest request) {
        return Result.success(governanceService.review(id, request));
    }

    @PostMapping("/{id}/cancel")
    @RequiresPermission("entity:ui-config:hotfix")
    public Result<UiHotfixRequestDTO> cancel(
            @PathVariable String id,
            @RequestBody UiHotfixCancelRequest request) {
        return Result.success(governanceService.cancel(id, request));
    }

    @GetMapping("/{id}")
    @RequiresPermission("entity:ui-config:hotfix")
    public Result<UiHotfixRequestDTO> get(@PathVariable String id) {
        return Result.success(governanceService.get(id));
    }

    @GetMapping
    @RequiresPermission("entity:ui-config:hotfix")
    public Result<List<UiHotfixRequestDTO>> list(
            @RequestParam String configType,
            @RequestParam String configId) {
        requireObjectAccess(configType, configId);
        return Result.success(governanceService.list(configType, configId));
    }

    @PostMapping("/releases/{releaseId}/metrics")
    @RequiresPermission("entity:ui-config:hotfix:observe")
    public Result<Void> recordMetric(
            @PathVariable String releaseId,
            @RequestBody UiHotfixObservationMetricRequest request) {
        governanceService.recordReleaseMetric(releaseId, request);
        return Result.success();
    }

    private void requireObjectAccess(String configType, String configId) {
        if (UiConfigReleaseService.FORM.equalsIgnoreCase(configType)) {
            accessService.requireFormAccess(configId);
            return;
        }
        if (UiConfigReleaseService.LIST.equalsIgnoreCase(configType)) {
            accessService.requireListAccess(configId);
            return;
        }
        throw new IllegalArgumentException("配置类型只能是 FORM 或 LIST");
    }
}
