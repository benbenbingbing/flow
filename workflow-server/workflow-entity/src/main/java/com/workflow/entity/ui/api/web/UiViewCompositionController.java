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

    /**
     * 兼容设计器集中 API 模块使用的查询参数式集合路由。
     *
     * @param ownerType 归属方类型标识，决定后续界面视图组合采用的处理分支
     * @param ownerId 归属方ID，后续用于列出界面视图组合时定位或关联目标
     * @return 符合条件的界面视图组合结果，供调用方继续处理
     */
    @GetMapping
    public Result<List<UiViewCompositionDTO>> list(
            @RequestParam String ownerType,
            @RequestParam String ownerId) {
        return Result.success(service.list(ownerType, ownerId));
    }

    /**
     * 列出路径；查询结果供调用方展示或继续处理。
     *
     * @param ownerType 归属方类型标识，决定后续路径采用的处理分支
     * @param ownerId 归属方ID，后续用于列出路径时定位或关联目标
     * @return 符合条件的界面视图组合结果，供调用方继续处理
     */
    @GetMapping("/{ownerType}/{ownerId}")
    public Result<List<UiViewCompositionDTO>> listByPath(
            @PathVariable String ownerType,
            @PathVariable String ownerId) {
        return Result.success(service.list(ownerType, ownerId));
    }

    /**
     * 创建界面视图组合；结果供后续流程传递或持久化。
     *
     * @param ownerType 归属方类型标识，决定后续界面视图组合采用的处理分支
     * @param ownerId 归属方ID，后续用于创建界面视图组合时定位或关联目标
     * @param request 本次请求，后续经校验后用于创建界面视图组合
     * @return 创建后的界面视图组合结果，供调用方继续处理
     */
    @PostMapping("/{ownerType}/{ownerId}")
    public Result<UiViewCompositionDTO> create(
            @PathVariable String ownerType,
            @PathVariable String ownerId,
            @RequestBody UiViewCompositionSaveRequest request) {
        return Result.success(service.create(ownerType, ownerId, request));
    }

    /**
     * 兼容正文携带宿主身份的创建路由。
     *
     * @param request 本次请求，后续经校验后用于创建界面视图组合
     * @return 创建后的界面视图组合结果，供调用方继续处理
     */
    @PostMapping
    public Result<UiViewCompositionDTO> create(
            @RequestBody UiViewCompositionSaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("关联内容保存请求不能为空");
        }
        return Result.success(service.create(
                request.getOwnerType(), request.getOwnerId(), request));
    }

    /**
     * 更新界面视图组合；后续读取或执行将使用更新后的状态。
     *
     * @param ownerType 归属方类型标识，决定后续界面视图组合采用的处理分支
     * @param ownerId 归属方ID，后续用于更新界面视图组合时定位或关联目标
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param request 本次请求，后续经校验后用于更新界面视图组合
     * @return 更新后的界面视图组合结果，供调用方继续处理
     */
    @PostMapping("/{ownerType}/{ownerId}/{id}/update")
    public Result<UiViewCompositionDTO> update(
            @PathVariable String ownerType,
            @PathVariable String ownerId,
            @PathVariable String id,
            @RequestBody UiViewCompositionSaveRequest request) {
        return Result.success(service.update(
                ownerType, ownerId, id, request));
    }

    /**
     * 兼容正文携带宿主身份的更新路由。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param request 本次请求，后续经校验后用于更新界面视图组合
     * @return 更新后的界面视图组合结果，供调用方继续处理
     */
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

    /**
     * 删除界面视图组合；后续读取或执行将使用更新后的状态。
     *
     * @param ownerType 归属方类型标识，决定后续界面视图组合采用的处理分支
     * @param ownerId 归属方ID，后续用于删除界面视图组合时定位或关联目标
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param request 本次请求，后续经校验后用于删除界面视图组合
     * @return 删除后的界面视图组合结果，供调用方继续处理
     */
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

    /**
     * 兼容正文携带宿主身份的删除路由。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param request 本次请求，后续经校验后用于删除界面视图组合
     * @return 删除后的界面视图组合结果，供调用方继续处理
     */
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

    /**
     * 校验界面视图组合；不满足约束时阻止后续处理。
     *
     * @param ownerType 归属方类型标识，决定后续界面视图组合采用的处理分支
     * @param ownerId 归属方ID，后续用于校验界面视图组合时定位或关联目标
     * @param request 本次请求，后续经校验后用于校验界面视图组合
     * @return 校验后的界面视图组合结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 兼容正文携带宿主身份的校验路由。
     *
     * @param request 本次请求，后续经校验后用于校验界面视图组合
     * @return 校验后的界面视图组合结果，供调用方继续处理
     */
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

    /**
     * 使用正文中的宿主身份和未保存配置执行真实数据测试。
     *
     * @param request 本次请求，后续经校验后用于处理测试
     * @return 处理后的测试结果，供调用方继续处理
     */
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

    /**
     * 路径式真实数据测试资源。
     *
     * @param ownerType 归属方类型标识，决定后续测试路径采用的处理分支
     * @param ownerId 归属方ID，后续用于处理测试路径时定位或关联目标
     * @param request 本次请求，后续经校验后用于处理测试路径
     * @return 处理后的测试路径结果，供调用方继续处理
     */
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
