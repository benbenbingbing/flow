package com.workflow.process.sla.calendar.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("work_calendar_exception_period")
public class WorkCalendarExceptionPeriod {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String exceptionId;
    private Integer startMinute;
    private Integer endMinute;
    private Integer sortOrder;
    private LocalDateTime createTime;
}
