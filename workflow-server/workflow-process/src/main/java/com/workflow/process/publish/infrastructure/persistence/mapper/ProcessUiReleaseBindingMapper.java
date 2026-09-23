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

    /** 查询表单被流程版本绑定的记录，按流程、版本及节点排列。 */
    default List<ProcessUiReleaseBinding> findByFormId(String formId) {
        return selectList(Wrappers.<ProcessUiReleaseBinding>lambdaQuery()
                .eq(ProcessUiReleaseBinding::getConfigType, "FORM")
                .eq(ProcessUiReleaseBinding::getConfigId, formId)
                .orderByAsc(ProcessUiReleaseBinding::getProcessKey)
                .orderByDesc(ProcessUiReleaseBinding::getProcessVersion)
                .orderByAsc(ProcessUiReleaseBinding::getNodeId));
    }

    /** 读取指定流程历史版本的界面绑定，按节点编号排列。 */
    default List<ProcessUiReleaseBinding> findByHistoryId(String historyId) {
        return selectList(Wrappers.<ProcessUiReleaseBinding>lambdaQuery()
                .eq(ProcessUiReleaseBinding::getProcessVersionHistoryId, historyId)
                .orderByAsc(ProcessUiReleaseBinding::getNodeId));
    }

    /** 该配置表没有逻辑删除字段，使用 BaseMapper 按条件物理删除。 */
    default int deleteByHistoryId(String historyId) {
        return delete(Wrappers.<ProcessUiReleaseBinding>lambdaQuery()
                .eq(ProcessUiReleaseBinding::getProcessVersionHistoryId, historyId));
    }
}
