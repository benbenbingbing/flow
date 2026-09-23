package com.workflow.process.instance.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.workflow.core.database.OffsetPage;
import org.apache.ibatis.annotations.*;
import java.util.*;

/** 按名称命中的部署定义分页；Flowable 7 的历史查询不支持 definition ID 集合。 */
// 普通外层分页由 MyBatis-Plus 处理；锁定与嵌套分页仍保留必要的数据库适配。
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

    default List<String> page(String userId, Set<String> definitionIds, Date start, Date end, long offset, int limit) {
        return pageRows(new OffsetPage<>(offset, limit), userId, definitionIds, start, end);
    }

    /** 保留完整业务查询，由 MyBatis-Plus 处理最外层分页，避免重复维护各数据库分页语法。 */
    @Select("<script>SELECT PROC_INST_ID_ " + WHERE
            + " ORDER BY START_TIME_ DESC, PROC_INST_ID_ DESC </script>")
    List<String> pageRows(
            @Param("page") IPage<String> page,
            @Param("userId") String userId,
            @Param("definitionIds") Set<String> definitionIds,
            @Param("start") Date start,
            @Param("end") Date end);
}
