package com.workflow.process.form.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.process.form.infrastructure.persistence.record.ProcessNodeForm;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 流程节点表单绑定Mapper
 */
@Mapper
public interface ProcessNodeFormMapper extends BaseMapper<ProcessNodeForm> {
    
    /**
     * 查询流程的节点表单绑定
     *
     * @param processConfigId 流程配置ID，后续用于查询流程配置ID时定位或关联目标
     * @return 流程节点表单集合，供调用方遍历或展示
     */
    default List<ProcessNodeForm> selectByProcessConfigId(String processConfigId) {
        return selectList(Wrappers.<ProcessNodeForm>lambdaQuery()
                .eq(ProcessNodeForm::getProcessConfigId, processConfigId)
                .orderByAsc(ProcessNodeForm::getNodeId)
                .orderByAsc(ProcessNodeForm::getSortOrder)
                .orderByAsc(ProcessNodeForm::getCreateTime));
    }
    
    /**
     * 查询节点的表单绑定
     *
     * @param processConfigId 流程配置ID，后续用于查询节点ID时定位或关联目标
     * @param nodeId 节点ID，后续用于查询节点ID时定位或关联目标
     * @return 查询后的节点ID结果，供调用方继续处理
     */
    default ProcessNodeForm selectByNodeId(String processConfigId, String nodeId) {
        // 首行限制交给分页插件，避免加载全部结果或在 Mapper 内拼接数据库分页语法。
        return selectList(new Page<ProcessNodeForm>(1, 1, false), Wrappers.<ProcessNodeForm>lambdaQuery()
                .eq(ProcessNodeForm::getProcessConfigId, processConfigId)
                .eq(ProcessNodeForm::getNodeId, nodeId)
                .orderByAsc(ProcessNodeForm::getSortOrder, ProcessNodeForm::getCreateTime)).stream().findFirst().orElse(null);
    }

    /**
     * 查询节点的所有表单绑定
     *
     * @param processConfigId 流程配置ID，后续用于查询列表节点ID时定位或关联目标
     * @param nodeId 节点ID，后续用于查询列表节点ID时定位或关联目标
     * @return 流程节点表单集合，供调用方遍历或展示
     */
    default List<ProcessNodeForm> selectListByNodeId(String processConfigId, String nodeId) {
        return selectList(Wrappers.<ProcessNodeForm>lambdaQuery()
                .eq(ProcessNodeForm::getProcessConfigId, processConfigId)
                .eq(ProcessNodeForm::getNodeId, nodeId)
                .orderByAsc(ProcessNodeForm::getSortOrder)
                .orderByAsc(ProcessNodeForm::getCreateTime));
    }

    /**
     * 删除节点的所有表单绑定
     */
    /**
     * 该配置表没有逻辑删除字段，使用 BaseMapper 按条件物理删除。
     *
     * @param processConfigId 流程配置ID，后续用于删除流程配置ID与节点ID时定位或关联目标
     * @param nodeId 节点ID，后续用于删除流程配置ID与节点ID时定位或关联目标
     */
    default void deleteByProcessConfigIdAndNodeId(String processConfigId, String nodeId) {
        delete(Wrappers.<ProcessNodeForm>lambdaQuery()
                .eq(ProcessNodeForm::getProcessConfigId, processConfigId)
                .eq(ProcessNodeForm::getNodeId, nodeId));
    }
    
    /**
     * 删除流程的所有节点表单绑定
     */
    /**
     * 该配置表没有逻辑删除字段，使用 BaseMapper 按条件物理删除。
     *
     * @param processConfigId 流程配置ID，后续用于删除流程配置ID时定位或关联目标
     */
    default void deleteByProcessConfigId(String processConfigId) {
        delete(Wrappers.<ProcessNodeForm>lambdaQuery()
                .eq(ProcessNodeForm::getProcessConfigId, processConfigId));
    }
}
