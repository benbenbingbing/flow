package com.workflow.process.sla.calendar.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("work_calendar_period")
public class WorkCalendarPeriod {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String calendarId;
    private Integer dayOfWeek;
    private Integer startMinute;
    private Integer endMinute;
    private Integer sortOrder;
    private LocalDateTime createTime;
}
