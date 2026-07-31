package com.workflow.process.sla.calendar.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("work_calendar")
public class WorkCalendar {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String calendarCode;
    private String calendarName;
    private String timezoneId;
    private String description;
    private Integer version;
    private Boolean defaultFlag;
    private String status;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String createdBy;
    private LocalDateTime createTime;
    private String updatedBy;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
