package com.workflow.entity.version.application.model;

import java.util.List;

/**
 * V2 固化范围预览，不写入任何版本数据。
 *
 * @param valid 有效，后续用于处理实体版本作用域预览时定位或关联目标
 * @param totalRows 总数行，保存在对象中供后续校验、查询或展示
 * @param estimatedBytes {@code estimated}字节，保存在对象中供后续校验、查询或展示
 * @param exceedsLimit {@code exceeds}上限，保存在对象中供后续校验、查询或展示
 * @param datasets {@code datasets}，保存在对象中供后续校验、查询或展示
 * @param warnings {@code warnings}，保存在对象中供后续校验、查询或展示
 */
public record EntityVersionScopePreview(
        boolean valid,
        Integer totalRows,
        Long estimatedBytes,
        boolean exceedsLimit,
        List<DatasetPreview> datasets,
        List<String> warnings) {

    /**
     * 封装数据集预览的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param nodeCode 节点编码，后续用于处理数据集预览时定位或关联目标
     * @param relationCode 关系编码，后续用于处理数据集预览时定位或关联目标
     * @param relationName 关系名称，后续用于处理数据集预览时匹配或展示
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityName 实体名称，后续用于处理数据集预览时匹配或展示
     * @param rowCount 行数量，保存在对象中供后续校验、查询或展示
     * @param maxRows 最大行，保存在对象中供后续校验、查询或展示
     * @param exceedsLimit {@code exceeds}上限，保存在对象中供后续校验、查询或展示
     */
    public record DatasetPreview(
            String nodeCode,
            String relationCode,
            String relationName,
            String entityCode,
            String entityName,
            Integer rowCount,
            Integer maxRows,
            boolean exceedsLimit) {
    }
}
