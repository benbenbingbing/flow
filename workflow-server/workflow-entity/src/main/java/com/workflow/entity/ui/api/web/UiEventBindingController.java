package com.workflow.entity.ui.api.web;

import com.workflow.core.result.Result;
import com.workflow.core.security.AuthenticatedApi;
import com.workflow.entity.ui.api.request.UiExtensionDeleteRequest;
import com.workflow.entity.ui.api.request.UiEventBindingSaveRequest;
import com.workflow.entity.ui.application.UiEventBindingService;
import com.workflow.entity.ui.infrastructure.persistence.record.UiEventBinding;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 统一接口事件绑定管理与运行控制器。
 */
@AuthenticatedApi(objectAuthorization = true)
@RestController
@RequestMapping("/api/ui-event-bindings")
@RequiredArgsConstructor
public class UiEventBindingController {

    private final UiEventBindingService bindingService;

    /**
     * 处理目录，并将结果传给后续步骤。
     *
     * @return 处理后的目录结果，供调用方继续处理
     */
    @GetMapping("/catalog")
    public Result<Map<String, Object>> catalog() {
        return Result.success(bindingService.catalog());
    }

    /**
     * 列出界面事件绑定；查询结果供调用方展示或继续处理。
     *
     * @param ownerType 归属方类型标识，决定后续界面事件绑定采用的处理分支
     * @param ownerId 归属方ID，后续用于列出界面事件绑定时定位或关联目标
     * @return 符合条件的界面事件绑定结果，供调用方继续处理
     */
    @GetMapping
    public Result<List<UiEventBinding>> list(
            @RequestParam String ownerType,
            @RequestParam String ownerId) {
        return Result.success(bindingService.list(ownerType, ownerId));
    }

    /**
     * 解析草稿；输出作为后续校验或处理的输入。
     *
     * @param ownerType 归属方类型标识，决定后续草稿采用的处理分支
     * @param ownerId 归属方ID，后续用于解析草稿时定位或关联目标
     * @param eventCode 事件编码，后续用于解析草稿时定位或关联目标
     * @return 解析后的草稿结果，供调用方继续处理
     */
    @GetMapping("/resolved-draft")
    public Result<Map<String, Object>> resolveDraft(
            @RequestParam String ownerType,
            @RequestParam String ownerId,
            @RequestParam String eventCode) {
        return Result.success(bindingService.resolveDraft(
                ownerType,
                ownerId,
                eventCode));
    }

    /**
     * 创建界面事件绑定；结果供后续流程传递或持久化。
     *
     * @param request 本次请求，后续经校验后用于创建界面事件绑定
     * @return 创建后的界面事件绑定结果，供调用方继续处理
     */
    @PostMapping
    public Result<UiEventBinding> create(
            @RequestBody UiEventBindingSaveRequest request) {
        request.setId(null);
        return Result.success(bindingService.save(request));
    }

    /**
     * 更新界面事件绑定；后续读取或执行将使用更新后的状态。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param request 本次请求，后续经校验后用于更新界面事件绑定
     * @return 更新后的界面事件绑定结果，供调用方继续处理
     */
    @PostMapping("/{id}/update")
    public Result<UiEventBinding> update(
            @PathVariable String id,
            @RequestBody UiEventBindingSaveRequest request) {
        request.setId(id);
        return Result.success(bindingService.save(request));
    }

    /**
     * 删除界面事件绑定；后续读取或执行将使用更新后的状态。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param request 本次请求，后续经校验后用于删除界面事件绑定
     * @return 删除后的界面事件绑定结果，供调用方继续处理
     */
    @PostMapping("/{id}/delete")
    public Result<Void> delete(
            @PathVariable String id,
            @RequestBody UiExtensionDeleteRequest request) {
        bindingService.delete(id, request.getExpectedRevision());
        return Result.success();
    }

}
