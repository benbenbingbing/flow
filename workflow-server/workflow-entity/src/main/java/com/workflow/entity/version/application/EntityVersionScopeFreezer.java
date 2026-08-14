package com.workflow.entity.version.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldOptionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityFieldOption;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把版本范围解析为不可变的一层数据图。
 *
 * <p>发布后捕获只使用这里冻结的关系键、中文名称和字段结构，不再通过
 * SUB_FORM/SUB_LIST 或当前实体字段推断范围。</p>
 */
@Component
@RequiredArgsConstructor
public class EntityVersionScopeFreezer {

    private static final Set<String> FILTER_SYSTEM_FIELDS = Set.of(
            "id", "entityCode", "dataNo", "title", "name", "code",
            "status", "processInstanceId", "processStartTime",
            "processEndTime", "currentTaskId", "currentTaskName",
            "currentTaskAssignee", "submitterId", "submitterName",
            "deptId", "deptName", "submitTime", "createdAt",
            "updatedAt", "createdBy", "updatedBy");

    private final EntityPublishedSnapshotService snapshotService;
    private final ObjectMapper objectMapper;
    private final EntityFieldOptionMapper optionMapper;

    public EntityVersionConfiguration freeze(
            EntityVersionConfiguration source) {
        EntityVersionConfiguration document = copy(source);
        EntityPublishedSnapshot root = snapshotService
                .getLatestByEntityCode(document.getEntityCode());
        EntityVersionConfiguration.SnapshotScope scope =
                document.getSnapshotScope();
        if (scope == null) {
            scope = new EntityVersionConfiguration.SnapshotScope();
            document.setSnapshotScope(scope);
        }
        EntityVersionConfiguration.ScopeNode rootNode = scope.getRoot();
        if (rootNode == null) {
            rootNode = new EntityVersionConfiguration.ScopeNode();
            scope.setRoot(rootNode);
        }
        rootNode.setNodeCode("ROOT");
        rootNode.setEntityCode(root.getEntityCode());
        rootNode.setEntityName(root.getEntityName());
        rootNode.setEntityReleaseId(root.getHistoryId());
        rootNode.setEntityReleaseVersion(root.getVersion());
        rootNode.setFields(selectFields(
                root.getFields(),
                rootNode.getFieldMode(),
                rootNode.getFieldCodes()));

        Map<String, EntityRelation> relations = new LinkedHashMap<>();
        for (EntityRelation relation : safe(root.getRelations())) {
            if (relation != null
                    && StringUtils.hasText(relation.getRelationCode())
                    && !Boolean.FALSE.equals(relation.getEnabled())
                    && relation.getOwnershipType()
                            == EntityRelation.OwnershipType.COMPOSITION) {
                relations.put(relation.getRelationCode(), relation);
            }
        }
        List<EntityVersionConfiguration.RelationScope> frozenRelations =
                new ArrayList<>();
        Set<String> nodeCodes = new LinkedHashSet<>();
        for (EntityVersionConfiguration.RelationScope relationScope
                : safe(scope.getRelations())) {
            if (Boolean.FALSE.equals(relationScope.getEnabled())) {
                continue;
            }
            EntityRelation relation = relations.get(
                    relationScope.getRelationCode());
            if (relation == null) {
                throw new IllegalArgumentException(
                        "固化范围引用了未发布关系: "
                                + relationScope.getRelationCode());
            }
            EntityPublishedSnapshot child = snapshotService
                    .getLatestByEntityCode(relation.getChildEntityCode());
            validateAndNormalizeFilter(
                    relationScope.getFilter(),
                    child.getFields(),
                    relation.getRelationName());
            String nodeCode = text(relationScope.getNodeCode());
            if (nodeCode == null) {
                nodeCode = "REL_" + relation.getRelationCode();
            }
            if ("ROOT".equalsIgnoreCase(nodeCode)
                    || !nodeCodes.add(nodeCode)) {
                throw new IllegalArgumentException(
                        "固化范围节点编码重复或保留: " + nodeCode);
            }
            relationScope.setNodeCode(nodeCode);
            relationScope.setEntityCode(child.getEntityCode());
            relationScope.setEntityName(child.getEntityName());
            relationScope.setEntityReleaseId(child.getHistoryId());
            relationScope.setEntityReleaseVersion(child.getVersion());
            relationScope.setRelationName(relation.getRelationName());
            relationScope.setChildEntityCode(child.getEntityCode());
            relationScope.setChildEntityName(child.getEntityName());
            relationScope.setDataKey(firstText(
                    relation.getDataKey(),
                    relation.getParentFieldCode(),
                    relation.getRelationCode()));
            relationScope.setChildRefFieldCode(
                    relation.getChildRefFieldCode());
            relationScope.setRelationType(
                    relation.getRelationType() == null
                            ? null : relation.getRelationType().name());
            relationScope.setFields(selectFields(
                    child.getFields(),
                    relationScope.getFieldMode(),
                    relationScope.getFieldCodes()));
            frozenRelations.add(relationScope);
        }
        frozenRelations.sort(Comparator.comparing(
                EntityVersionConfiguration.RelationScope::getNodeCode));
        scope.setRelations(frozenRelations);
        scope.setScopeHash(hash(scopeMaterial(scope)));
        document.setSchemaVersion(2);
        document.setMigrationState("MIGRATED");
        document.setRelationOptions(List.of());
        document.setFieldOptions(List.of());
        return document;
    }

