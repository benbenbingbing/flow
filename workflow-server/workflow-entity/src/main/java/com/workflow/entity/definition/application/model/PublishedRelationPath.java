package com.workflow.entity.definition.application.model;

import java.util.List;

/**
 * 由实体发布快照编译得到的不可变关系路径。
 *
 * <p>路径只保存稳定业务编码和精确实体发布版本。运行时必须重新校验每个
 * {@code historyId/schemaHash}，禁止回退到目标实体当前最新定义。</p>
 *
 * @param sourceEntityCode 来源实体编码，后续用于处理已发布关系路径时定位或关联目标
 * @param sourceHistoryId 来源历史ID，后续用于处理已发布关系路径时定位或关联目标
 * @param sourceSchemaHash 来源结构哈希，保存在对象中供后续校验、查询或展示
 * @param hops {@code hops}，保存在对象中供后续校验、查询或展示
 */
public record PublishedRelationPath(
        String sourceEntityCode,
        String sourceHistoryId,
        String sourceSchemaHash,
        List<Hop> hops) {

    /**
     * 初始化已发布关系路径，保存构造参数供后续方法使用。
     *
     * @param sourceEntityCode 来源实体编码，后续用于初始化已发布关系路径时定位或关联目标
     * @param sourceHistoryId 来源历史ID，后续用于初始化已发布关系路径时定位或关联目标
     * @param sourceSchemaHash 来源结构哈希，保存在对象中供后续校验、查询或展示
     * @param hops {@code hops}，保存在对象中供后续校验、查询或展示
     */
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
     *
     * @param type 类型标识，决定后续步骤{@code spec}采用的处理分支
     * @param code 业务编码，供后续匹配和引用
     * @param targetEntityId 目标实体ID，后续用于处理步骤{@code spec}时定位或关联目标
     */
    public record StepSpec(
            StepType type,
            String code,
            String targetEntityId) {
    }

    /**
     * 发布后冻结的一跳关系。
     *
     * @param index 索引，保存在对象中供后续校验、查询或展示
     * @param type 类型标识，决定后续跳采用的处理分支
     * @param code 业务编码，供后续匹配和引用
     * @param sourceEntityCode 来源实体编码，后续用于处理跳时定位或关联目标
     * @param sourceHistoryId 来源历史ID，后续用于处理跳时定位或关联目标
     * @param sourceSchemaHash 来源结构哈希，保存在对象中供后续校验、查询或展示
     * @param targetEntityCode 目标实体编码，后续用于处理跳时定位或关联目标
     * @param targetHistoryId 目标历史ID，后续用于处理跳时定位或关联目标
     * @param targetSchemaHash 目标结构哈希，保存在对象中供后续校验、查询或展示
     * @param sourceFieldCode 来源字段编码，后续用于处理跳时定位或关联目标
     * @param targetFieldCode 目标字段编码，后续用于处理跳时定位或关联目标
     * @param relationCode 关系编码，后续用于处理跳时定位或关联目标
     * @param ownershipType {@code ownership}类型标识，决定后续跳采用的处理分支
     * @param multiple {@code multiple}，保存在对象中供后续校验、查询或展示
     * @param sourceLinkField 来源链接字段，保存在对象中供后续校验、查询或展示
     * @param targetLinkField 目标链接字段，保存在对象中供后续校验、查询或展示
     */
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
