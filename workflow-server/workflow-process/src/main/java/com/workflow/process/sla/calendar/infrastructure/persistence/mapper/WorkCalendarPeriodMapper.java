package com.workflow.process.sla.calendar.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.process.sla.calendar.infrastructure.persistence.record.WorkCalendarPeriod;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WorkCalendarPeriodMapper extends BaseMapper<WorkCalendarPeriod> {

    @Select("""
            SELECT * FROM work_calendar_period
            WHERE calendar_id = #{calendarId}
            ORDER BY day_of_week, sort_order, start_minute
            """)
    List<WorkCalendarPeriod> findByCalendarId(@Param("calendarId") String calendarId);

    @Delete("DELETE FROM work_calendar_period WHERE calendar_id = #{calendarId}")
    int deleteByCalendarId(@Param("calendarId") String calendarId);
}
