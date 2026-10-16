package com.workflow.process.assignment.api.web;

import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.core.error.ForbiddenException;
import com.workflow.core.result.ApiResponse;
import com.workflow.core.result.PageResult;
import com.workflow.core.security.AuthenticatedApi;
import com.workflow.process.assignment.api.request.AssigneeIncidentHandleRequest;
import com.workflow.process.assignment.application.AssigneeIncidentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 空办理人事件管理与处置 API。 */
@AuthenticatedApi(objectAuthorization = true)
@RestController
@RequestMapping("/api/assignee-incidents")
@RequiredArgsConstructor
public class AssigneeIncidentController {

    private final AssigneeIncidentService incidentService;

    /**
     * 分页查询办理人异常事件，返回当前页和筛选后的总数。
     *
     * @param status 可选事件状态，未传时查询全部状态
     * @param pageNum 从 1 开始的页码
     * @param pageSize 每页条数，默认 20，最多 100
     * @return 当前页记录与总数，供管理页面分页展示
     */
    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        require("process:assignee-incident:list");
        return ApiResponse.success(incidentService.list(status, pageNum, pageSize));
    }

    /**
     * 处理指标集合，并将结果传给后续步骤。
     *
     * @return 处理后的指标集合结果，供调用方继续处理
     */
    @GetMapping("/metrics")
    public ApiResponse<Map<String, Object>> metrics() {
        require("process:assignee-incident:list");
        return ApiResponse.success(incidentService.metrics());
    }

    /**
     * 处理详情，并将结果传给后续步骤。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 处理后的详情结果，供调用方继续处理
     */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String id) {
        require("process:assignee-incident:list");
        return ApiResponse.success(incidentService.detail(id));
    }

    /**
     * 处理办理人异常事件，并将结果传给后续步骤。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param request 本次请求，后续经校验后用于处理办理人异常事件
     * @return 处理后的办理人异常事件结果，供调用方继续处理
     */
    @PostMapping("/{id}/handle")
    public ApiResponse<Map<String, Object>> handle(
            @PathVariable String id,
            @Valid @RequestBody AssigneeIncidentHandleRequest request) {
        require("process:assignee-incident:handle");
        return ApiResponse.success(incidentService.handle(id, request));
    }

    /**
     * 校验并获取办理人异常事件；不满足约束时阻止后续处理。
     *
     * @param permission 数据访问权限，后续与查询条件合并以限制可见记录
     * @throws ForbiddenException 当前用户缺少所需访问权限时抛出
     */
    private void require(String permission) {
        if (!PermissionUtil.hasPermission(permission)) {
            throw new ForbiddenException("没有权限处置空办理人事件：" + permission);
        }
    }
}
