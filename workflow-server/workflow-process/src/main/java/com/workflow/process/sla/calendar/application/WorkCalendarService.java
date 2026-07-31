package com.workflow.process.sla.calendar.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.migration.ConfigMigrationPublishRequest;
import com.workflow.contracts.migration.MigrationAssetHandler;
import com.workflow.process.sla.calendar.api.request.WorkCalendarSaveRequest;
import com.workflow.process.sla.calendar.api.response.WorkCalendarDTO;
import com.workflow.process.sla.calendar.infrastructure.persistence.mapper.WorkCalendarBindingMapper;
import com.workflow.process.sla.calendar.infrastructure.persistence.mapper.WorkCalendarExceptionMapper;
import com.workflow.process.sla.calendar.infrastructure.persistence.mapper.WorkCalendarExceptionPeriodMapper;
import com.workflow.process.sla.calendar.infrastructure.persistence.mapper.WorkCalendarMapper;
import com.workflow.process.sla.calendar.infrastructure.persistence.mapper.WorkCalendarPeriodMapper;
import com.workflow.process.sla.calendar.infrastructure.persistence.record.WorkCalendar;
import com.workflow.process.sla.calendar.infrastructure.persistence.record.WorkCalendarBinding;
import com.workflow.process.sla.calendar.infrastructure.persistence.record.WorkCalendarException;
import com.workflow.process.sla.calendar.infrastructure.persistence.record.WorkCalendarExceptionPeriod;
import com.workflow.process.sla.calendar.infrastructure.persistence.record.WorkCalendarPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkCalendarService {

    private final WorkCalendarMapper calendarMapper;
    private final WorkCalendarPeriodMapper periodMapper;
    private final WorkCalendarExceptionMapper exceptionMapper;
    private final WorkCalendarExceptionPeriodMapper exceptionPeriodMapper;
    private final WorkCalendarBindingMapper bindingMapper;
    private final WorkCalendarCalculator calculator;
    private final ObjectMapper objectMapper;
    private final MigrationAssetHandler migrationAssetHandler;

    @Transactional(readOnly = true)
    public List<WorkCalendar> list() {
        return calendarMapper.selectList(
                new LambdaQueryWrapper<WorkCalendar>()
                        .eq(WorkCalendar::getDeleted, 0)
                        .orderByDesc(WorkCalendar::getDefaultFlag)
                        .orderByAsc(WorkCalendar::getCalendarName));
    }

    @Transactional(readOnly = true)
    public WorkCalendarDTO get(String id) {
        WorkCalendar calendar = requireCalendar(id);
        return new WorkCalendarDTO(
                calendar,
                snapshot(calendar),
                bindingMapper.findByCalendarId(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkCalendarDTO save(
            String id,
            WorkCalendarSaveRequest request) {
        validateRequest(request);
        WorkCalendar existing = StringUtils.hasText(id)
                ? requireCalendar(id)
                : null;
        String code = request.calendarCode().trim();
        WorkCalendar latest = calendarMapper.findByCode(code);
        if (existing == null && latest != null) {
            throw new IllegalArgumentException("工作日历编码已存在");
        }
        if (existing != null
                && !code.equals(existing.getCalendarCode())) {
            throw new IllegalArgumentException("工作日历编码不可修改");
        }
        boolean createVersion = existing == null
                || !"DRAFT".equals(existing.getStatus());
        WorkCalendar calendar = createVersion
                ? new WorkCalendar()
                : existing;
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        calendar.setCalendarCode(code);
        calendar.setCalendarName(request.calendarName().trim());
        calendar.setTimezoneId(request.timezoneId().trim());
        calendar.setDescription(request.description());
        calendar.setDefaultFlag(Boolean.TRUE.equals(request.defaultFlag()));
        calendar.setEffectiveFrom(request.effectiveFrom());
        calendar.setEffectiveTo(request.effectiveTo());
        calendar.setStatus("DRAFT");
        calendar.setVersion(createVersion
                ? calendarMapper.findMaxVersion(code) + 1
                : existing.getVersion());
        calendar.setUpdatedBy(currentUser());
        calendar.setUpdateTime(now);
        calendar.setDeleted(0);
        if (createVersion) {
            calendar.setCreatedBy(currentUser());
            calendar.setCreateTime(now);
            calendarMapper.insert(calendar);
        } else {
            calendarMapper.updateById(calendar);
            clearChildren(calendar.getId());
        }
        savePeriods(calendar.getId(), request.periods(), now);
        saveExceptions(calendar.getId(), request.exceptions(), now);
        saveBindings(calendar.getId(), request.bindings(), now);
        WorkCalendarSnapshot snapshot = snapshot(calendar);
        calculator.validate(snapshot);
        return new WorkCalendarDTO(
                calendar,
                snapshot,
                bindingMapper.findByCalendarId(calendar.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkCalendarDTO publish(String id) {
        return publish(id, new ConfigMigrationPublishRequest());
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkCalendarDTO publish(
            String id,
            ConfigMigrationPublishRequest migrationRequest) {
        WorkCalendar calendar = requireCalendar(id);
        calculator.validate(snapshot(calendar));
        if (Boolean.TRUE.equals(calendar.getDefaultFlag())) {
            calendarMapper.update(
                    null,
                    new LambdaUpdateWrapper<WorkCalendar>()
                            .set(WorkCalendar::getDefaultFlag, false)
                            .eq(WorkCalendar::getDefaultFlag, true)
                            .eq(WorkCalendar::getDeleted, 0)
                            .ne(WorkCalendar::getId, calendar.getId()));
        }
        calendarMapper.update(
                null,
                new LambdaUpdateWrapper<WorkCalendar>()
                        .set(WorkCalendar::getStatus, "SUPERSEDED")
                        .set(WorkCalendar::getDefaultFlag, false)
                        .set(WorkCalendar::getUpdatedBy, currentUser())
                        .set(WorkCalendar::getUpdateTime,
                                LocalDateTime.now(ZoneOffset.UTC))
                        .eq(WorkCalendar::getCalendarCode,
                                calendar.getCalendarCode())
                        .eq(WorkCalendar::getStatus, "PUBLISHED")
                        .eq(WorkCalendar::getDeleted, 0)
                        .ne(WorkCalendar::getId, calendar.getId()));
        calendar.setStatus("PUBLISHED");
        calendar.setUpdatedBy(currentUser());
        calendar.setUpdateTime(LocalDateTime.now(ZoneOffset.UTC));
        calendarMapper.updateById(calendar);
        ConfigMigrationPublishRequest effectiveRequest =
                migrationRequest == null
                        ? new ConfigMigrationPublishRequest()
                        : migrationRequest;
        if (!StringUtils.hasText(
                effectiveRequest.getVersionDescription())) {
            effectiveRequest.setVersionDescription(
                    "发布工作日历 " + calendar.getCalendarCode()
                            + " V" + calendar.getVersion());
        }
        migrationAssetHandler.recordWorkCalendar(
                calendar.getId(),
                effectiveRequest);
        return get(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void disable(String id) {
        WorkCalendar calendar = requireCalendar(id);
        if (Boolean.TRUE.equals(calendar.getDefaultFlag())) {
            throw new IllegalStateException("系统默认工作日历不能停用");
        }
        calendar.setStatus("DISABLED");
        calendar.setUpdatedBy(currentUser());
        calendar.setUpdateTime(LocalDateTime.now(ZoneOffset.UTC));
        calendarMapper.updateById(calendar);
    }

    @Transactional(rollbackFor = Exception.class)
    public void disableForMigration(String calendarCode) {
        WorkCalendar calendar =
                calendarMapper.findLatestPublished(calendarCode);
        if (calendar == null) {
            return;
        }
        calendar.setDefaultFlag(false);
        calendar.setStatus("DISABLED");
        calendar.setUpdatedBy(currentUser());
        calendar.setUpdateTime(LocalDateTime.now(ZoneOffset.UTC));
        calendarMapper.updateById(calendar);
    }

    @Transactional(readOnly = true)
    public WorkCalendarSnapshot findPublishedSnapshotByCode(
            String calendarCode) {
        WorkCalendar calendar =
                calendarMapper.findLatestPublished(calendarCode);
        if (calendar == null) {
            throw new IllegalArgumentException(
                    "工作日历未发布: " + calendarCode);
        }
        return snapshot(calendar);
    }

    @Transactional(readOnly = true)
    public WorkCalendarSnapshot findDefaultSnapshot() {
        List<WorkCalendar> defaults =
                calendarMapper.findPublishedDefaults();
        if (defaults.size() != 1) {
            throw new IllegalStateException(
                    "系统必须且只能有一个已发布默认工作日历");
        }
        return snapshot(defaults.get(0));
    }

    @Transactional(readOnly = true)
    public WorkCalendarResolutionSnapshot resolutionSnapshot() {
        List<WorkCalendar> published = calendarMapper.findPublished();
        Map<String, WorkCalendar> byId = published.stream()
                .collect(Collectors.toMap(
                        WorkCalendar::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        Map<String, WorkCalendarSnapshot> calendars =
                new LinkedHashMap<>();
        for (WorkCalendar calendar : published) {
            calendars.put(
                    calendar.getCalendarCode(),
                    snapshot(calendar));
        }
        WorkCalendarSnapshot defaultCalendar =
                findDefaultSnapshot();
        List<WorkCalendarResolutionSnapshot.Binding> bindings =
                bindingMapper.findAllEnabled().stream()
                        .filter(binding ->
                                byId.containsKey(
                                        binding.getCalendarId()))
                        .map(binding -> {
                            WorkCalendar calendar =
                                    byId.get(binding.getCalendarId());
                            return new WorkCalendarResolutionSnapshot.Binding(
                                    binding.getScopeType(),
                                    binding.getScopeKey(),
                                    calendar.getCalendarCode(),
                                    binding.getPriority() == null
                                            ? 0
                                            : binding.getPriority(),
                                    binding.getEffectiveFrom(),
                                    binding.getEffectiveTo());
                        })
                        .toList();
        return new WorkCalendarResolutionSnapshot(
                defaultCalendar.calendarCode(),
                calendars,
                bindings);
    }

    @Transactional(readOnly = true)
    public WorkCalendarSnapshot resolveBinding(
            String scopeType,
            String scopeKey,
            LocalDate date) {
        if (!StringUtils.hasText(scopeType)
                || !StringUtils.hasText(scopeKey)) {
            return null;
        }
        List<WorkCalendarBinding> bindings =
                bindingMapper.findEffective(
                        scopeType.trim().toUpperCase(),
                        scopeKey.trim(),
                        date == null ? LocalDate.now() : date);
        if (bindings.isEmpty()) {
            return null;
        }
        WorkCalendar calendar =
                calendarMapper.selectById(bindings.get(0).getCalendarId());
        return calendar == null
                || !"PUBLISHED".equals(calendar.getStatus())
                ? null
                : snapshot(calendar);
    }

    @Transactional(readOnly = true)
    public WorkCalendarSnapshot snapshot(WorkCalendar calendar) {
        Map<Integer, List<WorkCalendarSnapshot.Period>> weekly =
                new LinkedHashMap<>();
        for (WorkCalendarPeriod period :
                periodMapper.findByCalendarId(calendar.getId())) {
            weekly.computeIfAbsent(
                    period.getDayOfWeek(),
                    ignored -> new java.util.ArrayList<>())
                    .add(new WorkCalendarSnapshot.Period(
                            period.getStartMinute(),
                            period.getEndMinute()));
        }
        Map<LocalDate, WorkCalendarSnapshot.ExceptionDay> exceptions =
                new LinkedHashMap<>();
        for (WorkCalendarException exception :
                exceptionMapper.findByCalendarId(calendar.getId())) {
            List<WorkCalendarSnapshot.Period> periods =
                    exceptionPeriodMapper
                            .findByExceptionId(exception.getId())
                            .stream()
                            .map(value ->
                                    new WorkCalendarSnapshot.Period(
                                            value.getStartMinute(),
                                            value.getEndMinute()))
                            .toList();
            exceptions.put(
                    exception.getExceptionDate(),
                    new WorkCalendarSnapshot.ExceptionDay(
                            exception.getExceptionType(),
                            exception.getExceptionName(),
                            periods));
        }
        return new WorkCalendarSnapshot(
                calendar.getCalendarCode(),
                calendar.getCalendarName(),
                calendar.getVersion(),
                calendar.getTimezoneId(),
                weekly,
                exceptions);
    }

    public String writeSnapshot(WorkCalendarSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("工作日历快照序列化失败", exception);
        }
    }

    public WorkCalendarSnapshot readSnapshot(String document) {
        try {
            return objectMapper.readValue(
                    document,
                    WorkCalendarSnapshot.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("工作日历快照解析失败", exception);
        }
    }

    public Instant simulate(
            String calendarId,
            Instant start,
            int minutes) {
        return calculator.addMinutes(
                start,
                minutes,
                "WORKING_TIME",
                snapshot(requireCalendar(calendarId)));
    }

    private void validateRequest(WorkCalendarSaveRequest request) {
        if (request == null
                || !StringUtils.hasText(request.calendarCode())
                || !StringUtils.hasText(request.calendarName())
                || !StringUtils.hasText(request.timezoneId())) {
            throw new IllegalArgumentException(
                    "日历编码、名称和时区不能为空");
        }
        if (request.effectiveFrom() != null
                && request.effectiveTo() != null
                && request.effectiveFrom().isAfter(
                        request.effectiveTo())) {
            throw new IllegalArgumentException(
                    "日历生效开始日期不能晚于结束日期");
        }
    }

    private void savePeriods(
            String calendarId,
            List<WorkCalendarSaveRequest.PeriodRequest> values,
            LocalDateTime now) {
        int sort = 0;
        for (WorkCalendarSaveRequest.PeriodRequest request :
                values == null ? List.<WorkCalendarSaveRequest.PeriodRequest>of() : values) {
            WorkCalendarPeriod period = new WorkCalendarPeriod();
            period.setCalendarId(calendarId);
            period.setDayOfWeek(request.dayOfWeek());
            period.setStartMinute(request.startMinute());
            period.setEndMinute(request.endMinute());
            period.setSortOrder(sort++);
            period.setCreateTime(now);
            periodMapper.insert(period);
        }
    }

    private void saveExceptions(
            String calendarId,
            List<WorkCalendarSaveRequest.ExceptionRequest> values,
            LocalDateTime now) {
        for (WorkCalendarSaveRequest.ExceptionRequest request :
                values == null ? List.<WorkCalendarSaveRequest.ExceptionRequest>of() : values) {
            WorkCalendarException exception =
                    new WorkCalendarException();
            exception.setCalendarId(calendarId);
            exception.setExceptionDate(request.date());
            exception.setExceptionType(
                    request.type() == null
                            ? null
                            : request.type().trim().toUpperCase());
            exception.setExceptionName(request.name());
            exception.setDescription(request.description());
            exception.setCreateTime(now);
            exceptionMapper.insert(exception);
            int sort = 0;
            for (WorkCalendarSaveRequest.TimePeriodRequest period :
                    request.periods() == null
                            ? List.<WorkCalendarSaveRequest.TimePeriodRequest>of()
                            : request.periods()) {
                WorkCalendarExceptionPeriod entity =
                        new WorkCalendarExceptionPeriod();
                entity.setExceptionId(exception.getId());
                entity.setStartMinute(period.startMinute());
                entity.setEndMinute(period.endMinute());
                entity.setSortOrder(sort++);
                entity.setCreateTime(now);
                exceptionPeriodMapper.insert(entity);
            }
        }
    }

    private void saveBindings(
            String calendarId,
            List<WorkCalendarSaveRequest.BindingRequest> values,
            LocalDateTime now) {
        for (WorkCalendarSaveRequest.BindingRequest request :
                values == null ? List.<WorkCalendarSaveRequest.BindingRequest>of() : values) {
            if (!StringUtils.hasText(request.scopeType())
                    || !StringUtils.hasText(request.scopeKey())) {
                throw new IllegalArgumentException(
                        "日历绑定的范围类型和范围值不能为空");
            }
            WorkCalendarBinding binding = new WorkCalendarBinding();
            binding.setCalendarId(calendarId);
            binding.setScopeType(
                    request.scopeType().trim().toUpperCase());
            binding.setScopeKey(request.scopeKey().trim());
            binding.setPriority(
                    request.priority() == null ? 0 : request.priority());
            binding.setEffectiveFrom(request.effectiveFrom());
            binding.setEffectiveTo(request.effectiveTo());
            binding.setStatus("ENABLED");
            binding.setCreatedBy(currentUser());
            binding.setCreateTime(now);
            binding.setUpdatedBy(currentUser());
            binding.setUpdateTime(now);
            binding.setDeleted(0);
            bindingMapper.insert(binding);
        }
    }

    private void clearChildren(String calendarId) {
        for (WorkCalendarException exception :
                exceptionMapper.findByCalendarId(calendarId)) {
            exceptionPeriodMapper.deleteByExceptionId(
                    exception.getId());
        }
        exceptionMapper.deleteByCalendarId(calendarId);
        periodMapper.deleteByCalendarId(calendarId);
        bindingMapper.deleteByCalendarId(calendarId);
    }

    private WorkCalendar requireCalendar(String id) {
        WorkCalendar calendar = calendarMapper.selectById(id);
        if (calendar == null
                || Integer.valueOf(1).equals(calendar.getDeleted())) {
            throw new IllegalArgumentException("工作日历不存在: " + id);
        }
        return calendar;
    }

    private String currentUser() {
        String username = UserContext.getUsername();
        return StringUtils.hasText(username) ? username : "system";
    }
}
