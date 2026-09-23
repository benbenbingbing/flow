package com.workflow.process.sla.runtime.api.web;

import com.workflow.core.result.PageResult;
import com.workflow.core.result.Result;
import com.workflow.core.security.RequiresPermission;
import com.workflow.process.sla.runtime.application.TaskSlaMonitorService;
import com.workflow.process.sla.runtime.infrastructure.persistence.record.ProcessTaskSla;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** SLA 监控页接口，统一要求监控权限，不提供任务状态修改能力。 */
@RestController
@RequestMapping("/api/task-sla/monitor")
@RequiresPermission("process:sla:monitor")
@RequiredArgsConstructor
public class TaskSlaMonitorController {

    private final TaskSlaMonitorService service;

    /**
     * 按状态、流程、办理人与关键字分页返回任务 SLA，页大小在服务层限制。
     *
     * @param pageNum 从 1 开始的页码，用于计算数据库偏移
     * @param pageSize 请求页大小，服务层限制在 1..200
     * @param status 可选的 SLA 总状态筛选
     * @param processKey 可选的流程定义键筛选
     * @param assignee 可选的当前办理人筛选
     * @param keyword 可选的业务关键字筛选
     * @return 分页任务 SLA 列表与总数
     */
    @GetMapping
    public Result<PageResult<ProcessTaskSla>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String processKey,
            @RequestParam(required = false) String assignee,
            @RequestParam(required = false) String keyword) {
        return Result.success(service.page(
                pageNum,
                pageSize,
                status,
                processKey,
                assignee,
                keyword));
    }

    /**
     * 返回各 SLA 状态数量，供监控概览卡片展示。
     *
     * @return 包含零值状态的计数映射
     */
    @GetMapping("/statistics")
    public Result<Map<String, Long>> statistics() {
        return Result.success(service.statistics());
    }
}
