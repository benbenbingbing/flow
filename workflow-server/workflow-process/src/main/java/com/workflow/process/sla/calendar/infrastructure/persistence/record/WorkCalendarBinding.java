package com.workflow.process.sla.calendar.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("work_calendar_binding")
public class WorkCalendarBinding {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String scopeType;
    private String scopeKey;
    private String calendarId;
    private Integer priority;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String status;
    private String createdBy;
    private LocalDateTime createTime;
    private String updatedBy;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
