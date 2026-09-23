package com.workflow.entity.data.infrastructure.persistence.mapper;

import com.workflow.core.database.OffsetPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.entity.data.infrastructure.persistence.record.EntityFlowStatusMapping;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 实体流程状态映射 Mapper
 */
// 普通查询使用 Wrapper；分页由 MyBatis-Plus 生成对应数据库语法。
@Mapper
public interface EntityFlowStatusMappingMapper extends BaseMapper<EntityFlowStatusMapping> {

    /**
     * 根据流程配置ID查询状态映射
     *
     * @param processConfigId 流程配置ID，后续用于查询流程配置ID时定位或关联目标
     * @return 实体流程状态映射集合，供调用方遍历或展示
     */
    default List<EntityFlowStatusMapping> findByProcessConfigId(String processConfigId) {
        return selectList(Wrappers.<EntityFlowStatusMapping>lambdaQuery()
                .eq(EntityFlowStatusMapping::getProcessConfigId, processConfigId)
                .eq(EntityFlowStatusMapping::getDeleted, 0)
                .orderByAsc(EntityFlowStatusMapping::getSortOrder));
    }

    /**
     * 根据流程标识查询状态映射
     *
     * @param processKey 流程键，后续用于授权校验、关联或幂等去重
     * @return 实体流程状态映射集合，供调用方遍历或展示
     */
    default List<EntityFlowStatusMapping> findByProcessKey(String processKey) {
        return selectList(Wrappers.<EntityFlowStatusMapping>lambdaQuery()
                .eq(EntityFlowStatusMapping::getProcessKey, processKey)
                .eq(EntityFlowStatusMapping::getDeleted, 0)
                .orderByAsc(EntityFlowStatusMapping::getSortOrder));
    }

    /**
     * 根据流程配置ID和源节点查询
     *
     * @param processConfigId 流程配置ID，后续用于查询流程与来源节点时定位或关联目标
     * @param sourceNodeId 来源节点ID，后续用于查询流程与来源节点时定位或关联目标
     * @return 实体流程状态映射集合，供调用方遍历或展示
     */
    default List<EntityFlowStatusMapping> findByProcessAndSourceNode(String processConfigId, String sourceNodeId) {
        return selectList(Wrappers.<EntityFlowStatusMapping>lambdaQuery()
                .eq(EntityFlowStatusMapping::getProcessConfigId, processConfigId)
                .eq(EntityFlowStatusMapping::getSourceNodeId, sourceNodeId)
                .eq(EntityFlowStatusMapping::getDeleted, 0));
    }

    /**
     * 根据流程配置ID、源节点和目标节点查询
     *
     * @param processConfigId 流程配置ID，后续用于查询流程与节点集合时定位或关联目标
     * @param sourceNodeId 来源节点ID，后续用于查询流程与节点集合时定位或关联目标
     * @param targetNodeId 目标节点ID，后续用于查询流程与节点集合时定位或关联目标
     * @return 符合条件的实体流程状态映射结果，供调用方继续处理
     */
    default EntityFlowStatusMapping findByProcessAndNodes(String processConfigId, String sourceNodeId, String targetNodeId) {
        return selectList(new OffsetPage<>(0, 1), Wrappers.<EntityFlowStatusMapping>lambdaQuery()
                .eq(EntityFlowStatusMapping::getProcessConfigId, processConfigId)
                .eq(EntityFlowStatusMapping::getSourceNodeId, sourceNodeId)
                .eq(EntityFlowStatusMapping::getTargetNodeId, targetNodeId)
                .eq(EntityFlowStatusMapping::getDeleted, 0))
                .stream().findFirst().orElse(null);
    }

    /**
     * 根据实体编码查询
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 实体流程状态映射集合，供调用方遍历或展示
     */
    default List<EntityFlowStatusMapping> findByEntityCode(String entityCode) {
        return selectList(Wrappers.<EntityFlowStatusMapping>lambdaQuery()
                .eq(EntityFlowStatusMapping::getEntityCode, entityCode)
                .eq(EntityFlowStatusMapping::getDeleted, 0)
                .orderByAsc(EntityFlowStatusMapping::getSortOrder));
    }

    /**
     * 物理删除（避免与已删除数据产生唯一索引冲突）
     *
     * @param processConfigId 流程配置ID，后续用于删除流程配置ID时定位或关联目标
     */
    @Update("DELETE FROM process_entity_status_mapping WHERE process_config_id = #{processConfigId}")
    void deleteByProcessConfigId(@Param("processConfigId") String processConfigId);
}
