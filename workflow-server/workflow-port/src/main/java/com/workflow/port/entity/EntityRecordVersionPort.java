package com.workflow.port.entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 查询业务记录版本和按已启用的版本策略留档。快照与对比文档沿用版本化的数据格式。 */
public interface EntityRecordVersionPort {
    record Version(String id, int number, String title, String operation, String operatorId,
                   String operatorName, String processInstanceId, LocalDateTime createdAt) { }

    record Page(List<Version> records, long total, long pageNumber, long pageSize) {
        public Page {
            records = List.copyOf(records);
        }
    }

    Page listVersions(String entityCode, String recordId, int pageNumber, int pageSize);
    Map<String, Object> getSnapshot(String entityCode, String recordId, int version);
    Map<String, Object> compareVersions(String entityCode, String recordId, int fromVersion, int toVersion);

    /** 预演恢复并返回结构化差异和阻断项，供业务模块判断后续处理。 */
    Map<String, Object> previewRestore(String entityCode, String recordId, int version);

    /**
     * 为调用方已授权的记录创建手工版本。重试使用相同幂等键。
     * triggerCode 由实体已启用的版本策略解释，reason 记录留档目的。
     */
    Version capture(String entityCode, String recordId, String idempotencyKey, String triggerCode, String reason);

    /** 按历史表单数据集节点编码分页查询快照行，返回该版本冻结的字段数据。 */
    Map<String, Object> pageSnapshotRows(String entityCode, String recordId, int version,
            String nodeCode, long pageNumber, long pageSize);

    /** 按历史数据集节点编码分页比较子记录；changedOnly 为 true 时仅返回有变化的行。 */
    Map<String, Object> pageDifferenceRows(String entityCode, String recordId, int fromVersion, int toVersion,
            String nodeCode, long pageNumber, long pageSize, boolean changedOnly);
}
