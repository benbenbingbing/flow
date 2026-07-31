package com.workflow.process.sla.runtime.application;

import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.process.sla.calendar.application.WorkCalendarResolutionSnapshot;
import com.workflow.process.sla.calendar.application.WorkCalendarSnapshot;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TaskSlaCalendarResolver {

    private final EntityDataDynamicService entityDataService;
    private final HistoryService historyService;
    private final SysUserMapper userMapper;

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

    private WorkCalendarSnapshot defaultCalendar(
            WorkCalendarResolutionSnapshot resolution) {
        WorkCalendarSnapshot calendar =
                resolution.calendars().get(resolution.defaultCalendarCode());
        if (calendar == null) {
            throw new IllegalStateException("默认工作日历快照不存在");
        }
        return calendar;
    }

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

    private String normalize(String value) {
        return value == null
                ? "SYSTEM_DEFAULT"
                : value.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
