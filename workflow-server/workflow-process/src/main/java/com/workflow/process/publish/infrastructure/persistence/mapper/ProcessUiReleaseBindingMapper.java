package com.workflow.process.publish.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.process.publish.infrastructure.persistence.record.ProcessUiReleaseBinding;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 流程版本 UI 发布绑定 Mapper。
 */
@Mapper
public interface ProcessUiReleaseBindingMapper
        extends BaseMapper<ProcessUiReleaseBinding> {

    /**
     * 查询表单被流程版本绑定的记录，按流程、版本及节点排列。
     *
     * @param formId 表单ID，后续用于查询表单ID时定位或关联目标
     * @return 流程界面发布版本绑定集合，供调用方遍历或展示
     */
    default List<ProcessUiReleaseBinding> findByFormId(String formId) {
        return selectList(Wrappers.<ProcessUiReleaseBinding>lambdaQuery()
                .eq(ProcessUiReleaseBinding::getConfigType, "FORM")
                .eq(ProcessUiReleaseBinding::getConfigId, formId)
                .orderByAsc(ProcessUiReleaseBinding::getProcessKey)
                .orderByDesc(ProcessUiReleaseBinding::getProcessVersion)
                .orderByAsc(ProcessUiReleaseBinding::getNodeId));
    }

    /**
     * 读取指定流程历史版本的界面绑定，按节点编号排列。
     *
     * @param historyId 历史ID，后续用于查询历史ID时定位或关联目标
     * @return 流程界面发布版本绑定集合，供调用方遍历或展示
     */
    default List<ProcessUiReleaseBinding> findByHistoryId(String historyId) {
        return selectList(Wrappers.<ProcessUiReleaseBinding>lambdaQuery()
                .eq(ProcessUiReleaseBinding::getProcessVersionHistoryId, historyId)
                .orderByAsc(ProcessUiReleaseBinding::getNodeId));
    }

    /**
     * 该配置表没有逻辑删除字段，使用 BaseMapper 按条件物理删除。
     *
     * @param historyId 历史ID，后续用于删除历史ID时定位或关联目标
     * @return 删除后的历史ID结果，供调用方继续处理
     */
    default int deleteByHistoryId(String historyId) {
        return delete(Wrappers.<ProcessUiReleaseBinding>lambdaQuery()
                .eq(ProcessUiReleaseBinding::getProcessVersionHistoryId, historyId));
    }
}
