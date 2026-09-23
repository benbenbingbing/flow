package com.workflow.entity.version.infrastructure.persistence.mapper;

import com.workflow.core.database.OffsetPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.entity.version.infrastructure.persistence.record.EntityRecordVersionDataset;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/** V2 版本关系数据集 Mapper。 */
// 普通查询使用 Wrapper；分页由 MyBatis-Plus 生成对应数据库语法。
@Mapper
public interface EntityRecordVersionDatasetMapper
        extends BaseMapper<EntityRecordVersionDataset> {

    /** 读取指定记录版本包含的数据集，按节点编码返回。 */
    default List<EntityRecordVersionDataset> findByVersionId(String versionId) {
        return selectList(Wrappers.<EntityRecordVersionDataset>lambdaQuery()
                .eq(EntityRecordVersionDataset::getVersionId, versionId)
                .orderByAsc(EntityRecordVersionDataset::getNodeCode));
    }

    default EntityRecordVersionDataset findByNodeCode(String versionId, String nodeCode) {
        return selectList(new OffsetPage<>(0, 1), Wrappers.<EntityRecordVersionDataset>lambdaQuery()
                .eq(EntityRecordVersionDataset::getVersionId, versionId)
                .eq(EntityRecordVersionDataset::getNodeCode, nodeCode))
                .stream().findFirst().orElse(null);
    }
}
