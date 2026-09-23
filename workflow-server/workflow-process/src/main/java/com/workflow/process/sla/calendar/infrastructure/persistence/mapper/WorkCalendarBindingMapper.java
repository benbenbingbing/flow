package com.workflow.process.sla.calendar.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.process.sla.calendar.infrastructure.persistence.record.WorkCalendarBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface WorkCalendarBindingMapper extends BaseMapper<WorkCalendarBinding> {

    /** 查询日历的有效绑定，优先级高的绑定优先返回。 */
    default List<WorkCalendarBinding> findByCalendarId(String calendarId) {
        return selectList(Wrappers.<WorkCalendarBinding>lambdaQuery()
                .eq(WorkCalendarBinding::getCalendarId, calendarId)
                .orderByDesc(WorkCalendarBinding::getPriority)
                .orderByAsc(WorkCalendarBinding::getCreateTime));
    }

    @Delete("DELETE FROM work_calendar_binding WHERE calendar_id = #{calendarId}")
    int deleteByCalendarId(@Param("calendarId") String calendarId);

    /** 读取所有启用且未删除的日历绑定，供作用域匹配使用。 */
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
