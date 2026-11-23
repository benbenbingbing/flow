package com.workflow.contracts.entity.port;

import com.workflow.contracts.entity.model.EntityRecordData;
import com.workflow.contracts.entity.model.EntityRecordPage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 保留宿主实体读取与数据权限语义的业务查询入口；调用方不得自行查询动态表。 */
public interface EntityRecordQueryPort {
    /** 按当前数据范围读取；不存在或不在范围内返回 null，实体访问被拒绝时抛授权异常。 */
    EntityRecordData findById(String entityCode, String recordId);

    /** 从已发布列表的数据范围查询详情。 */
    default EntityRecordData findById(String entityCode, String listKey, String recordId) {
        throw new UnsupportedOperationException("当前实现未提供指定列表的实体详情");
    }

    /** 使用发布字段及当前身份的数据权限查询；条件格式与实体运行时保持一致。 */
    List<EntityRecordData> findByCondition(String entityCode, Map<String, Object> condition);

    /** 按发布字段排序和筛选后分页；页码从 1 开始，有效页大小最多 200。 */
    default EntityRecordPage page(String entityCode, Map<String, Object> condition,
            int pageNumber, int pageSize, String sortField, boolean descending) {
        throw new UnsupportedOperationException("当前实现未提供实体分页");
    }

    /** 按已发布列表的入口权限和数据范围查询。 */
    default EntityRecordPage page(String entityCode, String listKey, Map<String, Object> condition,
            int pageNumber, int pageSize, String sortField, boolean descending) {
        throw new UnsupportedOperationException("当前实现未提供指定列表的实体分页");
    }

    /** 按当前数据范围批量读取主表记录；按输入顺序去重，缺失及不可见记录被略过。 */
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

    /** 统计授权范围内命中条件的记录，无需组装实体聚合。 */
    default long count(String entityCode, Map<String, Object> condition) {
        return findByCondition(entityCode, condition).size();
    }

    /** 判断当前数据范围内是否有命中记录。 */
    default boolean exists(String entityCode, Map<String, Object> condition) {
        return count(entityCode, condition) > 0;
    }

    /** 根据主记录保存的当前流程实例 ID 查询可见记录；历史轮次可先从流程查询取得业务引用。 */
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
}
