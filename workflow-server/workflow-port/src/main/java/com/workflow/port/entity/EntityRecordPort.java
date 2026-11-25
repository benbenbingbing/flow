package com.workflow.port.entity;

import com.workflow.port.entity.model.EntityRecordData;
import com.workflow.port.entity.model.EntityRecordPage;
import com.workflow.port.entity.model.mutation.EntitySubmissionCommand;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 查询及提交业务实体记录。查询的实体和行范围由调用方授权。 */
public interface EntityRecordPort {
    /** 按主键读取未删除记录，不存在时返回 null。 */
    EntityRecordData findById(String entityCode, String recordId);

    /** 按主键读取记录，并确认给定列表键对应已发布列表。 */
    default EntityRecordData findById(String entityCode, String listKey, String recordId) {
        throw new UnsupportedOperationException("当前实现未提供指定列表的实体详情");
    }

    /** 使用已发布字段和调用方提供的条件查询；条件格式与实体运行时保持一致。 */
    List<EntityRecordData> findByCondition(String entityCode, Map<String, Object> condition);

    /** 按发布字段排序和筛选后分页；页码从 1 开始，有效页大小最多 200。 */
    default EntityRecordPage page(String entityCode, Map<String, Object> condition,
            int pageNumber, int pageSize, String sortField, boolean descending) {
        throw new UnsupportedOperationException("当前实现未提供实体分页");
    }

    /** 按调用方提供的条件分页，并确认给定列表键对应已发布列表。 */
    default EntityRecordPage page(String entityCode, String listKey, Map<String, Object> condition,
            int pageNumber, int pageSize, String sortField, boolean descending) {
        throw new UnsupportedOperationException("当前实现未提供指定列表的实体分页");
    }

    /** 按输入顺序去重并批量读取主表记录，缺失和已逻辑删除的记录被略过。 */
    default List<EntityRecordData> findByIds(String entityCode, List<String> recordIds) {
        return recordIds.stream().distinct().map(id -> findById(entityCode, id))
                .filter(java.util.Objects::nonNull).toList();
    }

    /** 仅保留指定业务字段；记录 ID、状态和流程引用等系统字段仍可供调用方关联。 */
    default List<EntityRecordData> findByIds(String entityCode, List<String> recordIds, Set<String> fields) {
        return findByIds(entityCode, recordIds).stream().map(record -> selectFields(record, fields)).toList();
    }

    /** 在服务端筛选和排序之后投影业务字段，分页及总数沿用原查询结果。 */
    default EntityRecordPage page(String entityCode, Map<String, Object> condition, int pageNumber, int pageSize,
            String sortField, boolean descending, Set<String> fields) {
        var result = page(entityCode, condition, pageNumber, pageSize, sortField, descending);
        return new EntityRecordPage(result.records().stream().map(record -> selectFields(record, fields)).toList(),
                result.total(), result.pageNumber(), result.pageSize());
    }

    /** 统计命中调用方查询条件的记录，无需组装实体聚合。 */
    default long count(String entityCode, Map<String, Object> condition) {
        return findByCondition(entityCode, condition).size();
    }

    /** 判断调用方提供的条件是否有命中记录。 */
    default boolean exists(String entityCode, Map<String, Object> condition) {
        return count(entityCode, condition) > 0;
    }

    /** 对发布的主表数值字段求和，复用实体查询的条件。无匹配值时返回零。 */
    default BigDecimal sum(String entityCode, String fieldCode, Map<String, Object> condition) {
        throw new UnsupportedOperationException("当前实现未提供实体字段求和");
    }

    /** 根据主记录保存的当前流程实例 ID 查询未删除记录；历史轮次可先从流程查询取得业务引用。 */
    default EntityRecordData findByProcessInstance(String entityCode, String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            throw new IllegalArgumentException("流程实例 ID 不能为空");
        }
        return findByCondition(entityCode, Map.of("process_instance_id", processInstanceId, "process_instance_id_op", "EQ"))
                .stream().findFirst().orElse(null);
    }

    private static EntityRecordData selectFields(EntityRecordData record, Set<String> fields) {
        if (fields == null) return record;
        var values = new LinkedHashMap<String, Object>();
        if (record.getData() != null) {
            record.getData().forEach((key, value) -> {
                if (fields.contains(key)) values.put(key, value);
            });
        }
        record.setData(values);
        return record;
    }

    /** 保存新草稿。命令的意图须为 CREATE_DRAFT，同一操作者重试时沿用原 requestId。 */
    EntityRecordData saveDraft(EntitySubmissionCommand command);

    /** 保存已有记录。意图须为 UPDATE，可传详情的 revision 在写锁下校验。 */
    EntityRecordData update(EntitySubmissionCommand command);

    /** 新增并启动流程。命令的意图须为 CREATE_AND_START，启动失败时一并回滚实体写入。 */
    EntityRecordData createAndStart(EntitySubmissionCommand command);

    /** 提交已有草稿并启动流程。命令的意图须为 START_EXISTING。 */
    EntityRecordData startExisting(EntitySubmissionCommand command);

    /** 从已撤回的上一轮重新发起。调用方负责授权，命令意图须为 RESTART 并提供上一轮实例 ID。 */
    EntityRecordData restart(EntitySubmissionCommand command);

    /** 逻辑删除调用方已授权的记录，保留发布动作的状态约束及历史数据。 */
    void delete(String entityCode, String recordId, String listKey);

    /** 逐条校验调用方已授权的记录是否满足删除条件，任一记录失败时整批回滚。 */
    void deleteBatch(String entityCode, List<String> recordIds, String listKey);

    /** 根据发布配置和记录状态返回 edit、delete、batchDelete、restartProcess 能力及受限原因。 */
    Map<String, RecordCapability> getRecordCapabilities(String entityCode, String recordId, String listKey);

    /** visible 决定是否展示入口，enabled 决定当前状态下能否执行。 */
    record RecordCapability(boolean visible, boolean enabled, String reason) { }
}
