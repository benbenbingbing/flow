package com.workflow.process.configuration.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.process.configuration.infrastructure.persistence.record.NodeConfig;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 流程节点配置 Mapper
 */
@Mapper
public interface NodeConfigMapper extends BaseMapper<NodeConfig> {

    /**
     * 根据流程配置ID查询节点列表
     *
     * @param processConfigId 流程配置ID，后续用于查询流程配置ID时定位或关联目标
     * @return 节点配置集合，供调用方遍历或展示
     */
    default List<NodeConfig> findByProcessConfigId(String processConfigId) {
        return selectList(Wrappers.<NodeConfig>lambdaQuery()
                .eq(NodeConfig::getProcessConfigId, processConfigId)
                .orderByAsc(NodeConfig::getCreatedAt));
    }

    /**
     * 根据流程配置ID删除节点
     */
    /**
     * 该配置表没有逻辑删除字段，使用 BaseMapper 按条件物理删除。
     *
     * @param processConfigId 流程配置ID，后续用于删除流程配置ID时定位或关联目标
     */
    default void deleteByProcessConfigId(String processConfigId) {
        delete(Wrappers.<NodeConfig>lambdaQuery()
                .eq(NodeConfig::getProcessConfigId, processConfigId));
    }
    
    /**
     * 根据节点ID和流程配置ID查询节点配置
     *
     * @param nodeId 节点ID，后续用于查询节点ID与流程ID时定位或关联目标
     * @param processConfigId 流程配置ID，后续用于查询节点ID与流程ID时定位或关联目标
     * @return 查询后的节点ID与流程ID结果，供调用方继续处理
     */
    default NodeConfig selectByNodeIdAndProcessId(String nodeId, String processConfigId) {
        // 首行限制交给分页插件，避免加载全部结果或在 Mapper 内拼接数据库分页语法。
        return selectList(new Page<NodeConfig>(1, 1, false), Wrappers.<NodeConfig>lambdaQuery()
                .eq(NodeConfig::getNodeId, nodeId)
                .eq(NodeConfig::getProcessConfigId, processConfigId)).stream().findFirst().orElse(null);
    }
}
