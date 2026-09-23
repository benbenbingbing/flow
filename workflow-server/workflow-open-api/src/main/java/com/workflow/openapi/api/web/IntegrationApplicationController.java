package com.workflow.openapi.api.web;

import com.workflow.core.result.Result;
import com.workflow.core.security.RequiresPermission;
import com.workflow.openapi.api.request.CreateIntegrationApplicationRequest;
import com.workflow.openapi.api.request.RevokeIntegrationCredentialRequest;
import com.workflow.openapi.api.request.RotateIntegrationCredentialRequest;
import com.workflow.openapi.api.request.UpdateIntegrationStatusRequest;
import com.workflow.openapi.api.response.IntegrationApplicationView;
import com.workflow.openapi.api.response.IssuedIntegrationCredentialView;
import com.workflow.openapi.application.IntegrationApplicationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供集成应用相关接口；接收请求并将校验后的参数交给应用服务处理。
 */
@RestController
@RequestMapping("/api/integration-applications")
@RequiredArgsConstructor
public class IntegrationApplicationController {

    private final IntegrationApplicationService service;

    /**
     * 列出集成应用；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的集成应用视图结果，供调用方继续处理
     */
    @GetMapping
    @RequiresPermission("system:integration:view")
    public Result<List<IntegrationApplicationView>> list() {
        return Result.success(service.list());
    }

    /**
     * 创建集成应用；结果供后续流程传递或持久化。
     *
     * @param request 本次请求，后续经校验后用于创建集成应用
     * @return 创建后的集成应用结果，供调用方继续处理
     */
    @PostMapping
    @RequiresPermission("system:integration:manage")
    public Result<IssuedIntegrationCredentialView> create(
            @Valid @RequestBody CreateIntegrationApplicationRequest request) {
        return Result.success(service.create(request));
    }

    /**
     * 更新状态；后续读取或执行将使用更新后的状态。
     *
     * @param applicationId 应用ID，后续用于更新状态时定位或关联目标
     * @param request 本次请求，后续经校验后用于更新状态
     * @return 更新后的状态结果，供调用方继续处理
     */
    @PostMapping("/{applicationId}/status")
    @RequiresPermission("system:integration:manage")
    public Result<IntegrationApplicationView> updateStatus(
            @PathVariable String applicationId,
            @Valid @RequestBody UpdateIntegrationStatusRequest request) {
        return Result.success(service.updateStatus(applicationId, request));
    }

    /**
     * 处理轮换凭据，并将结果传给后续步骤。
     *
     * @param applicationId 应用ID，后续用于处理轮换凭据时定位或关联目标
     * @param request 本次请求，后续经校验后用于处理轮换凭据
     * @return 处理后的轮换凭据结果，供调用方继续处理
     */
    @PostMapping("/{applicationId}/credentials/rotate")
    @RequiresPermission("system:integration:secret-rotate")
    public Result<IssuedIntegrationCredentialView> rotateCredential(
            @PathVariable String applicationId,
            @Valid @RequestBody
            RotateIntegrationCredentialRequest request) {
        return Result.success(service.rotateCredential(
                applicationId,
                request));
    }

    /**
     * 撤销凭据；后续读取或执行将使用更新后的状态。
     *
     * @param applicationId 应用ID，后续用于撤销凭据时定位或关联目标
     * @param request 本次请求，后续经校验后用于撤销凭据
     * @return 撤销后的凭据结果，供调用方继续处理
     */
    @PostMapping("/{applicationId}/credentials/revoke")
    @RequiresPermission("system:integration:secret-rotate")
    public Result<IntegrationApplicationView> revokeCredential(
            @PathVariable String applicationId,
            @Valid @RequestBody
            RevokeIntegrationCredentialRequest request) {
        return Result.success(service.revokeCredential(
                applicationId,
                request));
    }
}
