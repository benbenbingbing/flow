package com.workflow.process.sla.runtime.application;

import com.workflow.process.sla.runtime.application.model.PublishedTaskSlaConfig;

import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.process.sla.calendar.application.model.WorkCalendarResolutionSnapshot;
import com.workflow.process.sla.calendar.application.model.WorkCalendarSnapshot;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Map;

/**
 * 为任务 SLA 从已发布的日历解析快照选择实际工作日历。
 * 选择顺序可依业务部门、发起人部门/组织回退到默认日历，运行时不读取可变配置。
 */
@Component
@RequiredArgsConstructor
public class TaskSlaCalendarResolver {

    private final EntityDataDynamicService entityDataService;
    private final HistoryService historyService;
    private final SysUserMapper userMapper;

    /**
     * 根据发布策略和流程上下文解析本次任务的日历，供截止时间计算器使用。
     *
     * @param config 发布 SLA 配置，包含固定日历或可解析的作用域快照
     * @param processInstanceId 用于查找发起人部门及组织的流程实例 ID
     * @param entityCode 业务实体编码，业务部门字段不在变量中时用于查询记录
     * @param entityDataId 业务记录 ID，与 entityCode 一起定位业务部门
     * @param variables 当前流程变量，优先提供业务部门字段值
     * @return 本次任务使用的工作日历快照；缺少默认快照时抛错
     * @throws IllegalStateException 发布配置缺少解析快照或默认日历时抛出
     */
    public WorkCalendarSnapshot resolve(
            PublishedTaskSlaConfig config,
            String processInstanceId,
            String entityCode,
            String entityDataId,
            Map<String, Object> variables) {
        if (config.calendarSnapshot() != null) {
            return config.calendarSnapshot();
        }
        WorkCalendarResolutionSnapshot resolution =
                config.calendarResolutionSnapshot();
        if (resolution == null) {
            throw new IllegalStateException("SLA工作日历快照缺失");
        }
        // 用统一 UTC 日期筛选绑定生效区间，避免应用节点时区不同导致同一任务选到不同日历。
        LocalDate effectiveDate = LocalDate.now(ZoneOffset.UTC);
        String source = normalize(config.calendarSource());
        if ("BUSINESS_DEPT".equals(source)) {
            String departmentId = businessDepartment(
                    config.businessFieldCode(),
                    entityCode,
                    entityDataId,
                    variables);
            WorkCalendarSnapshot business = resolveScope(
                    resolution,
                    "DEPARTMENT",
                    departmentId,
                    effectiveDate);
            if (business != null) {
                return business;
            }
            WorkCalendarSnapshot starter = resolveStarter(
                    resolution,
                    processInstanceId,
                    effectiveDate);
            return starter != null ? starter : defaultCalendar(resolution);
        }
        if ("STARTER_DEPT".equals(source)) {
            WorkCalendarSnapshot starter = resolveStarter(
                    resolution,
                    processInstanceId,
                    effectiveDate);
            return starter != null ? starter : defaultCalendar(resolution);
        }
        return defaultCalendar(resolution);
    }

