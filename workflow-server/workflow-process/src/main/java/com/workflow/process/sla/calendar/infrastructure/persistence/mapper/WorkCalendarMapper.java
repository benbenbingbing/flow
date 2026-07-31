package com.workflow.process.sla.calendar.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.process.sla.calendar.infrastructure.persistence.record.WorkCalendar;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WorkCalendarMapper extends BaseMapper<WorkCalendar> {

    @Select("""
            SELECT * FROM work_calendar
            WHERE calendar_code = #{calendarCode}
              AND deleted = 0
            ORDER BY version DESC
            LIMIT 1
            """)
    WorkCalendar findByCode(@Param("calendarCode") String calendarCode);

    @Select("""
            SELECT COALESCE(MAX(version), 0)
            FROM work_calendar
            WHERE calendar_code = #{calendarCode}
              AND deleted = 0
            """)
    int findMaxVersion(@Param("calendarCode") String calendarCode);

    @Select("""
            SELECT * FROM work_calendar
            WHERE calendar_code = #{calendarCode}
              AND status = 'PUBLISHED'
              AND deleted = 0
            ORDER BY version DESC
            LIMIT 1
            """)
    WorkCalendar findLatestPublished(
            @Param("calendarCode") String calendarCode);

    @Select("""
            SELECT * FROM work_calendar
            WHERE default_flag = 1
              AND status = 'PUBLISHED'
              AND deleted = 0
            ORDER BY version DESC
            """)
    List<WorkCalendar> findPublishedDefaults();

    @Select("""
            SELECT * FROM work_calendar
            WHERE status = 'PUBLISHED'
              AND deleted = 0
            ORDER BY default_flag DESC, calendar_name, version DESC
            """)
    List<WorkCalendar> findPublished();
}