    public EntityVersionConfiguration enrichDraftOptions(
            EntityVersionConfiguration document) {
        if (document == null || !StringUtils.hasText(document.getEntityCode())) {
            return document;
        }
        EntityPublishedSnapshot root = snapshotService
                .getLatestByEntityCode(document.getEntityCode());
        document.setFieldOptions(selectFields(
                root.getFields(), "ALL_PUBLISHED", List.of()));
        List<EntityVersionConfiguration.RelationOption> options =
                new ArrayList<>();
        for (EntityRelation relation : safe(root.getRelations())) {
            if (relation == null
                    || Boolean.FALSE.equals(relation.getEnabled())
                    || relation.getOwnershipType()
                            != EntityRelation.OwnershipType.COMPOSITION
                    || !StringUtils.hasText(relation.getRelationCode())
                    || !StringUtils.hasText(relation.getChildEntityCode())) {
                continue;
            }
            EntityPublishedSnapshot child = snapshotService
                    .getLatestByEntityCode(relation.getChildEntityCode());
            EntityVersionConfiguration.RelationOption option =
                    new EntityVersionConfiguration.RelationOption();
            option.setRelationCode(relation.getRelationCode());
            option.setRelationName(relation.getRelationName());
            option.setChildEntityCode(child.getEntityCode());
            option.setChildEntityName(child.getEntityName());
            option.setRelationType(relation.getRelationType() == null
                    ? null : relation.getRelationType().name());
            option.setFields(selectFields(
                    child.getFields(), "ALL_PUBLISHED", List.of()));
            options.add(option);
        }
        document.setRelationOptions(options);
        return document;
    }

    private List<EntityVersionConfiguration.FieldPresentation> selectFields(
            List<EntityField> fields,
            String fieldMode,
            List<String> selectedCodes) {
        Set<String> selected = new LinkedHashSet<>(
                selectedCodes == null ? List.of() : selectedCodes);
        boolean all = !"SELECTED".equalsIgnoreCase(fieldMode);
        List<EntityVersionConfiguration.FieldPresentation> result =
                new ArrayList<>();
        for (EntityField field : safe(fields)) {
            if (field == null
                    || !StringUtils.hasText(field.getFieldCode())
                    || field.getFieldType() == EntityField.FieldType.SUB_FORM
                    || field.getFieldType() == EntityField.FieldType.SUB_LIST
                    || (!all && !selected.contains(field.getFieldCode()))) {
                continue;
            }
            EntityVersionConfiguration.FieldPresentation presentation =
                    new EntityVersionConfiguration.FieldPresentation();
            presentation.setFieldCode(field.getFieldCode());
            presentation.setFieldName(field.getFieldName());
            presentation.setFieldLabel(field.getFieldName());
            presentation.setFieldType(field.getFieldType() == null
                    ? "UNKNOWN" : field.getFieldType().name());
            presentation.setSortOrder(field.getSortOrder() == null
                    ? 0 : field.getSortOrder());
            presentation.setRenderHint(renderHint(field.getFieldType()));
            presentation.setDictType(field.getDictType());
            presentation.setOptionLabels(readOptionLabels(field));
            result.add(presentation);
        }
        result.sort(Comparator
                .comparing(EntityVersionConfiguration.FieldPresentation::getSortOrder)
                .thenComparing(EntityVersionConfiguration.FieldPresentation::getFieldCode));
        if (!all) {
            Set<String> resolved = result.stream()
                    .map(EntityVersionConfiguration.FieldPresentation::getFieldCode)
                    .collect(java.util.stream.Collectors.toSet());
            for (String code : selected) {
                if (!resolved.contains(code)) {
                    throw new IllegalArgumentException(
                            "固化范围引用了未发布或不可持久化字段: " + code);
                }
            }
        }
        return result;
    }

