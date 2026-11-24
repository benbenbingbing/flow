package com.workflow.port.entity;

import java.util.List;

/** 通过已发布的关系及引用查找实体编码和记录 ID。调用方负责来源与各跳的访问授权。 */
public interface EntityRelationPort {
    enum Direction { RELATION, REFERENCE_FIELD, REVERSE_REFERENCE }

    /** 反向引用需提供声明引用字段的实体编码；其他类型由发布定义解析目标。 */
    record Step(Direction direction, String code, String targetEntityCode) { }

    record RecordRef(String entityCode, String recordId) { }

    record Result(List<RecordRef> records, long total, long pageNumber, long pageSize, boolean truncated) {
        public Result {
            records = List.copyOf(records);
        }
    }

    Result findRelatedRecords(String sourceEntityCode, List<String> sourceRecordIds, List<Step> steps, int pageNumber, int pageSize);
}
