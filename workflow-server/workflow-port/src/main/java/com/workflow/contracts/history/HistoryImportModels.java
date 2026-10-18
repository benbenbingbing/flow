package com.workflow.contracts.history;

import java.util.List;
import java.util.Map;

/** 历史文件导入的跨模块契约；来源身份与目标主键分离，归档 ID 不进入流程引擎。 */
public final class HistoryImportModels {
    private HistoryImportModels() {}

    /** 每个实体拥有独立表页；references 将字段中的旧 ID 转换为目标实体主键。 */
    public record EntitySpec(String key, String entityCode, boolean readonly,
                             Map<String, String> references) {
        public EntitySpec { references = mappings(references); }
    }
    /** 一个方案支持多个业务实体；processVersionId 为空时仅导入业务数据。 */
    public record Plan(String name, String sourceSystem, List<EntitySpec> entities,
                       String processVersionId, Map<String, String> nodeMappings,
                       Map<String, String> actionMappings) {
        public Plan { nodeMappings = mappings(nodeMappings); actionMappings = mappings(actionMappings); }
    }
    /** groupKey 使主记录及从属记录在同一事务提交；共享记录可独立分组。 */
    public record EntityRow(String entityKey, String sourceId, String groupKey,
                            Map<String, Object> values) {}
    public record InstanceRow(String sourceId, String entityKey, String recordId, String name,
                              String initiator, String startedAt, String endedAt, String result,
                              int expectedEvents) {}
    public record EventRow(String sourceId, String instanceId, String node, String actor,
                           String action, String comment, String handledAt, int sequence) {}
    public record Input(List<EntityRow> entities, List<InstanceRow> instances, List<EventRow> events) {}
    /** 只暴露可导入字段；标签和类型随归档固定，避免展示依赖最新配置。 */
    public record Field(String code, String name, String type, boolean required, String referenceEntity) {}
    /** 发布关系随预检冻结，用于主从同组及必填/一对一约束检查。 */
    public record Relation(String name, String childEntity, String childField, boolean composition,
                           boolean required, boolean single) {}
    public record EntitySchema(String code, String name, String fingerprint, List<Field> fields,
                               List<Relation> relations) {}
    public record EntityResult(String id, boolean reused, Map<String, Object> values) {}
    public record SourceRecord(String entityCode, String recordId, String contentHash) {}
    public record ProcessTemplate(String id, String name, int version, String xml, Map<String, String> nodes) {}
    /** 关联实体的迁入时快照；查看仍需逐条校验当前实体数据权限。 */
    public record RelatedRecord(String entityCode, String recordId, Map<String, Object> data, EntitySchema schema) {}
    public record Archive(String sourceSystem, InstanceRow instance, String entityCode, String recordId,
                          ProcessTemplate template, Map<String, Object> data, EntitySchema schema,
                          Map<String, String> nodeMappings, Map<String, String> actionMappings,
                          List<EventRow> events, List<RelatedRecord> related) {}

    /** 清空下拉选项等同删除映射；统一处理后幂等摘要不会受空键值影响。 */
    private static Map<String, String> mappings(Map<String, String> input) {
        if (input == null) return Map.of();
        var result = new java.util.LinkedHashMap<String, String>();
        input.forEach((key, value) -> { if (key != null && value != null && !value.isBlank()) result.put(key, value); });
        return java.util.Collections.unmodifiableMap(result);
    }
}
