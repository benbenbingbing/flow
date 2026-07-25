package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.ProcessUiReleaseBinding;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 流程版本 UI 发布绑定 Mapper。
 */
@Mapper
public interface ProcessUiReleaseBindingMapper
        extends BaseMapper<ProcessUiReleaseBinding> {

    @Select("SELECT * FROM process_ui_release_binding "
            + "WHERE config_type = 'FORM' AND config_id = #{formId} "
            + "ORDER BY process_key, process_version DESC, node_id")
    List<ProcessUiReleaseBinding> findByFormId(
            @Param("formId") String formId);

    @Select("SELECT * FROM process_ui_release_binding "
            + "WHERE process_version_history_id = #{historyId} "
            + "ORDER BY node_id")
    List<ProcessUiReleaseBinding> findByHistoryId(
            @Param("historyId") String historyId);

    @Delete("DELETE FROM process_ui_release_binding "
            + "WHERE process_version_history_id = #{historyId}")
    int deleteByHistoryId(@Param("historyId") String historyId);
}
