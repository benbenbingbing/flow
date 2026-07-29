package com.workflow.openapi.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationApplicationRecord;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface IntegrationApplicationMapper
        extends BaseMapper<IntegrationApplicationRecord> {

    @Select("""
            SELECT *
              FROM integration_application
             ORDER BY create_time DESC, id DESC
             LIMIT 200
            """)
    List<IntegrationApplicationRecord> findRecent();

    @Select("""
            SELECT *
              FROM integration_application
             WHERE id = #{id}
             FOR UPDATE
            """)
    IntegrationApplicationRecord lockById(@Param("id") String id);

    @Select("""
            SELECT *
              FROM integration_application
             WHERE client_id = #{clientId}
             LIMIT 1
            """)
    IntegrationApplicationRecord findByClientId(
            @Param("clientId") String clientId);

    @Update("""
            UPDATE integration_application
               SET status = #{status},
                   version = version + 1,
                   updated_by = #{operatorId},
                   update_time = #{now}
             WHERE id = #{id}
               AND version = #{expectedVersion}
            """)
    int updateStatus(
            @Param("id") String id,
            @Param("status") String status,
            @Param("expectedVersion") long expectedVersion,
            @Param("operatorId") String operatorId,
            @Param("now") java.time.LocalDateTime now);

    @Update("""
            UPDATE integration_application
               SET version = version + 1,
                   updated_by = #{operatorId},
                   update_time = #{now}
             WHERE id = #{id}
               AND version = #{expectedVersion}
            """)
    int advanceVersion(
            @Param("id") String id,
            @Param("expectedVersion") long expectedVersion,
            @Param("operatorId") String operatorId,
            @Param("now") java.time.LocalDateTime now);
}
