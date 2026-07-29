package com.workflow.openapi.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationApplicationCredentialRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface IntegrationCredentialMapper
        extends BaseMapper<IntegrationApplicationCredentialRecord> {

    @Select("""
            SELECT *
              FROM integration_application_credential
             WHERE application_id = #{applicationId}
               AND status = 'ACTIVE'
             LIMIT 1
            """)
    IntegrationApplicationCredentialRecord findActive(
            @Param("applicationId") String applicationId);

    @Select("""
            <script>
            SELECT *
              FROM integration_application_credential
             WHERE status = 'ACTIVE'
               AND application_id IN
               <foreach collection="applicationIds" item="id"
                        open="(" separator="," close=")">
                 #{id}
               </foreach>
             ORDER BY application_id
            </script>
            """)
    List<IntegrationApplicationCredentialRecord>
            findActiveByApplicationIds(
                    @Param("applicationIds")
                    List<String> applicationIds);

    @Select("""
            SELECT COALESCE(MAX(credential_version), 0)
              FROM integration_application_credential
             WHERE application_id = #{applicationId}
            """)
    long findLatestVersion(
            @Param("applicationId") String applicationId);

    @Update("""
            UPDATE integration_application_credential
               SET status = 'REVOKED',
                   revoked_by = #{operatorId},
                   revoked_at = #{now}
             WHERE application_id = #{applicationId}
               AND status = 'ACTIVE'
            """)
    int revokeActive(
            @Param("applicationId") String applicationId,
            @Param("operatorId") String operatorId,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE integration_application_credential credential
            JOIN integration_application application
              ON application.id = credential.application_id
               SET credential.last_used_at = #{now}
             WHERE application.client_id = #{clientId}
               AND application.status = 'ACTIVE'
               AND credential.status = 'ACTIVE'
            """)
    int markActiveUsedByClientId(
            @Param("clientId") String clientId,
            @Param("now") LocalDateTime now);
}
