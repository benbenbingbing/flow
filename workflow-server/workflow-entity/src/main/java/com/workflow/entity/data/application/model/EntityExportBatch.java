package com.workflow.entity.data.application.model;

import com.workflow.entity.data.api.response.EntityDataDTO;
import java.util.List;

/** 有界导出结果；游标由服务端物理列生成，不接受客户端指定列名或 SQL。 */
public record EntityExportBatch(List<EntityDataDTO> records, Cursor nextCursor) {
    /** 复合游标保留原始数据库类型，用主键打破排序值相同的记录，避免深 OFFSET 扫描。 */
    public record Cursor(Object sortValue, String id) {}
}
