package com.workflow.contracts.entity.port;

import java.util.List;

/** 通过发布态的关系或引用查找可见记录，返回可继续查询的实体编码和记录 ID。 */
public interface EntityRelationQueryPort {
    enum Direction { RELATION, REFERENCE_FIELD, REVERSE_REFERENCE }

    /** 反向引用需提供声明引用字段的实体编码；其他类型由发布定义解析目标。 */
    record Step(Direction direction, String code, String targetEntityCode) { }

    record RecordRef(String entityCode, String recordId) { }

    record Result(List<RecordRef> records, long total, long pageNumber, long pageSize, boolean truncated) {
        public Result {
            records = List.copyOf(records);
        }
    }

    Result find(String sourceEntityCode, List<String> sourceRecordIds, List<Step> steps, int pageNumber, int pageSize);
}
