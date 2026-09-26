package com.workflow.process.task.application.model;

import lombok.Getter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

/** 数据库分页的受控条件；文本参数只参与绑定，保留旧 contains 的字面匹配口径。 */
@Getter
public final class TaskInboxQuery {
    private final String userId;
    private final String status;
    private final long pageNum;
    private final long pageSize;
    private final long offset;
    private final String keyword;
    private final String startUserName;
    private final String priority;
    private final LocalDateTime startDate;
    private final LocalDateTime endDate;

    public TaskInboxQuery(String userId, String status, Integer pageNum, Integer pageSize, String keyword,
                          String startUserName, String priority, LocalDate startDate, LocalDate endDate) {
        if (!"todo".equals(status) && !"done".equals(status)) throw new IllegalArgumentException("非法任务列表类型");
        this.userId = userId;
        this.status = status;
        this.pageNum = pageNum == null ? 1 : Math.max(1, pageNum);
        this.pageSize = pageSize == null ? 10 : Math.max(1, Math.min(100, pageSize));
        this.offset = (this.pageNum - 1) * this.pageSize;
        this.keyword = pattern(keyword);
        this.startUserName = pattern(startUserName);
        this.priority = priority == null ? "" : priority.trim().toUpperCase(Locale.ROOT);
        this.startDate = startDate == null ? null : startDate.atStartOfDay();
        this.endDate = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
    }

    private static String pattern(String text) {
        if (text == null || text.isBlank()) return null;
        return "%" + text.trim().toLowerCase(Locale.ROOT).replace("!", "!!")
                .replace("%", "!%").replace("_", "!_") + "%";
    }
}
