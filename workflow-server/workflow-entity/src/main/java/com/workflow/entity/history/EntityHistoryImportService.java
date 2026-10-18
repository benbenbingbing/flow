package com.workflow.entity.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.contracts.history.*;
import com.workflow.contracts.history.HistoryImportModels.*;
import com.workflow.contracts.entity.mutation.model.*;
import com.workflow.contracts.entity.mutation.port.EntityMutationPort;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityPermissionAction;
import com.workflow.core.error.BusinessConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** 通过受控变更管道迁入业务记录；不进入表单事件链，不创建流程实例。 */
@Service
@RequiredArgsConstructor
public class EntityHistoryImportService implements EntityHistoryImportPort {
    private final EntityDefinitionMapper definitions;
    private final EntityPublishedSnapshotService snapshots;
    private final EntityActionCapabilityService permissions;
    private final EntityMutationPort mutations;
    private final com.workflow.entity.data.application.EntityDataDynamicService records;
    private final com.workflow.entity.history.infrastructure.persistence.EntityHistorySourceMapper sources;
    private final ObjectMapper json;
    private final com.workflow.entity.data.application.EntityRelationRuntimeService relations;
    private final com.workflow.entity.definition.infrastructure.persistence.mapper.EntityCodeRuleMapper codeRules;
    private final com.workflow.entity.data.application.DynamicTableService tables;

    @Override
    public List<EntitySchema> schemas() {
        return definitions.selectList(Wrappers.<EntityDefinition>lambdaQuery()
                        .eq(EntityDefinition::getStatus, EntityDefinition.Status.PUBLISHED)).stream()
                .filter(d -> com.workflow.admin.authorization.application.PermissionUtil.hasPermission(
                        "entity:" + d.getEntityCode() + ":import"))
                .filter(d -> d.getStorageMode() != EntityDefinition.StorageMode.SYSTEM)
                .map(d -> schema(d.getEntityCode())).toList();
    }

    /** 固定发布字段和类型；未支持的复合字段明确排除，必填时禁止该实体迁入。 */
    @Override
    public EntitySchema schema(String entityCode) {
        permissions.requireEntityMetadataAccess(entityCode);
        var snapshot = snapshots.getLatestByEntityCode(entityCode);
        List<Field> fields = new ArrayList<>();
        for (EntityField field : snapshot.getFields()) {
            if (Boolean.TRUE.equals(field.getIsSystem()) && !Set.of("name", "status", "deptId").contains(field.getFieldCode())) continue;
            String type = field.getFieldType().name();
            if (Set.of("SUB_FORM", "SUB_LIST").contains(type)) continue;
            if (Set.of("sourceRecordId", "groupKey").contains(field.getFieldCode()))
                throw new IllegalArgumentException("实体字段与迁移保留列重名: " + field.getFieldCode());
            String reference = field.getRefEntityCode();
            if (field.getRefEntityId() != null) {
                var target = definitions.selectById(field.getRefEntityId());
                if (target != null) reference = target.getEntityCode();
            }
            fields.add(new Field(field.getFieldCode(), field.getFieldName(), type,
                    Boolean.TRUE.equals(field.getIsRequired()), reference));
        }
        var publishedRelations = snapshot.getRelations().stream().filter(r -> !Boolean.FALSE.equals(r.getEnabled()))
                .map(r -> new Relation(r.getRelationName(), r.getChildEntityCode(), r.getChildRefFieldCode(),
                        r.getOwnershipType() == com.workflow.entity.data.infrastructure.persistence.record.EntityRelation.OwnershipType.COMPOSITION,
                        Boolean.TRUE.equals(r.getRequired()), r.getRelationType() == com.workflow.entity.data.infrastructure.persistence.record.EntityRelation.RelationType.ONE_TO_ONE)).toList();
        return new EntitySchema(entityCode, snapshot.getEntityName(),
                HistoryIdentity.hash(String.valueOf(snapshot.getVersion()), encode(fields), encode(publishedRelations)), fields, publishedRelations);
    }

    @Override
    public void validateImport(String entityCode) {
        permissions.requireStandardPermission(entityCode, EntityPermissionAction.IMPORT);
        permissions.requireStandardPermission(entityCode, EntityPermissionAction.CREATE);
        var definition = definitions.findByEntityCode(entityCode).orElseThrow(() -> new IllegalArgumentException("实体不存在"));
        if (definition.getStorageMode() == EntityDefinition.StorageMode.SYSTEM || definition.getStatus() != EntityDefinition.Status.PUBLISHED)
            throw new IllegalArgumentException("历史导入只支持已发布的动态业务实体");
        // 组事务内禁止自动建表，避免 MySQL DDL 隐式提交破坏整组回滚。
        if (!tables.tableExists(entityCode)) throw new IllegalArgumentException("实体物理表未就绪，请先完成实体发布");
        if (codeRules.findByEntityCode(entityCode).map(rule -> "CUSTOM".equals(rule.getGenerationMode())).orElse(false))
            throw new IllegalArgumentException("历史导入不能执行自定义编码生成器，请使用规则取号实体: " + entityCode);
    }

    @Override
    public SourceRecord findSource(String system, String key, String sourceId) {
        var found = sources.find(HistoryIdentity.hash(system, key, sourceId));
        if (found.isEmpty()) return null;
        readable(found.get(0).entityCode(), found.get(0).recordId());
        return found.get(0);
    }

