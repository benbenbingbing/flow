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

    /**
     * 列出父级实体ID；查询结果供调用方展示或继续处理。
     *
     * @param entityId 实体ID，后续用于列出父级实体ID时定位或关联目标
     * @return 实体关系集合，供调用方遍历或展示
     */
    @Transactional(readOnly = true)
    public List<EntityRelation> listByParentEntityId(String entityId) {
        EntityDefinition definition = definitionMapper.selectById(entityId);
        return list(definition);
    }

    /**
     * 列出父级实体编码；查询结果供调用方展示或继续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 实体关系集合，供调用方遍历或展示
     */
    @Transactional(readOnly = true)
    public List<EntityRelation> listByParentEntityCode(String entityCode) {
        EntityDefinition definition = definitionMapper
                .findByEntityCode(entityCode)
                .orElse(null);
        return list(definition);
    }

    /**
     * 列出实体已发布关系；查询结果供调用方展示或继续处理。
     *
     * @param definition 定义，作为 {@code snapshotService.findLatestByEntityCode} 的输入影响后续处理
     * @return 实体关系集合，供调用方遍历或展示
     */
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

    /**
     * 按关系编码查询实体关系；结果供后续展示或处理。
     *
     * @param parentEntityCode 父级实体编码，后续用于查询关系编码时定位或关联目标
     * @param relationCode 关系编码，后续用于查询关系编码时定位或关联目标
     * @return 符合条件的实体关系结果，供调用方继续处理
     */
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

    /**
     * 按绑定引用查询实体关系；结果供后续展示或处理。
     *
     * @param parentEntityId 父级实体ID，后续用于查询绑定引用时定位或关联目标
     * @param bindingRef 绑定引用，供本方法查询绑定引用时使用
     * @return 符合条件的实体关系结果，供调用方继续处理
     */
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

    /**
     * 生成有效数据键文本，供后续匹配或展示。
     *
     * @param relation 关系，供本方法处理有效数据键时使用
     * @return 处理后的有效数据键文本，供调用方比较或展示
     */
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
