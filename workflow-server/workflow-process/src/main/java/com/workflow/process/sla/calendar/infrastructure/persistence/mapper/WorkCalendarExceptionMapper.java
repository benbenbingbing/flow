package com.workflow.process.sla.calendar.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.process.sla.calendar.infrastructure.persistence.record.WorkCalendarException;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface WorkCalendarExceptionMapper extends BaseMapper<WorkCalendarException> {

    @Select("""
            SELECT * FROM work_calendar_exception
            WHERE calendar_id = #{calendarId}
            ORDER BY exception_date
            """)
    List<WorkCalendarException> findByCalendarId(@Param("calendarId") String calendarId);

    @Select("""
            SELECT * FROM work_calendar_exception
            WHERE calendar_id = #{calendarId}
              AND exception_date = #{date}
            LIMIT 1
            """)
    WorkCalendarException findByDate(
            @Param("calendarId") String calendarId,
            @Param("date") LocalDate date);

    @Delete("DELETE FROM work_calendar_exception WHERE calendar_id = #{calendarId}")
    int deleteByCalendarId(@Param("calendarId") String calendarId);
}