    /**
     * 优先从流程变量取部门，缺失时查询业务记录；读取失败交给上层回退规则。
     *
     * @param fieldCode 发布配置指定的业务部门字段编码
     * @param entityCode 查询业务记录所需的实体编码
     * @param entityDataId 查询业务记录所需的记录 ID
     * @param variables 流程变量，避免已有部门值时额外访问实体表
     * @return 可用于作用域匹配的部门 ID；无法取得时返回 null
     */
    private String businessDepartment(
            String fieldCode,
            String entityCode,
            String entityDataId,
            Map<String, Object> variables) {
        Object value = variables == null || !StringUtils.hasText(fieldCode)
                ? null
                : variables.get(fieldCode);
        if (value == null
                && StringUtils.hasText(entityCode)
                && StringUtils.hasText(entityDataId)
                && StringUtils.hasText(fieldCode)) {
            try {
                EntityDataDTO record =
                        entityDataService.findById(entityCode, entityDataId);
                value = record.getData() == null
                        ? null
                        : record.getData().get(fieldCode);
                if (value == null && "dept_id".equalsIgnoreCase(fieldCode)) {
                    value = record.getDeptId();
                }
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return identifier(value);
    }

    /**
     * 发起人部门优先于组织；历史流程保存的发起人 ID 兼容用户 ID 与用户名。
     *
     * @param resolution 发布时固定的日历及作用域绑定
     * @param processInstanceId 查询历史发起人的流程实例 ID
     * @param effectiveDate 用于筛选绑定生效区间的 UTC 日期
     * @return 发起人部门或组织日历；无法匹配时返回 null 供默认回退
     */
    private WorkCalendarSnapshot resolveStarter(
            WorkCalendarResolutionSnapshot resolution,
            String processInstanceId,
            LocalDate effectiveDate) {
        HistoricProcessInstance process = historyService
                .createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (process == null || !StringUtils.hasText(process.getStartUserId())) {
            return null;
        }
        SysUser user = userMapper.selectById(process.getStartUserId());
        if (user == null) {
            user = userMapper.selectByUsername(process.getStartUserId());
        }
        if (user == null) {
            return null;
        }
        WorkCalendarSnapshot department = resolveScope(
                resolution,
                "DEPARTMENT",
                user.getDeptId(),
                effectiveDate);
        if (department != null) {
            return department;
        }
        return resolveScope(
                resolution,
                "ORGANIZATION",
                user.getOrgId(),
                effectiveDate);
    }

    /**
     * 仅选择当天生效的同作用域绑定，按优先级从高到低取首个存在于快照的日历。
     *
     * @param resolution 只包含已发布日历的解析快照
     * @param scopeType 部门或组织类型，限定候选绑定
     * @param scopeKey 当前部门或组织 ID
     * @param effectiveDate 筛选绑定生效起止日期
     * @return 命中的日历；无有效绑定时返回 null
     */
    private WorkCalendarSnapshot resolveScope(
            WorkCalendarResolutionSnapshot resolution,
            String scopeType,
            String scopeKey,
            LocalDate effectiveDate) {
        if (!StringUtils.hasText(scopeKey)) {
            return null;
        }
        return resolution.bindings().stream()
                .filter(binding -> scopeType.equalsIgnoreCase(
                        binding.scopeType()))
                .filter(binding -> scopeKey.equals(binding.scopeKey()))
                .filter(binding -> binding.effectiveFrom() == null
                        || !effectiveDate.isBefore(binding.effectiveFrom()))
                .filter(binding -> binding.effectiveTo() == null
                        || !effectiveDate.isAfter(binding.effectiveTo()))
                .sorted(Comparator.comparingInt(
                        WorkCalendarResolutionSnapshot.Binding::priority)
                        .reversed())
                .map(binding ->
                        resolution.calendars().get(binding.calendarCode()))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * 回退到发布快照指定的默认日历；缺失视为发布数据损坏。
     *
     * @param resolution 含默认日历编码及日历映射的发布快照
     * @return 默认日历，供任务 SLA 截止时间计算
     * @throws IllegalStateException 默认编码未映射到日历时抛出
     */
    private WorkCalendarSnapshot defaultCalendar(
            WorkCalendarResolutionSnapshot resolution) {
        WorkCalendarSnapshot calendar =
                resolution.calendars().get(resolution.defaultCalendarCode());
        if (calendar == null) {
            throw new IllegalStateException("默认工作日历快照不存在");
        }
        return calendar;
    }

    /**
     * 兼容选择器对象与纯 ID 字符串，统一得到部门作用域键。
     *
     * @param value 流程变量或业务记录中的部门字段值
     * @return 用于绑定匹配的 ID；空值返回 null
     */
    private String identifier(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object id = map.get("id");
            if (id == null) {
                id = map.get("value");
            }
            return id == null ? null : String.valueOf(id);
        }
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 空来源按系统默认日历处理，供 resolve 的分支选择使用。
     *
     * @param value 发布配置中的来源类型
     * @return 标准大写来源码
     */
    private String normalize(String value) {
        return value == null
                ? "SYSTEM_DEFAULT"
                : value.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
