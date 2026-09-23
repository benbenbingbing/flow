package com.workflow.process.sla.calendar.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 封装工作日历的数据访问；应用服务通过它读取或持久化业务状态。
 */
@Data
@TableName("work_calendar_exception")
public class WorkCalendarException {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String calendarId;
    private LocalDate exceptionDate;
    private String exceptionType;
    private String exceptionName;
    private String description;
    private LocalDateTime createTime;
}
