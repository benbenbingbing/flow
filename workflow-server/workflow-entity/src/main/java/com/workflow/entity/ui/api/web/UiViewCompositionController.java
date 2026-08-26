package com.workflow.entity.ui.api.web;

import com.workflow.core.result.Result;
import com.workflow.core.security.AuthenticatedApi;
import com.workflow.entity.ui.api.request.UiViewCompositionDeleteRequest;
import com.workflow.entity.ui.api.request.UiViewCompositionSaveRequest;
import com.workflow.entity.ui.api.request.UiViewCompositionValidateRequest;
import com.workflow.entity.ui.api.response.UiViewCompositionDTO;
import com.workflow.entity.ui.api.response.UiViewCompositionMutationResultDTO;
import com.workflow.entity.ui.api.response.UiViewCompositionTestDTO;
import com.workflow.entity.ui.api.response.UiViewCompositionValidationDTO;
import com.workflow.entity.ui.application.UiViewCompositionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 表单和列表“关联内容”设计态资源控制器。
 *
 * <p>ownerType/ownerId 固定在资源路径中，避免请求正文将关联内容写入另一个
 * 宿主；更新和删除使用资源自身 revision 进行乐观并发控制。</p>
 */
@AuthenticatedApi(objectAuthorization = true)
@RestController
@RequestMapping("/api/ui-view-compositions")
@RequiredArgsConstructor
public class UiViewCompositionController {

    private final UiViewCompositionService service;

    /** 兼容设计器集中 API 模块使用的查询参数式集合路由。 */
    @GetMapping
    public Result<List<UiViewCompositionDTO>> list(
            @RequestParam String ownerType,
            @RequestParam String ownerId) {
        return Result.success(service.list(ownerType, ownerId));
    }

    @GetMapping("/{ownerType}/{ownerId}")
    public Result<List<UiViewCompositionDTO>> listByPath(
            @PathVariable String ownerType,
            @PathVariable String ownerId) {
        return Result.success(service.list(ownerType, ownerId));
    }

    @PostMapping("/{ownerType}/{ownerId}")
    public Result<UiViewCompositionDTO> create(
            @PathVariable String ownerType,
            @PathVariable String ownerId,
            @RequestBody UiViewCompositionSaveRequest request) {
        return Result.success(service.create(ownerType, ownerId, request));
    }

    /** 兼容正文携带宿主身份的创建路由。 */
    @PostMapping
    public Result<UiViewCompositionDTO> create(
            @RequestBody UiViewCompositionSaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("关联内容保存请求不能为空");
        }
        return Result.success(service.create(
                request.getOwnerType(), request.getOwnerId(), request));
    }

    @PostMapping("/{ownerType}/{ownerId}/{id}/update")
    public Result<UiViewCompositionDTO> update(
            @PathVariable String ownerType,
            @PathVariable String ownerId,
            @PathVariable String id,
            @RequestBody UiViewCompositionSaveRequest request) {
        return Result.success(service.update(
                ownerType, ownerId, id, request));
    }

    /** 兼容正文携带宿主身份的更新路由。 */
    @PostMapping("/{id}/update")
    public Result<UiViewCompositionDTO> update(
            @PathVariable String id,
            @RequestBody UiViewCompositionSaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("关联内容保存请求不能为空");
        }
        return Result.success(service.update(
                request.getOwnerType(), request.getOwnerId(), id, request));
    }

    @PostMapping("/{ownerType}/{ownerId}/{id}/delete")
    public Result<UiViewCompositionMutationResultDTO> delete(
            @PathVariable String ownerType,
            @PathVariable String ownerId,
            @PathVariable String id,
            @RequestBody UiViewCompositionDeleteRequest request) {
        return Result.success(service.delete(
                ownerType,
                ownerId,
                id,
                request == null ? null : request.getExpectedRevision(),
                request == null ? null : request.getExpectedOwnerRevision()));
    }

    /** 兼容正文携带宿主身份的删除路由。 */
    @PostMapping("/{id}/delete")
    public Result<UiViewCompositionMutationResultDTO> delete(
            @PathVariable String id,
            @RequestBody UiViewCompositionDeleteRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("关联内容删除请求不能为空");
        }
        if (request.getOwnerType() == null || request.getOwnerId() == null) {
            return Result.success(service.delete(
                    id,
                    request.getExpectedRevision(),
                    request.getExpectedOwnerRevision()));
        }
        return Result.success(service.delete(
                request.getOwnerType(), request.getOwnerId(), id,
                request.getExpectedRevision(),
                request.getExpectedOwnerRevision()));
    }

    @PostMapping("/{ownerType}/{ownerId}/validate")
    public Result<UiViewCompositionValidationDTO> validate(
            @PathVariable String ownerType,
            @PathVariable String ownerId,
            @RequestBody UiViewCompositionValidateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("关联内容校验请求不能为空");
        }
        return Result.success(service.validate(
                ownerType, ownerId, request.getConfig()));
    }

    /** 兼容正文携带宿主身份的校验路由。 */
    @PostMapping("/validate")
    public Result<UiViewCompositionValidationDTO> validate(
            @RequestBody UiViewCompositionValidateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("关联内容校验请求不能为空");
        }
        return Result.success(service.validate(
                request.getOwnerType(),
                request.getOwnerId(),
                request.getConfig()));
    }

    /** 使用正文中的宿主身份和未保存配置执行真实数据测试。 */
    @PostMapping("/test")
    public Result<UiViewCompositionTestDTO> test(
            @RequestBody UiViewCompositionValidateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("关联内容测试请求不能为空");
        }
        return Result.success(service.test(
                request.getOwnerType(),
                request.getOwnerId(),
                request.getConfig(),
                request.getSourceRecordId()));
    }

    /** 路径式真实数据测试资源。 */
    @PostMapping("/{ownerType}/{ownerId}/test")
    public Result<UiViewCompositionTestDTO> testByPath(
            @PathVariable String ownerType,
            @PathVariable String ownerId,
            @RequestBody UiViewCompositionValidateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("关联内容测试请求不能为空");
        }
        return Result.success(service.test(
                ownerType,
                ownerId,
                request.getConfig(),
                request.getSourceRecordId()));
    }
}
