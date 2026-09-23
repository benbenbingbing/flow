package com.workflow.process.sla.calendar.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.process.sla.calendar.infrastructure.persistence.record.WorkCalendarBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;

import java.time.LocalDate;
import java.util.List;

/**
 * 定义工作日历绑定的调用契约；实现层按此提供能力，调用方无需依赖具体实现。
 */
@Mapper
public interface WorkCalendarBindingMapper extends BaseMapper<WorkCalendarBinding> {

    /**
     * 查询日历的有效绑定，优先级高的绑定优先返回。
     *
     * @param calendarId 日历ID，后续用于查询日历ID时定位或关联目标
     * @return 工作日历绑定集合，供调用方遍历或展示
     */
    default List<WorkCalendarBinding> findByCalendarId(String calendarId) {
        return selectList(Wrappers.<WorkCalendarBinding>lambdaQuery()
                .eq(WorkCalendarBinding::getCalendarId, calendarId)
                .orderByDesc(WorkCalendarBinding::getPriority)
                .orderByAsc(WorkCalendarBinding::getCreateTime));
    }

    /**
     * 删除日历ID；后续读取或执行将使用更新后的状态。
     *
     * @param calendarId 日历ID，后续用于删除日历ID时定位或关联目标
     * @return 删除后的日历ID结果，供调用方继续处理
     */
    @Delete("DELETE FROM work_calendar_binding WHERE calendar_id = #{calendarId}")
    int deleteByCalendarId(@Param("calendarId") String calendarId);

    /**
     * 读取所有启用且未删除的日历绑定，供作用域匹配使用。
     *
     * @return 工作日历绑定集合，供调用方遍历或展示
     */
    default List<WorkCalendarBinding> findAllEnabled() {
        return selectList(Wrappers.<WorkCalendarBinding>lambdaQuery()
                .eq(WorkCalendarBinding::getStatus, "ENABLED")
                .orderByAsc(WorkCalendarBinding::getScopeType)
                .orderByAsc(WorkCalendarBinding::getScopeKey)
                .orderByDesc(WorkCalendarBinding::getPriority)
                .orderByDesc(WorkCalendarBinding::getUpdateTime));
    }

    /**
     * 按调用方指定的业务日期选择有效绑定，起止边界均包含当天，空边界表示不限。
     * date 为 null 时沿用 SQL 三值逻辑，仅两端都未限定日期的绑定可以命中。
     *
     * @param scopeType 作用域类型标识，决定后续有效采用的处理分支
     * @param scopeKey 作用域键，后续用于授权校验、关联或幂等去重
     * @param date 日期，后续用于判断有效期或展示该事件的发生时间
     * @return 工作日历绑定集合，供调用方遍历或展示
     */
    default List<WorkCalendarBinding> findEffective(String scopeType, String scopeKey, LocalDate date) {
        return selectList(Wrappers.<WorkCalendarBinding>lambdaQuery()
                .eq(WorkCalendarBinding::getScopeType, scopeType)
                .eq(WorkCalendarBinding::getScopeKey, scopeKey)
                .eq(WorkCalendarBinding::getStatus, "ENABLED")
                .and(start -> start.isNull(WorkCalendarBinding::getEffectiveFrom)
                        .or().le(WorkCalendarBinding::getEffectiveFrom, date))
                .and(end -> end.isNull(WorkCalendarBinding::getEffectiveTo)
                        .or().ge(WorkCalendarBinding::getEffectiveTo, date))
                .orderByDesc(WorkCalendarBinding::getPriority, WorkCalendarBinding::getUpdateTime));
    }
}
