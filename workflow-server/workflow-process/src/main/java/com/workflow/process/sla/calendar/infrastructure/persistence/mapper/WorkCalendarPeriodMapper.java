package com.workflow.process.sla.calendar.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.process.sla.calendar.infrastructure.persistence.record.WorkCalendarPeriod;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface WorkCalendarPeriodMapper extends BaseMapper<WorkCalendarPeriod> {

    /** 读取日历的每周工作时段，按星期及配置顺序排列。 */
    default List<WorkCalendarPeriod> findByCalendarId(String calendarId) {
        return selectList(Wrappers.<WorkCalendarPeriod>lambdaQuery()
                .eq(WorkCalendarPeriod::getCalendarId, calendarId)
                .orderByAsc(WorkCalendarPeriod::getDayOfWeek)
                .orderByAsc(WorkCalendarPeriod::getSortOrder)
                .orderByAsc(WorkCalendarPeriod::getStartMinute));
    }

    /** 该配置表没有逻辑删除字段，使用 BaseMapper 按条件物理删除。 */
    default int deleteByCalendarId(String calendarId) {
        return delete(Wrappers.<WorkCalendarPeriod>lambdaQuery()
                .eq(WorkCalendarPeriod::getCalendarId, calendarId));
    }
}
