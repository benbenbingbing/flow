package com.workflow.entity.definition.application.model;

import java.util.List;

/**
 * 由实体发布快照编译得到的不可变关系路径。
 *
 * <p>路径只保存稳定业务编码和精确实体发布版本。运行时必须重新校验每个
 * {@code historyId/schemaHash}，禁止回退到目标实体当前最新定义。</p>
 */
public record PublishedRelationPath(
        String sourceEntityCode,
        String sourceHistoryId,
        String sourceSchemaHash,
        List<Hop> hops) {

    public PublishedRelationPath {
        hops = hops == null ? List.of() : List.copyOf(hops);
    }

    /** 配置端可选择的通用关联方式，不包含 SQL、脚本或任意表达式。 */
    public enum StepType {
        /** 按已发布实体关系从父记录查找子记录。 */
        RELATION,
        /** 读取当前实体的引用字段并定位目标记录。 */
        REFERENCE_FIELD,
        /** 在目标实体中查找引用当前记录的记录。 */
        REVERSE_REFERENCE
    }

    /**
     * 链接字段在钉定发布快照中的值形态。
     *
     * <p>运行时据此决定读取业务表标量列还是实体多值表，禁止再读取当前
     * {@code entity_field} 推断字段类型。</p>
     */
    public enum LinkValueType {
        SCALAR_REFERENCE,
        MULTI_REFERENCE
    }

    /**
     * 从精确实体发布快照提取的最小链接字段投影。
     *
     * @param fieldCode       发布字段编码
     * @param valueType       单值或多值引用
     * @param storageColumn   单值字段的物理列；多值字段固定为空
     * @param referenceEntityId 引用目标实体 ID
     */
    public record LinkField(
            String fieldCode,
            LinkValueType valueType,
            String storageColumn,
            String referenceEntityId) {
    }

    /**
     * 发布前的路径选择。反向引用需要 targetEntityId，其余类型由已发布定义
     * 自动推导目标实体。
     */
    public record StepSpec(
            StepType type,
            String code,
            String targetEntityId) {
    }

    /** 发布后冻结的一跳关系。 */
    public record Hop(
            int index,
            StepType type,
            String code,
            String sourceEntityCode,
            String sourceHistoryId,
            String sourceSchemaHash,
            String targetEntityCode,
            String targetHistoryId,
            String targetSchemaHash,
            String sourceFieldCode,
            String targetFieldCode,
            String relationCode,
            String ownershipType,
            boolean multiple,
            LinkField sourceLinkField,
            LinkField targetLinkField) {
    }
}
