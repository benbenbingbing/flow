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

    /**
     * 读取指定记录版本包含的数据集，按节点编码返回。
     *
     * @param versionId 版本ID，后续用于查询版本ID时定位或关联目标
     * @return 实体记录版本数据集集合，供调用方遍历或展示
     */
    default List<EntityRecordVersionDataset> findByVersionId(String versionId) {
        return selectList(Wrappers.<EntityRecordVersionDataset>lambdaQuery()
                .eq(EntityRecordVersionDataset::getVersionId, versionId)
                .orderByAsc(EntityRecordVersionDataset::getNodeCode));
    }

    /**
     * 按节点编码查询实体记录版本数据集；结果供后续展示或处理。
     *
     * @param versionId 版本ID，后续用于查询节点编码时定位或关联目标
     * @param nodeCode 节点编码，后续用于查询节点编码时定位或关联目标
     * @return 符合条件的实体记录版本数据集结果，供调用方继续处理
     */
    default EntityRecordVersionDataset findByNodeCode(String versionId, String nodeCode) {
        return selectList(new OffsetPage<>(0, 1), Wrappers.<EntityRecordVersionDataset>lambdaQuery()
                .eq(EntityRecordVersionDataset::getVersionId, versionId)
                .eq(EntityRecordVersionDataset::getNodeCode, nodeCode))
                .stream().findFirst().orElse(null);
    }
}
