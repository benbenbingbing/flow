package com.workflow.admin.setting.api.web;

import com.workflow.admin.setting.api.request.GlobalSettingRequests;
import com.workflow.admin.setting.api.response.GlobalSettingView;

import com.workflow.admin.setting.application.GlobalSettingService;
import com.workflow.core.result.Result;
import com.workflow.core.security.AuthenticatedApi;
import com.workflow.core.security.RequiresPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 全局设置 API；个人接口以登录用户为唯一归属，系统接口要求独立管理权限。 */
@RestController
@RequestMapping("/api/system/settings")
@RequiredArgsConstructor
public class GlobalSettingController {
    private final GlobalSettingService service;

    /**
     * 获取个人有效设置及个人覆盖版本，不暴露其他用户记录。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @return 处理后的{@code mine}结果，供调用方继续处理
     */
    @GetMapping("/mine/{key}")
    @AuthenticatedApi
    public Result<GlobalSettingView> mine(@PathVariable String key) {
        return Result.success(service.readMine(key));
    }

    /**
     * 保存自己的偏好；归属验证由服务层执行，不接收客户端用户 ID。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param request 本次请求，后续经校验后用于保存{@code mine}
     * @return 保存后的{@code mine}结果，供调用方继续处理
     */
    @PostMapping("/mine/{key}")
    @AuthenticatedApi(objectAuthorization = true)
    public Result<GlobalSettingView> saveMine(@PathVariable String key, @RequestBody GlobalSettingRequests.Save request) {
        return Result.success(service.saveMine(key, request));
    }

    /**
     * 删除自己的覆盖并返回继承值；请求必须携带当前个人记录版本。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param request 本次请求，后续经校验后用于处理重置{@code mine}
     * @return 处理后的重置{@code mine}结果，供调用方继续处理
     */
    @PostMapping("/mine/{key}/reset")
    @AuthenticatedApi(objectAuthorization = true)
    public Result<GlobalSettingView> resetMine(@PathVariable String key, @RequestBody GlobalSettingRequests.Reset request) {
        return Result.success(service.resetMine(key, request));
    }

    /**
     * 列出系统设置；包括尚未建立系统覆盖的注册项。
     *
     * @return 符合条件的全局设置视图结果，供调用方继续处理
     */
    @GetMapping
    @RequiresPermission(value = {"system:setting:view", "system:setting:manage"}, any = true)
    public Result<List<GlobalSettingView>> list() {
        return Result.success(service.listSystem());
    }

    /**
     * 管理员修改系统默认值，已有个人设置继续优先。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param request 本次请求，后续经校验后用于保存系统
     * @return 保存后的系统结果，供调用方继续处理
     */
    @PostMapping("/{key}")
    @RequiresPermission("system:setting:manage")
    public Result<GlobalSettingView> saveSystem(@PathVariable String key, @RequestBody GlobalSettingRequests.Save request) {
        return Result.success(service.saveSystem(key, request));
    }

    /**
     * 管理员删除系统覆盖，恢复程序默认值。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param request 本次请求，后续经校验后用于处理重置系统
     * @return 处理后的重置系统结果，供调用方继续处理
     */
    @PostMapping("/{key}/reset")
    @RequiresPermission("system:setting:manage")
    public Result<GlobalSettingView> resetSystem(@PathVariable String key, @RequestBody GlobalSettingRequests.Reset request) {
        return Result.success(service.resetSystem(key, request));
    }
}
