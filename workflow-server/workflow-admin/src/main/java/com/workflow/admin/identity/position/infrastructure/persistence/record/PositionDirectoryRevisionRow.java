package com.workflow.admin.identity.position.infrastructure.persistence.record;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.Data;

/** 任职目录的聚合版本事实；字符串协议在 Java 中组装，避免依赖数据库日期格式函数。 */
@Data
public class PositionDirectoryRevisionRow {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("uuuuMMddHHmmss.SSSSSS", Locale.ROOT);

    private Long positionRevision;
    private LocalDateTime organizationUpdatedAt;
    private Long assignmentCount;
    private Long assignmentRevision;
    private LocalDateTime assignmentUpdatedAt;

    /**
     * 保持既有目录版本协议：职务版本、组织时间、任职数、最大任职版本、最后任职时间。
     * 数据库时间为无时区的本地时间，保留六位小数，不转换时区；缺少任职时间以 0 表示。
     * 缺少职务版本或组织时间时保留原 SQL CONCAT 的空值结果，不能伪造有效版本。
     *
     * @return 转换为后的修订版本令牌文本，供调用方比较或展示
     */
    public String toRevisionToken() {
        if (positionRevision == null || organizationUpdatedAt == null) return null;
        return positionRevision + ":" + TIMESTAMP.format(organizationUpdatedAt) + ":"
                + assignmentCount + ":" + assignmentRevision + ":"
                + (assignmentUpdatedAt == null ? "0" : TIMESTAMP.format(assignmentUpdatedAt));
    }
}
