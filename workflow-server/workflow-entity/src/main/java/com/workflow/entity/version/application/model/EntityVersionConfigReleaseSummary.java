package com.workflow.entity.version.application.model;

import java.time.LocalDateTime;

/** 固化策略的不可变发布记录摘要。 */
public record EntityVersionConfigReleaseSummary(
        String id,
        Integer version,
        String publishedBy,
        String publishedByName,
        LocalDateTime publishTime,
        int relationCount) {
}
