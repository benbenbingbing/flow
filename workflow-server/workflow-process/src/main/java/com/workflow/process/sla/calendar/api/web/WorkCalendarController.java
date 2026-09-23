package com.workflow.process.sla.calendar.api.web;

import com.workflow.core.result.Result;
import com.workflow.core.security.RequiresPermission;
import com.workflow.process.sla.calendar.api.request.WorkCalendarSaveRequest;
import com.workflow.process.sla.calendar.api.request.WorkCalendarSimulationRequest;
import com.workflow.process.sla.calendar.api.response.WorkCalendarDTO;
import com.workflow.process.sla.calendar.application.WorkCalendarService;
import com.workflow.process.sla.calendar.infrastructure.persistence.record.WorkCalendar;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/** 工作日历管理入口；查看权限用于读取与模拟，变更和发布由各方法单独授权。 */
@RestController
@RequestMapping("/api/work-calendars")
@RequiredArgsConstructor
@RequiresPermission("system:work-calendar:view")
public class WorkCalendarController {

    private final WorkCalendarService service;

    /**
     * 列出各日历版本供管理端选择，不直接提供任务运行时计算。
     *
     * @return 包含草稿和发布版本的列表
     */
    @GetMapping
    public Result<List<WorkCalendar>> list() {
        return Result.success(service.list());
    }

    /**
     * 按版本 ID 读取日历详情及范围绑定供编辑回显。
     *
     * @param id 日历版本 ID，由详情路由提供
     * @return 日历元数据、工作时段快照和适用范围绑定
     */
    @GetMapping("/{id}")
    public Result<WorkCalendarDTO> get(@PathVariable String id) {
        return Result.success(service.get(id));
    }

    /**
     * 新增日历草稿；工作时段和特殊日期在服务层统一验证。
     *
     * @param request 新草稿的基础信息、时段、特殊日期与绑定
     * @return 保存后的草稿详情，供编辑页继续操作
     */
    @PostMapping
    @RequiresPermission("system:work-calendar:manage")
    public Result<WorkCalendarDTO> create(
            @RequestBody WorkCalendarSaveRequest request) {
        return Result.success(service.save(null, request));
    }

    /**
     * 更新指定版本；已发布版本由服务层创建新草稿，避免改变在途任务快照。
     *
     * @param id 待编辑的日历版本 ID
     * @param request 用于重建该版本子配置的完整草稿数据
     * @return 更新或新建的草稿详情
     */
    @PostMapping("/{id}/update")
    @RequiresPermission("system:work-calendar:manage")
    public Result<WorkCalendarDTO> update(
            @PathVariable String id,
            @RequestBody WorkCalendarSaveRequest request) {
        return Result.success(service.save(id, request));
    }

    /**
     * 发布日历版本并更新默认日历及迁移资产。
     *
     * @param id 待发布的日历版本 ID
     * @return 发布后的日历详情
     */
    @PostMapping("/{id}/publish")
    @RequiresPermission("system:work-calendar:publish")
    public Result<WorkCalendarDTO> publish(@PathVariable String id) {
        return Result.success(service.publish(id));
    }

    /**
     * 停用非默认日历版本，供管理端下线不再使用的配置。
     *
     * @param id 待停用的日历版本 ID
     * @return 操作成功响应
     */
    @PostMapping("/{id}/disable")
    @RequiresPermission("system:work-calendar:manage")
    public Result<Void> disable(@PathVariable String id) {
        service.disable(id);
        return Result.success();
    }

    /**
     * 用指定版本模拟工作时间截止时刻，request.startAt 和 minutes 仅用于预演；
     * 返回 UTC dueAt 供管理端检查时区和跨工作日结果，不产生任务事件。
     *
     * @param id 用于预演的日历版本 ID
     * @param request 起点及累计分钟数，不写入任务状态
     * @return 含 UTC dueAt 的预演结果
     * @throws IllegalArgumentException 起点缺失或分钟数为负时抛出
     */
    @PostMapping("/{id}/simulate")
    public Result<Map<String, Object>> simulate(
            @PathVariable String id,
            @RequestBody WorkCalendarSimulationRequest request) {
        if (request == null
                || request.startAt() == null
                || request.minutes() == null
                || request.minutes() < 0) {
            throw new IllegalArgumentException(
                    "模拟开始时间和分钟数不能为空");
        }
        OffsetDateTime dueAt = service.simulate(
                        id,
                        request.startAt().toInstant(),
                        request.minutes())
                .atOffset(ZoneOffset.UTC);
        return Result.success(Map.of("dueAt", dueAt));
    }
}