    /** 输入只能包含发布字段，保留 ID 的大小写和前导零；不允许夹带系统运行态字段。 */
    @Override
    public Map<String, Object> normalize(EntitySchema schema, Map<String, Object> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        var known = new HashSet<String>();
        for (Field field : schema.fields()) {
            known.add(field.code());
            Object value = values.get(field.code());
            if (Set.of("FILE", "IMAGE").contains(field.type()) && (field.required() || value != null && !value.toString().isBlank()))
                throw new IllegalArgumentException("本期不支持附件字段迁入: " + field.name());
            if (value == null || value instanceof String s && s.isBlank()) {
                if (field.required()) throw new IllegalArgumentException("缺少必填字段: " + field.name());
                continue;
            }
            try {
                Object converted = switch (field.type()) {
                    case "INTEGER" -> new BigDecimal(value.toString()).intValueExact();
                    case "LONG" -> new BigDecimal(value.toString()).longValueExact();
                    case "DECIMAL" -> new BigDecimal(value.toString());
                    case "BOOLEAN" -> parseBoolean(value.toString());
                    case "DATE" -> value.toString().contains("T")
                            ? LocalDateTime.parse(value.toString()).toLocalDate().toString() : LocalDate.parse(value.toString()).toString();
                    case "DATETIME" -> LocalDateTime.parse(value.toString().replace(' ', 'T')).toString();
                    case "MULTI_REFERENCE", "MULTI_SELECT", "CHECKBOX" -> value instanceof List<?> list
                            ? list.stream().map(Object::toString).toList() : Arrays.stream(value.toString().split(";")).filter(s -> !s.isBlank()).toList();
                    default -> value.toString();
                };
                result.put(field.code(), converted);
            } catch (RuntimeException invalid) { throw new IllegalArgumentException("字段格式错误: " + field.name(), invalid); }
        }
        for (String key : values.keySet()) if (!known.contains(key)) throw new IllegalArgumentException("不可导入字段: " + key);
        return result;
    }

    private boolean parseBoolean(String value) {
        if (Set.of("true", "1", "是").contains(value.toLowerCase(Locale.ROOT))) return true;
        if (Set.of("false", "0", "否").contains(value.toLowerCase(Locale.ROOT))) return false;
        throw new IllegalArgumentException("布尔值必须为 true/false、1/0 或 是/否");
    }

    /** 来源映射和实体必须处于同一事务，数据库唯一键决定跨批次重试能否复用。 */
    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public EntityResult create(String system, EntitySpec spec, EntityRow row, String sourceHash,
                               Map<String, Object> values, String batchId) {
        validateImport(spec.entityCode());
        SourceRecord existing = findSource(system, spec.key(), row.sourceId());
        if (existing != null) {
            if (!existing.entityCode().equals(spec.entityCode()) || !existing.contentHash().equals(sourceHash))
                throw new BusinessConflictException("HISTORY_SOURCE_CONFLICT", "同一来源记录已存在且内容或配置不同: " + row.sourceId());
            return new EntityResult(existing.recordId(), true, readable(spec.entityCode(), existing.recordId()));
        }
        var schema = schema(spec.entityCode());
        Map<String, Object> normalized = normalize(schema, values);
        for (Field field : schema.fields()) {
            if (field.referenceEntity() == null || !normalized.containsKey(field.code())) continue;
            Object value = normalized.get(field.code());
            for (Object id : value instanceof List<?> list ? list : List.of(value)) readable(field.referenceEntity(), id.toString());
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("data", normalized);
        for (String standard : List.of("name", "status", "deptId")) if (normalized.containsKey(standard)) payload.put(standard, normalized.get(standard));
        payload.put("startProcess", false);
        String identity = HistoryIdentity.hash(system, spec.key(), row.sourceId());
        var context = EntityMutationContext.builder(EntityMutationSourceType.IMPORT, "HISTORICAL_IMPORT", "历史业务数据导入")
                .sourceId(batchId).trace("history_" + identity, "history_" + identity).build();
        com.workflow.contracts.entity.mutation.model.EntityMutationResult saved;
        // 仅延迟必填子集合检查，字段、唯一性和写权限仍立即校验；事务提交前必定复核。
        try (var ignored = HistoryRelationValidationScope.open()) {
            saved = mutations.execute(EntityMutationCommand.create(spec.entityCode(), payload, context));
        }
        String createdId = saved.recordId();
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void beforeCommit(boolean readOnly) {
                        relations.validateImportedRelations(spec.entityCode(), createdId);
                    }
                });
        sources.insert(
                identity, system, spec.key(), row.sourceId(), spec.entityCode(), saved.recordId(), sourceHash, spec.readonly() ? 1 : 0, batchId);
        return new EntityResult(saved.recordId(), false, saved.record());
    }

    @Override
    public Map<String, Object> readable(String entityCode, String recordId) {
        permissions.requireStandardPermission(entityCode, EntityPermissionAction.VIEW);
        var record = records.findAccessibleById(entityCode, recordId, null);
        if (record == null) throw new IllegalArgumentException("关联实体记录不存在或不可读");
        Map<String, Object> result = new LinkedHashMap<>(record.getData() == null ? Map.of() : record.getData());
        result.put("name", record.getName()); result.put("status", record.getStatus());
        return result;
    }

    private String encode(Object value) {
        try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalArgumentException("实体配置无法编码", e); }
    }
}
