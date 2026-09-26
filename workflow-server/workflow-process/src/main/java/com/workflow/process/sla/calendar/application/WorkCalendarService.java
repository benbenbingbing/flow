package com.workflow.process.sla.calendar.application;

import com.workflow.process.sla.calendar.application.model.WorkCalendarResolutionSnapshot;
import com.workflow.process.sla.calendar.application.model.WorkCalendarSnapshot;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.migration.model.ConfigMigrationPublishRequest;
import com.workflow.contracts.migration.port.MigrationAssetPort;
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

/**
 * 管理工作日历草稿、发布版本和适用范围绑定。
 * 发布时生成供 SLA 策略使用的日历快照；运行中任务应使用快照而非重新读取草稿。
 */
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
    private final MigrationAssetPort migrationAssetHandler;

    /**
     * 列出未删除的所有日历版本，供管理端优先展示默认日历。
     *
     * @return 按默认标记及名称排序的日历版本
     */
    @Transactional(readOnly = true)
    public List<WorkCalendar> list() {
        return calendarMapper.selectList(
                new LambdaQueryWrapper<WorkCalendar>()
                        .eq(WorkCalendar::getDeleted, 0)
                        .orderByDesc(WorkCalendar::getDefaultFlag)
                        .orderByAsc(WorkCalendar::getCalendarName));
    }

    /**
     * 根据版本 ID 返回日历、时段与绑定，供编辑页回显。
     *
     * @param id 要读取的日历版本 ID
     * @return 包含日历快照和作用域绑定的详情
     * @throws IllegalArgumentException 版本不存在或已删除时抛出
     */
    @Transactional(readOnly = true)
    public WorkCalendarDTO get(String id) {
        WorkCalendar calendar = requireCalendar(id);
        return new WorkCalendarDTO(
                calendar,
                snapshot(calendar),
                bindingMapper.findByCalendarId(id));
    }

    /**
     * 保存日历草稿；编辑非草稿版本时创建更高版本，保留已发布版本供在途任务追溯。
     * id 为空时新增编码，request 的时段、特殊日期和绑定会共同写入同一事务。
     *
     * @param id 待编辑版本 ID；为空时创建新日历
     * @param request 完整草稿内容，用于重建时段、特殊日期与绑定
     * @return 保存后的草稿和快照，供管理端继续预览或发布
     * @throws IllegalArgumentException 编码重复或配置非法时抛出
     */
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
        // 已发布版本不可原地修改；后续 SLA 快照可能仍引用它，必须另起草稿版本。
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

    /**
     * 使用默认迁移说明发布指定日历版本。
     *
     * @param id 要发布的日历版本 ID
     * @return 发布后的详情
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkCalendarDTO publish(String id) {
        return publish(id, new ConfigMigrationPublishRequest());
    }

    /**
     * 发布指定日历版本并记录迁移资产；同编码旧发布版转为 SUPERSEDED，
     * 默认标记在所有未删除日历中保持唯一，供后续 SLA 解析兜底。
     *
     * @param id 要发布的日历版本 ID
     * @param migrationRequest 迁移资产的版本说明；为空时自动生成
     * @return 发布后的日历详情
     * @throws IllegalArgumentException 日历或时段配置非法时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkCalendarDTO publish(
            String id,
            ConfigMigrationPublishRequest migrationRequest) {
        WorkCalendar calendar = requireCalendar(id);
        calculator.validate(snapshot(calendar));
        // 默认日历是未匹配部门/组织绑定时的最后兜底，切换时先清理旧标记。
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

    /**
     * 停用指定版本；当前默认日历不可停用，以免后续任务无法选择日历。
     *
     * @param id 要停用的日历版本 ID
     * @throws IllegalStateException 目标仍是系统默认日历时抛出
     */
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

    /**
     * 配置迁移下线时停用该编码的最新发布版；不存在时按幂等操作返回。
     *
     * @param calendarCode 迁移资产所指向的日历编码
     */
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

    /**
     * 按编码读取最新发布日历快照，供流程发布时固定日历定义。
     *
     * @param calendarCode 已发布日历编码
     * @return 最新发布版的工作时段与时区快照
     * @throws IllegalArgumentException 该编码尚未发布时抛出
     */
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

    /**
     * 返回唯一已发布默认日历；数量异常时拒绝发布依赖它的 SLA 配置。
     *
     * @return 默认日历快照
     * @throws IllegalStateException 已发布默认日历不恰好为一个时抛出
     */
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

    /**
     * 固定当前所有已发布日历和有效绑定，供任务运行时按部门或组织解析；
     * 只保留指向已发布日历的绑定，避免未来草稿改变在途任务的日历选择。
     *
     * @return 含默认编码、发布日历映射与适用范围绑定的解析快照
     */
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
        // 绑定表可能仍含旧版本的范围配置，快照只能引用本次收录的发布版本。
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

    /**
     * 按作用域及生效日期查找第一条有效绑定，仅发布中的日历可返回给调用方。
     *
     * @param scopeType 部门或组织等范围类型
     * @param scopeKey 具体范围 ID，用于定位候选绑定
     * @param date 生效日期；为空时按服务器当前日期查询
     * @return 命中的发布日历快照；无匹配项时返回 null
     */
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

    /**
     * 将日历版本及子表整理成可序列化快照，后续截止时间计算不再访问子表。
     *
     * @param calendar 已读取的日历版本，提供时区和版本坐标
     * @return 包含每周时段与特殊日期的快照
     */
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

    /**
     * 序列化日历快照，供发布配置和迁移包持久化。
     *
     * @param snapshot 待保存的日历发布内容
     * @return 可持久化的 JSON 文档
     * @throws IllegalStateException 序列化失败时抛出
     */
    public String writeSnapshot(WorkCalendarSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("工作日历快照序列化失败", exception);
        }
    }

    /**
     * 从发布文档恢复日历快照；解析失败时阻止运行时使用残缺配置。
     *
     * @param document 持久化的日历 JSON 文档
     * @return 供运行时计时的日历快照
     * @throws IllegalStateException 文档无法解析时抛出
     */
    public WorkCalendarSnapshot readSnapshot(String document) {
        try {
            return objectMapper.readValue(
                    document,
                    WorkCalendarSnapshot.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("工作日历快照解析失败", exception);
        }
    }

    /**
     * 按指定版本预演工作时间截止时刻，不写入 SLA 任务状态。
     *
     * @param calendarId 用于预演的日历版本 ID
     * @param start 累计工作分钟的起点
     * @param minutes 要累计的工作分钟数
     * @return 预演得到的绝对截止时刻
     */
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

    /**
     * 保存前验证必填坐标及有效期，时段细则交给计算器统一校验。
     *
     * @param request 待保存的日历草稿请求
     * @throws IllegalArgumentException 编码、名称、时区或生效区间非法时抛出
     */
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

    /**
     * 按输入顺序写入每周时段，sortOrder 供管理端稳定回显。
     *
     * @param calendarId 所属日历版本 ID
     * @param values 每周时段请求，允许为空
     * @param now 本次保存统一采用的 UTC 创建时间
     */
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

    /**
     * 先保存特殊日期再保存其时段，运行时将以该日期覆盖每周规则。
     *
     * @param calendarId 所属日历版本 ID
     * @param values 特殊日期及其工作时段请求
     * @param now 本次保存统一采用的 UTC 创建时间
     */
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

    /**
     * 保存部门/组织等作用域绑定，优先级和生效区间供运行时解析日历。
     *
     * @param calendarId 绑定指向的日历版本 ID
     * @param values 范围、优先级和生效区间配置
     * @param now 本次保存统一采用的 UTC 审计时间
     * @throws IllegalArgumentException 范围类型或 ID 为空时抛出
     */
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

    /**
     * 更新草稿前删除原有子表，避免旧时段或绑定残留到本次发布快照。
     *
     * @param calendarId 将被重建子配置的草稿版本 ID
     */
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

    /**
     * 统一拒绝不存在或软删除的版本，避免编辑及发布路径绕过状态检查。
     *
     * @param id 待读取的日历版本 ID
     * @return 可参与后续操作的日历记录
     * @throws IllegalArgumentException 版本不存在或已删除时抛出
     */
    private WorkCalendar requireCalendar(String id) {
        WorkCalendar calendar = calendarMapper.selectById(id);
        if (calendar == null
                || Integer.valueOf(1).equals(calendar.getDeleted())) {
            throw new IllegalArgumentException("工作日历不存在: " + id);
        }
        return calendar;
    }

    /**
     * 无交互身份的迁移任务以 system 记录修改人，供配置审计追溯。
     *
     * @return 当前用户名或 system
     */
    private String currentUser() {
        String username = UserContext.getUsername();
        return StringUtils.hasText(username) ? username : "system";
    }
}
