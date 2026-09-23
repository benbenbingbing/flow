package com.workflow.entity.version.infrastructure.persistence.mapper;

import com.workflow.core.database.OffsetPage;
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

    /** 读取版本数据集的快照行，保持原始行顺序及记录编号排序。 */
    default List<EntityRecordVersionDatasetRow> findByDatasetId(String datasetId) {
        return selectList(Wrappers.<EntityRecordVersionDatasetRow>lambdaQuery()
                .eq(EntityRecordVersionDatasetRow::getDatasetId, datasetId)
                .orderByAsc(EntityRecordVersionDatasetRow::getRowOrder)
                .orderByAsc(EntityRecordVersionDatasetRow::getRecordId));
    }

    /** 分页读取数据集快照行，保留调用方给出的任意行偏移。 */
    default List<EntityRecordVersionDatasetRow> findPage(String datasetId, long offset, long limit) {
        return selectList(new OffsetPage<>(offset, limit), Wrappers.<EntityRecordVersionDatasetRow>lambdaQuery()
                .eq(EntityRecordVersionDatasetRow::getDatasetId, datasetId)
                .orderByAsc(EntityRecordVersionDatasetRow::getRowOrder, EntityRecordVersionDatasetRow::getRecordId));
    }

    /** 统计版本数据集的快照行数，供历史数据分页使用。 */
    default long countByDatasetId(String datasetId) {
        return selectCount(Wrappers.<EntityRecordVersionDatasetRow>lambdaQuery()
                .eq(EntityRecordVersionDatasetRow::getDatasetId, datasetId));
    }
}
