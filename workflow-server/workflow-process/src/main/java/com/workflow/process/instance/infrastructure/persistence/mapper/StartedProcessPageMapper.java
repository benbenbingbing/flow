package com.workflow.process.instance.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.*;
import java.util.*;

/** 按名称命中的部署定义分页；Flowable 7 的历史查询不支持 definition ID 集合。 */
@Mapper
public interface StartedProcessPageMapper {
    String WHERE = """
            FROM ACT_HI_PROCINST
            WHERE START_USER_ID_=#{userId} AND PROC_DEF_ID_ IN
            <foreach collection="definitionIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            <if test="start != null">AND START_TIME_ &gt; #{start}</if>
            <if test="end != null">AND START_TIME_ &lt; #{end}</if>
            """;

    @Select("<script>SELECT COUNT(*) " + WHERE + "</script>")
    long count(@Param("userId") String userId, @Param("definitionIds") Set<String> definitionIds,
               @Param("start") Date start, @Param("end") Date end);

    @Select("<script>SELECT PROC_INST_ID_ " + WHERE
            + " ORDER BY START_TIME_ DESC, PROC_INST_ID_ DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<String> page(@Param("userId") String userId, @Param("definitionIds") Set<String> definitionIds,
                      @Param("start") Date start, @Param("end") Date end,
                      @Param("offset") long offset, @Param("limit") int limit);
}