    private void validateAndNormalizeFilter(
            EntityVersionConfiguration.FixedFilter filter,
            List<EntityField> childFields,
            String relationName) {
        if (filter == null || filter.getConditions() == null) {
            return;
        }
        Set<String> allowed = new LinkedHashSet<>(FILTER_SYSTEM_FIELDS);
        for (EntityField field : safe(childFields)) {
            if (field != null && StringUtils.hasText(field.getFieldCode())) {
                allowed.add(field.getFieldCode());
            }
        }
        for (EntityVersionConfiguration.FilterCondition condition
                : filter.getConditions()) {
            String code = text(condition.getFieldCode());
            if (code != null && code.startsWith("data.")) {
                code = text(code.substring("data.".length()));
            }
            if (code == null || !allowed.contains(code)) {
                throw new IllegalArgumentException(
                        "关系 " + firstText(relationName, "未命名关系")
                                + " 的固定过滤字段未发布或不存在: "
                                + condition.getFieldCode());
            }
            condition.setFieldCode(code);
        }
    }

    private Map<String, Object> scopeMaterial(
            EntityVersionConfiguration.SnapshotScope scope) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("root", nodeScopeMaterial(scope.getRoot()));
        result.put("relations", safe(scope.getRelations()).stream()
                .map(this::relationScopeMaterial)
                .toList());
        result.put("limits", scope.getLimits());
        return result;
    }

    private Map<String, Object> nodeScopeMaterial(
            EntityVersionConfiguration.ScopeNode node) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodeCode", node.getNodeCode());
        result.put("entityCode", node.getEntityCode());
        result.put("fieldCodes", safe(node.getFields()).stream()
                .map(EntityVersionConfiguration.FieldPresentation::getFieldCode)
                .toList());
        return result;
    }

    private Map<String, Object> relationScopeMaterial(
            EntityVersionConfiguration.RelationScope relation) {
        Map<String, Object> result = nodeScopeMaterial(relation);
        result.put("relationCode", relation.getRelationCode());
        result.put("childEntityCode", relation.getChildEntityCode());
        result.put("dataKey", relation.getDataKey());
        result.put("childRefFieldCode", relation.getChildRefFieldCode());
        result.put("relationType", relation.getRelationType());
        result.put("filter", relation.getFilter());
        result.put("maxRows", relation.getMaxRows());
        return result;
    }

    private Map<String, String> readOptionLabels(EntityField field) {
        Map<String, String> structured = new LinkedHashMap<>();
        if (StringUtils.hasText(field.getId())) {
            for (EntityFieldOption option
                    : optionMapper.findByFieldId(field.getId())) {
                if (StringUtils.hasText(option.getOptionValue())) {
                    structured.put(option.getOptionValue(),
                            firstText(option.getOptionLabel(),
                                    option.getOptionValue()));
                }
            }
        }
        if (!structured.isEmpty()) {
            return structured;
        }
        String json = field.getOptionsJson();
        if (!StringUtils.hasText(json)) {
            return structured;
        }
        try {
            List<Map<String, Object>> values = objectMapper.readValue(
                    json, new TypeReference<>() { });
            Map<String, String> result = new LinkedHashMap<>();
            for (Map<String, Object> value : values) {
                String code = firstText(
                        text(value.get("value")),
                        text(value.get("optionValue")),
                        text(value.get("code")));
                String label = firstText(
                        text(value.get("label")),
                        text(value.get("optionLabel")),
                        text(value.get("name")), code);
                if (code != null) {
                    result.put(code, label);
                }
            }
            return result;
        } catch (JsonProcessingException ignored) {
            return new LinkedHashMap<>();
        }
    }

    private String renderHint(EntityField.FieldType type) {
        if (type == null) {
            return "TEXT";
        }
        return switch (type) {
            case TEXT, RICH_TEXT -> "LONG_TEXT";
            case SELECT, MULTI_SELECT, RADIO, CHECKBOX -> "TAG";
            case FILE, IMAGE -> "ATTACHMENT";
            case USER, DEPT, REFERENCE, MULTI_REFERENCE -> "REFERENCE";
            default -> "TEXT";
        };
    }

    private EntityVersionConfiguration copy(EntityVersionConfiguration source) {
        return objectMapper.convertValue(source, EntityVersionConfiguration.class);
    }

    private String hash(Object value) {
        try {
            byte[] input = objectMapper.writeValueAsString(value)
                    .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(input));
        } catch (Exception exception) {
            throw new IllegalStateException("数据版本范围摘要计算失败", exception);
        }
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String result = String.valueOf(value).trim();
        return result.isEmpty() ? null : result;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
