package com.workflow.service;

import com.workflow.vo.TaskVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskListFilterTest {

    @Test
    void filtersByBusinessTextOwnerPriorityAndInclusiveDateRange() {
        TaskVO urgent = task("紧急采购审批", "PROC-001", "张三", 90, LocalDate.of(2026, 7, 25));
        TaskVO normal = task("普通报销", "PROC-002", "李四", 10, LocalDate.of(2026, 7, 24));

        List<TaskVO> result = TaskListFilter.filter(
                List.of(urgent, normal),
                "采购",
                "张",
                "URGENT",
                LocalDate.of(2026, 7, 25),
                LocalDate.of(2026, 7, 25));

        assertEquals(List.of(urgent), result);
    }

    @Test
    void unknownPriorityDoesNotHideTasks() {
        TaskVO task = task("审批", "PROC-001", "张三", null, LocalDate.of(2026, 7, 25));

        assertEquals(List.of(task), TaskListFilter.filter(
                List.of(task), null, null, "FUTURE_LEVEL", null, null));
    }

    private TaskVO task(
            String processName,
            String code,
            String startUserName,
            Integer priority,
            LocalDate date) {
        TaskVO task = new TaskVO();
        task.setProcessName(processName);
        task.setCode(code);
        task.setStartUserName(startUserName);
        task.setPriority(priority);
        task.setCreateTime(Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        return task;
    }
}
