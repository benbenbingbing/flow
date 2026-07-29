package com.workflow.openapi.infrastructure.persistence.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface IntegrationApiRequestLeaseMapper {

    @Delete("""
            DELETE FROM integration_api_request_lease
             WHERE application_id = #{applicationId}
               AND expires_at <= #{now}
            """)
    int deleteExpiredForApplication(
            @Param("applicationId") String applicationId,
            @Param("now") LocalDateTime now);

    @Select("""
            SELECT COUNT(*)
              FROM integration_api_request_lease
             WHERE application_id = #{applicationId}
               AND expires_at > #{now}
            """)
    int countActive(
            @Param("applicationId") String applicationId,
            @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO integration_api_request_lease (
              lease_id, application_id, expires_at, create_time, update_time
            ) VALUES (
              #{leaseId}, #{applicationId}, #{expiresAt}, #{now}, #{now}
            )
            """)
    int insert(
            @Param("leaseId") String leaseId,
            @Param("applicationId") String applicationId,
            @Param("expiresAt") LocalDateTime expiresAt,
            @Param("now") LocalDateTime now);

    @Delete("""
            DELETE FROM integration_api_request_lease
             WHERE lease_id = #{leaseId}
            """)
    int release(@Param("leaseId") String leaseId);

    @Delete("""
            DELETE FROM integration_api_request_lease
             WHERE expires_at <= #{now}
             LIMIT #{limit}
            """)
    int deleteExpired(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit);
}
