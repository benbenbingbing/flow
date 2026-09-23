package com.workflow.entity.ui.api.web;

import com.workflow.core.result.Result;
import com.workflow.core.security.AuthenticatedApi;
import com.workflow.core.security.RequiresPermission;
import com.workflow.entity.ui.api.request.UiHotfixObservationMetricRequest;
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

/** UI HOTFIX 发布审计查询与观察窗口接口。 */
@AuthenticatedApi(objectAuthorization = true)
@RestController
@RequestMapping("/api/ui-hotfix-requests")
@RequiredArgsConstructor
public class UiHotfixGovernanceController {

    private final UiHotfixGovernanceService governanceService;
    private final UiConfigurationAccessService accessService;

    /**
     * 读取{@code result<ui}热修复请求{@code dto>}；结果供调用方展示或继续处理。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 符合条件的{@code result<ui}热修复请求{@code dto>}结果，供调用方继续处理
     */
    @GetMapping("/{id}")
    @RequiresPermission("entity:ui-config:hotfix")
    public Result<UiHotfixRequestDTO> get(@PathVariable String id) {
        return Result.success(governanceService.get(id));
    }

    /**
     * 列出界面热修复治理；查询结果供调用方展示或继续处理。
     *
     * @param configType 配置类型标识，决定后续界面热修复治理采用的处理分支
     * @param configId 配置ID，后续用于列出界面热修复治理时定位或关联目标
     * @return 符合条件的界面热修复请求结果，供调用方继续处理
     */
    @GetMapping
    @RequiresPermission("entity:ui-config:hotfix")
    public Result<List<UiHotfixRequestDTO>> list(
            @RequestParam String configType,
            @RequestParam String configId) {
        requireObjectAccess(configType, configId);
        return Result.success(governanceService.list(configType, configId));
    }

    /**
     * 记录指标；供后续追溯或审计使用。
     *
     * @param releaseId 发布版本ID，后续用于记录指标时定位或关联目标
     * @param request 本次请求，后续经校验后用于记录指标
     * @return 记录后的指标结果，供调用方继续处理
     */
    @PostMapping("/releases/{releaseId}/metrics")
    @RequiresPermission("entity:ui-config:hotfix:observe")
    public Result<Void> recordMetric(
            @PathVariable String releaseId,
            @RequestBody UiHotfixObservationMetricRequest request) {
        governanceService.recordReleaseMetric(releaseId, request);
        return Result.success();
    }

    /**
     * 校验并获取对象访问；不满足约束时阻止后续处理。
     *
     * @param configType 配置类型标识，决定后续对象访问采用的处理分支
     * @param configId 配置ID，后续用于校验并获取对象访问时定位或关联目标
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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
