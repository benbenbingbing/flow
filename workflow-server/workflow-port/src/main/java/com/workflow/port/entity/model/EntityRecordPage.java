package com.workflow.port.entity.model;

import java.util.List;

/** 当前调用身份可见的记录页；total 与当前页共用相同筛选和数据范围。 */
public record EntityRecordPage(List<EntityRecordData> records, long total, long pageNumber, long pageSize) {
    public EntityRecordPage {
        records = List.copyOf(records);
    }
}
