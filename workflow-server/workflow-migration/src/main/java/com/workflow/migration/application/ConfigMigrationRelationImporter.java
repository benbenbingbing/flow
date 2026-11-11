package com.workflow.migration.application;

import com.workflow.entity.data.infrastructure.persistence.mapper.EntityRelationMapper;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.definition.api.request.EntityRelationSaveRequest;
import com.workflow.entity.definition.application.EntityRelationDefinitionService;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static com.workflow.migration.application.ConfigMigrationReferenceSupport.text;
import static com.workflow.migration.application.ConfigMigrationReferenceService.maps;

/** 独立实体关系按稳定编码恢复；必须在所有实体字段就绪后、表单节点校验前调用。 */
@Component
@RequiredArgsConstructor
public class ConfigMigrationRelationImporter {
    private final EntityDefinitionMapper entityMapper;
    private final EntityFieldMapper fieldMapper;
    private final EntityRelationMapper relationMapper;
    private final EntityRelationDefinitionService relationService;

    /**
     * 以目标实体和字段身份恢复关系，复用正常关系校验，不把关系 dataKey 创建成物理字段。
     * 包外关系保持原样；局部表单包未携带 relations 时使用目标已有关系。
     *
     * @param parent 已创建且字段已应用的目标父实体
     * @param snapshot 当前选择范围内的实体快照
     * @param mapping 环境编码映射，用于解析子实体
     */
    public void apply(EntityDefinition parent, Map<String, Object> snapshot,
                      BiFunction<String, String, String> mapping) {
        for (Map<String, Object> source : maps(snapshot.get("relations"))) {
            String code = text(source.get("relationCode"));
            if (!StringUtils.hasText(code)) throw new IllegalArgumentException("迁移关系缺少 relationCode");
            String childCode = mapping.apply("ENTITY", text(source.get("childEntityCode")));
            EntityDefinition child = entityMapper.findByEntityCode(childCode)
                    .orElseThrow(() -> new IllegalArgumentException("关系子实体不存在: " + childCode));
            EntityRelationSaveRequest request = new EntityRelationSaveRequest();
            request.setRelationCode(code);
            request.setRelationName(text(source.get("relationName")));
            request.setDataKey(text(source.get("dataKey")));
            request.setChildEntityId(child.getId());
            request.setChildRefFieldCode(text(source.get("childRefFieldCode")));
            String type = text(source.get("relationType"));
            if (!type.isBlank()) request.setRelationType(EntityRelation.RelationType.valueOf(type));
            String ownership = text(source.get("ownershipType"));
            if (!ownership.isBlank()) request.setOwnershipType(EntityRelation.OwnershipType.valueOf(ownership));
            request.setCascadeDelete(flag(source.get("cascadeDelete")));
            request.setRequired(flag(source.get("required")));
            request.setEnabled(!source.containsKey("enabled") || flag(source.get("enabled")));
            if (source.get("sortOrder") != null) request.setSortOrder(Integer.valueOf(text(source.get("sortOrder"))));
            String fieldCode = text(source.get("parentFieldCode"));
            if (!fieldCode.isBlank()) {
                var field = fieldMapper.findByEntityIdAndFieldCode(parent.getId(), fieldCode);
                if (field == null) throw new IllegalArgumentException("关系父字段不存在: " + fieldCode);
                request.setParentFieldId(field.getId());
                request.setParentFieldCode(fieldCode);
            }
            EntityRelation existing = relationMapper.selectByRelationCode(parent.getId(), code);
            if (existing == null) relationService.create(parent.getId(), request);
            else relationService.update(parent.getId(), existing.getId(), request);
        }
    }

    /** 回滚只停用本批次新增的关系，保留数据及目标独有关系，避免删除稳定关系编码。 */
    public void disableAdded(EntityDefinition parent, Map<String, Object> imported,
                             Map<String, Object> restored) {
        Set<String> restoredCodes = maps(restored.get("relations")).stream()
                .map(value -> text(value.get("relationCode"))).collect(Collectors.toSet());
        for (Map<String, Object> source : maps(imported.get("relations"))) {
            String code = text(source.get("relationCode"));
            if (restoredCodes.contains(code)) continue;
            EntityRelation existing = relationMapper.selectByRelationCode(parent.getId(), code);
            if (existing != null) {
                existing.setEnabled(false);
                relationMapper.updateById(existing);
            }
        }
    }

    private static boolean flag(Object value) {
        return Boolean.TRUE.equals(value) || "1".equals(text(value)) || "true".equalsIgnoreCase(text(value));
    }
}
