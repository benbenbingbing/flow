package com.workflow.entity.version.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.version.infrastructure.persistence.record.EntityRecordVersionDataset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** V2 版本关系数据集 Mapper。 */
@Mapper
public interface EntityRecordVersionDatasetMapper
        extends BaseMapper<EntityRecordVersionDataset> {

    @Select("""
            SELECT * FROM entity_record_version_dataset
            WHERE version_id = #{versionId}
            ORDER BY node_code ASC
            """)
    List<EntityRecordVersionDataset> findByVersionId(
            @Param("versionId") String versionId);

    @Select("""
            SELECT * FROM entity_record_version_dataset
            WHERE version_id = #{versionId}
              AND node_code = #{nodeCode}
            LIMIT 1
            """)
    EntityRecordVersionDataset findByNodeCode(
            @Param("versionId") String versionId,
            @Param("nodeCode") String nodeCode);
}
