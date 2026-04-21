package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.entity.ProcessDraft;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 流程草稿箱 Mapper
 */
@Mapper
public interface ProcessDraftMapper extends BaseMapper<ProcessDraft> {
    
    /**
     * 查询用户的草稿列表
     */
    Page<ProcessDraft> selectByUserId(Page<ProcessDraft> page,
            @Param("userId") String userId,
            @Param("status") String status);
}
