package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.ProcessCommonOpinion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 常用审批意见 Mapper
 */
@Mapper
public interface ProcessCommonOpinionMapper extends BaseMapper<ProcessCommonOpinion> {
    
    /**
     * 查询用户的常用意见
     */
    List<ProcessCommonOpinion> findByUserId(@Param("userId") String userId, 
                                            @Param("opinionType") String opinionType);
    
    /**
     * 增加使用次数
     */
    @Update("UPDATE process_common_opinion SET use_count = use_count + 1, updated_at = NOW() WHERE id = #{id}")
    void incrementUseCount(@Param("id") String id);
}
