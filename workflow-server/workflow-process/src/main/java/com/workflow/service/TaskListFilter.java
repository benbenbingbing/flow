package com.workflow.service;

import com.workflow.vo.TaskVO;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 工作台任务列表的统一筛选规则。
 */
public final class TaskListFilter {

    private TaskListFilter() {
    }

    public static List<TaskVO> filter(
            List<TaskVO> tasks,
            String keyword,
            String startUserName,
            String priority,
            LocalDate startDate,
            LocalDate endDate) {
        return tasks.stream()
                .filter(task -> matchesKeyword(task, keyword))
                .filter(task -> contains(task.getStartUserName(), startUserName))
                .filter(task -> matchesPriority(task.getPriority(), priority))
                .filter(task -> matchesDate(task.getCreateTime(), startDate, endDate))
                .toList();
    }

    private static boolean matchesKeyword(TaskVO task, String keyword) {
        if (isBlank(keyword)) {
            return true;
        }
        return contains(task.getProcessName(), keyword)
                || contains(task.getTaskName(), keyword)
                || contains(task.getCurrentTaskName(), keyword)
                || contains(task.getName(), keyword)
                || contains(task.getDataName(), keyword)
                || contains(task.getCode(), keyword)
                || contains(task.getBusinessKey(), keyword);
    }

    private static boolean matchesPriority(Integer value, String priority) {
        if (isBlank(priority)) {
            return true;
        }
        int normalized = value == null ? 0 : value;
        return switch (priority.trim().toUpperCase(Locale.ROOT)) {
            case "URGENT" -> normalized >= 80;
            case "HIGH" -> normalized >= 50 && normalized < 80;
            case "NORMAL" -> normalized < 50;
            default -> true;
        };
    }

    private static boolean matchesDate(Date value, LocalDate startDate, LocalDate endDate) {
        if (value == null) {
            return startDate == null && endDate == null;
        }
        LocalDate taskDate = value.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return (startDate == null || !taskDate.isBefore(startDate))
                && (endDate == null || !taskDate.isAfter(endDate));
    }

    private static boolean contains(String value, String expected) {
        if (isBlank(expected)) {
            return true;
        }
        return value != null
                && value.toLowerCase(Locale.ROOT).contains(expected.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
