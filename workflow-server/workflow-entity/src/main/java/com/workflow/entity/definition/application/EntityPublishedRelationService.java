package com.workflow.entity.definition.application;

import com.workflow.entity.data.infrastructure.persistence.mapper.EntityRelationMapper;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 运行态实体关系解析器。
 *
 * <p>新发布优先使用不可变的 relationsSnapshot；V043 之前的发布没有该快照，
 * 才回退读取当前 entity_relation，以保持历史系统可运行。</p>
 */
@Service
@RequiredArgsConstructor
public class EntityPublishedRelationService {

    private final EntityPublishedSnapshotService snapshotService;
    private final EntityDefinitionMapper definitionMapper;
    private final EntityRelationMapper relationMapper;

    @Transactional(readOnly = true)
    public List<EntityRelation> listByParentEntityId(String entityId) {
        EntityDefinition definition = definitionMapper.selectById(entityId);
        return list(definition);
    }

    @Transactional(readOnly = true)
    public List<EntityRelation> listByParentEntityCode(String entityCode) {
        EntityDefinition definition = definitionMapper
                .findByEntityCode(entityCode)
                .orElse(null);
        return list(definition);
    }

    @Transactional(readOnly = true)
    public List<EntityRelation> list(EntityDefinition definition) {
        if (definition == null || !StringUtils.hasText(definition.getId())) {
            return List.of();
        }
        EntityPublishedSnapshot snapshot =
                snapshotService.findLatestByEntityCode(
                        definition.getEntityCode());
        if (snapshot != null && snapshot.isRelationsSnapshotAvailable()) {
            return snapshot.getRelations() == null
                    ? List.of() : List.copyOf(snapshot.getRelations());
        }
        List<EntityRelation> fallback =
                relationMapper.selectByParentEntityId(definition.getId());
        return fallback == null ? List.of() : List.copyOf(fallback);
    }

    @Transactional(readOnly = true)
    public EntityRelation findByRelationCode(
            String parentEntityCode,
            String relationCode) {
        if (!StringUtils.hasText(relationCode)) {
            return null;
        }
        return listByParentEntityCode(parentEntityCode).stream()
                .filter(relation -> relationCode.equals(
                        relation.getRelationCode()))
                .findFirst()
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public EntityRelation findByBindingRef(
            String parentEntityId,
            String bindingRef) {
        if (!StringUtils.hasText(bindingRef)) {
            return null;
        }
        return listByParentEntityId(parentEntityId).stream()
                .filter(relation -> bindingRef.equals(
                        relation.getRelationCode())
                        || bindingRef.equals(
                        relation.getParentFieldCode())
                        || bindingRef.equals(effectiveDataKey(relation)))
                .findFirst()
                .orElse(null);
    }

    public String effectiveDataKey(EntityRelation relation) {
        if (relation == null) {
            return null;
        }
        if (StringUtils.hasText(relation.getDataKey())) {
            return relation.getDataKey();
        }
        if (StringUtils.hasText(relation.getParentFieldCode())) {
            return relation.getParentFieldCode();
        }
        return relation.getRelationCode();
    }
}
