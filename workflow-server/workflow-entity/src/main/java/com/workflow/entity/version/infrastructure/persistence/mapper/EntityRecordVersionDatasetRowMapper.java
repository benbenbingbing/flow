package com.workflow.entity.version.infrastructure.persistence.mapper;

import com.workflow.core.database.mybatis.OffsetPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.entity.version.infrastructure.persistence.record.EntityRecordVersionDatasetRow;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/** V2 版本关系数据集行 Mapper。 */
// 普通查询使用 Wrapper；分页由 MyBatis-Plus 生成对应数据库语法。
@Mapper
public interface EntityRecordVersionDatasetRowMapper
        extends BaseMapper<EntityRecordVersionDatasetRow> {

    /**
     * 读取版本数据集的快照行，保持原始行顺序及记录编号排序。
     *
     * @param datasetId 数据集ID，后续用于查询数据集ID时定位或关联目标
     * @return 实体记录版本数据集行集合，供调用方遍历或展示
     */
    default List<EntityRecordVersionDatasetRow> findByDatasetId(String datasetId) {
        return selectList(Wrappers.<EntityRecordVersionDatasetRow>lambdaQuery()
                .eq(EntityRecordVersionDatasetRow::getDatasetId, datasetId)
                .orderByAsc(EntityRecordVersionDatasetRow::getRowOrder)
                .orderByAsc(EntityRecordVersionDatasetRow::getRecordId));
    }

    /**
     * 分页读取数据集快照行，保留调用方给出的任意行偏移。
     *
     * @param datasetId 数据集ID，后续用于查询实体记录版本数据集行分页时定位或关联目标
     * @param offset 偏移参数，用于限制后续查询范围和返回数量
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 实体记录版本数据集行集合，供调用方遍历或展示
     */
    default List<EntityRecordVersionDatasetRow> findPage(String datasetId, long offset, long limit) {
        return selectList(new OffsetPage<>(offset, limit), Wrappers.<EntityRecordVersionDatasetRow>lambdaQuery()
                .eq(EntityRecordVersionDatasetRow::getDatasetId, datasetId)
                .orderByAsc(EntityRecordVersionDatasetRow::getRowOrder, EntityRecordVersionDatasetRow::getRecordId));
    }

    /**
     * 统计版本数据集的快照行数，供历史数据分页使用。
     *
     * @param datasetId 数据集ID，后续用于统计数据集ID时定位或关联目标
     * @return 符合条件的数据集ID数量
     */
    default long countByDatasetId(String datasetId) {
        return selectCount(Wrappers.<EntityRecordVersionDatasetRow>lambdaQuery()
                .eq(EntityRecordVersionDatasetRow::getDatasetId, datasetId));
    }
}
