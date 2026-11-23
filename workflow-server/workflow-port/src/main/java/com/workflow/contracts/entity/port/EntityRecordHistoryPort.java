package com.workflow.contracts.entity.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 业务记录版本只读视图。快照与对比文档沿用服务端版本化的数据格式。 */
public interface EntityRecordHistoryPort {
    record Version(String id, int number, String title, String operation, String operatorId,
                   String operatorName, String processInstanceId, LocalDateTime createdAt) { }

    record Page(List<Version> records, long total, long pageNumber, long pageSize) {
        public Page {
            records = List.copyOf(records);
        }
    }

    Page list(String entityCode, String recordId, int pageNumber, int pageSize);
    Map<String, Object> snapshot(String entityCode, String recordId, int version);
    Map<String, Object> compare(String entityCode, String recordId, int fromVersion, int toVersion);

    /** 预演恢复并返回结构化差异和阻断项，供业务模块判断后续处理。 */
    Map<String, Object> planRestore(String entityCode, String recordId, int version);
}
