package com.workflow.entity.version.application.model;

import java.time.LocalDateTime;

/**
 * 实体版本策略发布记录。
 */
public record EntityVersionReleaseSummary(
        String id,
        Integer version,
        String publishedBy,
        String publishedByName,
        LocalDateTime publishTime) {
}
