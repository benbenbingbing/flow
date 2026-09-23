package com.workflow.entity.ui.api.web;

import com.workflow.core.security.AuthenticatedApi;

import com.workflow.core.result.Result;
import com.workflow.core.security.RequiresPermission;
import com.workflow.entity.ui.api.request.UiExtensionDeleteRequest;
import com.workflow.entity.ui.api.request.UiExtensionDefinitionSaveRequest;
import com.workflow.entity.ui.api.request.UiExtensionExecuteRequest;
import com.workflow.entity.ui.api.response.UiAvailableInterface;
import com.workflow.entity.ui.infrastructure.persistence.record.UiExtensionDefinition;
import com.workflow.entity.ui.application.UiAvailableInterfaceService;
import com.workflow.entity.ui.application.UiConfigurationAccessService;
import com.workflow.entity.ui.application.UiExtensionDefinitionService;
import com.workflow.entity.ui.application.UiInterfaceExtensionService;
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
 * UI 扩展定义管理控制器。
 * <p>提供扩展定义的查询、新增、更新接口，所有写操作需全局配置权限。
 */
@AuthenticatedApi(objectAuthorization = true)
@RestController
@RequestMapping("/api/ui-extensions")
@RequiredArgsConstructor
public class UiExtensionDefinitionController {

    private final UiExtensionDefinitionService service;
    private final UiInterfaceExtensionService interfaceService;
    private final UiAvailableInterfaceService availableInterfaceService;
    private final UiConfigurationAccessService accessService;

    /**
     * 返回接口实现、上下文、用途与 Provider 目录。
     *
     * @return 处理后的目录结果，供调用方继续处理
     */
    @RequiresPermission("system:extension:list")
    @GetMapping("/catalog")
    public Result<Map<String, Object>> catalog() {
        accessService.requireGlobalConfigurationAccess();
        return Result.success(interfaceService.catalog());
    }

    /**
     * 查询扩展定义列表。GET /api/ui-extensions
     *
     * @param extensionType 扩展类型（可选过滤）
     * @param extensionKey  扩展标识（可选过滤）
     * @param status        状态（可选过滤）
     * @param scopeType 作用域类型标识，决定后续界面扩展定义采用的处理分支
     * @param scopeId 作用域ID，后续用于列出界面扩展定义时定位或关联目标
     * @param implementationType 实现类型标识，决定后续界面扩展定义采用的处理分支
     * @return 匹配的扩展定义列表
     */
    @GetMapping
    @RequiresPermission("system:extension:list")
    public Result<List<UiExtensionDefinition>> list(
            @RequestParam(required = false) String extensionType,
            @RequestParam(required = false) String extensionKey,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String scopeType,
            @RequestParam(required = false) String scopeId,
            @RequestParam(required = false) String implementationType) {
        return Result.success(service.list(
                extensionType, extensionKey, status,
                scopeType, scopeId, implementationType));
    }

    /**
     * 查询一个绑定位置可选择的完整接口扩展。
     *
     * @param ownerType 归属方类型标识，决定后续可用{@code interfaces}采用的处理分支
     * @param ownerId 归属方ID，后续用于处理可用{@code interfaces}时定位或关联目标
     * @param bindingCode 绑定编码，后续用于处理可用{@code interfaces}时定位或关联目标
     * @return 处理后的可用{@code interfaces}结果，供调用方继续处理
     */
    @GetMapping("/available-interfaces")
    public Result<List<UiAvailableInterface>> availableInterfaces(
            @RequestParam String ownerType,
            @RequestParam String ownerId,
            @RequestParam String bindingCode) {
        return Result.success(availableInterfaceService.available(
                ownerType, ownerId, bindingCode));
    }

    /**
     * 新增扩展定义。POST /api/ui-extensions
     *
     * @param request 扩展定义保存请求（id 将被忽略并置空）
     * @return 保存后的扩展定义
     */
    @PostMapping
    @RequiresPermission("system:extension:update")
    public Result<UiExtensionDefinition> create(
            @RequestBody UiExtensionDefinitionSaveRequest request) {
        accessService.requireGlobalConfigurationAccess();
        request.setId(null);
        return Result.success(saveByExtensionType(request));
    }

    /**
     * 更新扩展定义。POST /api/ui-extensions/{id}
     *
     * @param id      扩展定义ID
     * @param request 扩展定义保存请求（id 将被覆盖为路径 id）
     * @return 保存后的扩展定义
     */
    @PostMapping("/{id}")
    @RequiresPermission("system:extension:update")
    public Result<UiExtensionDefinition> update(
            @PathVariable String id,
            @RequestBody UiExtensionDefinitionSaveRequest request) {
        accessService.requireGlobalConfigurationAccess();
        request.setId(id);
        return Result.success(saveByExtensionType(request));
    }

    /**
     * 删除一条接口扩展；仍被可执行发布版本引用时拒绝删除。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param request 本次请求，后续经校验后用于删除界面扩展定义
     * @return 删除后的界面扩展定义结果，供调用方继续处理
     */
    @PostMapping("/{id}/delete")
    @RequiresPermission("system:extension:update")
    public Result<Void> delete(
            @PathVariable String id,
            @RequestBody UiExtensionDeleteRequest request) {
        accessService.requireGlobalConfigurationAccess();
        interfaceService.delete(id, request.getExpectedRevision());
        return Result.success();
    }

    /**
     * 在管理上下文中调试一条完整接口扩展。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param request 本次请求，后续经校验后用于处理预览
     * @return 处理后的预览结果，供调用方继续处理
     */
    @PostMapping("/{id}/preview")
    @RequiresPermission("system:extension:test")
    public Result<Object> preview(
            @PathVariable String id,
            @RequestBody UiExtensionExecuteRequest request) {
        accessService.requireGlobalConfigurationAccess();
        return Result.success(interfaceService.preview(id, request));
    }

    /**
     * 根据扩展类型把写命令交给对应的应用服务。
     *
     * <p>通用 UI 组件目录只负责 FORM/NODE/FIELD/LIST；INTERFACE 需要专门的
     * 实现、作用域和执行契约校验。分派保留在统一 HTTP 入口，可避免目录查询服务
     * 反向依赖接口运行服务，进而破坏发布链路的单向依赖。</p>
     *
     * @param request 本次请求，后续经校验后用于保存扩展类型
     * @return 保存后的扩展类型结果，供调用方继续处理
     */
    private UiExtensionDefinition saveByExtensionType(
            UiExtensionDefinitionSaveRequest request) {
        if (request != null
                && request.getExtensionType() != null
                && "INTERFACE".equalsIgnoreCase(
                        request.getExtensionType().trim())) {
            return interfaceService.save(request);
        }
        return service.save(request);
    }
}
