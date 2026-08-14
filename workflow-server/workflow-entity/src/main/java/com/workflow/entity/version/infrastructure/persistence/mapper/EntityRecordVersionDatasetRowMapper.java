package com.workflow.entity.version.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.version.infrastructure.persistence.record.EntityRecordVersionDatasetRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** V2 版本关系数据集行 Mapper。 */
@Mapper
public interface EntityRecordVersionDatasetRowMapper
        extends BaseMapper<EntityRecordVersionDatasetRow> {

    @Select("""
            SELECT * FROM entity_record_version_dataset_row
            WHERE dataset_id = #{datasetId}
            ORDER BY row_order ASC, record_id ASC
            """)
    List<EntityRecordVersionDatasetRow> findByDatasetId(
            @Param("datasetId") String datasetId);

    @Select("""
            SELECT * FROM entity_record_version_dataset_row
            WHERE dataset_id = #{datasetId}
            ORDER BY row_order ASC, record_id ASC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<EntityRecordVersionDatasetRow> findPage(
            @Param("datasetId") String datasetId,
            @Param("offset") long offset,
            @Param("limit") long limit);

    @Select("""
            SELECT COUNT(*) FROM entity_record_version_dataset_row
            WHERE dataset_id = #{datasetId}
            """)
    long countByDatasetId(@Param("datasetId") String datasetId);
}
