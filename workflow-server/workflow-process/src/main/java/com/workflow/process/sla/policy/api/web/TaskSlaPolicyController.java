package com.workflow.process.sla.policy.api.web;

import com.workflow.core.result.Result;
import com.workflow.core.security.RequiresPermission;
import com.workflow.process.sla.policy.api.request.TaskSlaPolicySaveRequest;
import com.workflow.process.sla.policy.api.response.TaskSlaPolicyDTO;
import com.workflow.process.sla.policy.application.TaskSlaPolicyService;
import com.workflow.process.sla.policy.infrastructure.persistence.record.TaskSlaPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** SLA 策略管理入口；读取、草稿维护和发布分别受相应权限约束。 */
@RestController
@RequestMapping("/api/task-sla-policies")
@RequiredArgsConstructor
@RequiresPermission("process:sla-policy:view")
public class TaskSlaPolicyController {

    private final TaskSlaPolicyService service;

    /**
     * 列出未删除策略的所有版本，供管理端查看发布历史。
     *
     * @return 按策略编码和版本排列的列表
     */
    @GetMapping
    public Result<List<TaskSlaPolicy>> list() {
        return Result.success(service.list());
    }

    /**
     * 只返回已发布策略，供流程配置页选择可绑定版本。
     *
     * @return 当前可绑定的发布策略列表
     */
    @GetMapping("/published")
    public Result<List<TaskSlaPolicy>> published() {
        return Result.success(service.published());
    }

    /**
     * 按版本 ID 读取策略和升级步骤快照。
     *
     * @param id 策略版本 ID
     * @return 策略及其可供回显的升级步骤
     */
    @GetMapping("/{id}")
    public Result<TaskSlaPolicyDTO> get(@PathVariable String id) {
        return Result.success(service.get(id));
    }

    /**
     * 从请求创建草稿策略；升级步骤和计时口径由服务层统一校验。
     *
     * @param request 新策略的时限、暂停规则和升级步骤
     * @return 保存后的草稿详情
     */
    @PostMapping
    @RequiresPermission("process:sla-policy:manage")
    public Result<TaskSlaPolicyDTO> create(
            @RequestBody TaskSlaPolicySaveRequest request) {
        return Result.success(service.save(null, request));
    }

    /**
     * 更新策略；已发布版本由服务层另建草稿以保留旧快照。
     *
     * @param id 待编辑的策略版本 ID
     * @param request 用于保存草稿的完整策略配置
     * @return 更新或新建的草稿详情
     */
    @PostMapping("/{id}/update")
    @RequiresPermission("process:sla-policy:manage")
    public Result<TaskSlaPolicyDTO> update(
            @PathVariable String id,
            @RequestBody TaskSlaPolicySaveRequest request) {
        return Result.success(service.save(id, request));
    }

    /**
     * 发布已校验的草稿，后续流程绑定读取新的策略快照。
     *
     * @param id 待发布的草稿版本 ID
     * @return 发布后的策略详情
     */
    @PostMapping("/{id}/publish")
    @RequiresPermission("process:sla-policy:publish")
    public Result<TaskSlaPolicyDTO> publish(@PathVariable String id) {
        return Result.success(service.publish(id));
    }
}
