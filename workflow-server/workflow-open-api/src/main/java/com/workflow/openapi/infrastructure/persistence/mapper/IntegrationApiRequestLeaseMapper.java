package com.workflow.openapi.infrastructure.persistence.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.DeleteProvider;
import org.apache.ibatis.builder.annotation.ProviderContext;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.DatabaseSort;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface IntegrationApiRequestLeaseMapper {

    /** 开放接口的应用级保留范围；与 Embed 的带前缀 Grant 范围互斥，兼容历史空范围。 */
    String APPLICATION_SCOPE = "(scope_key = 'open-api-application-v1' OR NULLIF(scope_key, '') IS NULL)";

    @Delete("""
            DELETE FROM integration_api_request_lease
             WHERE application_id = #{applicationId}
               AND expires_at <= #{now}
               AND
            """ + APPLICATION_SCOPE)
    int deleteExpiredForApplication(
            @Param("applicationId") String applicationId,
            @Param("now") LocalDateTime now);

    @Select("""
            SELECT COUNT(*)
             FROM integration_api_request_lease
             WHERE application_id = #{applicationId}
               AND expires_at > #{now}
               AND
            """ + APPLICATION_SCOPE)
    int countActive(
            @Param("applicationId") String applicationId,
            @Param("now") LocalDateTime now);

    /** 空范围的存储值由纯类型规则选择；MySQL 保留原空串，避免滚动期间旧节点漏计新租约。 */
    @Insert("""
            <script>
            <bind name="_applicationScope" value="@com.workflow.integration.database.api.DatabaseScalarValues@nonNullEmptyText(_databaseId, 'open-api-application-v1')"/>
            INSERT INTO integration_api_request_lease (
              lease_id, application_id, scope_key, expires_at, create_time, update_time
            ) VALUES (
              #{leaseId}, #{applicationId}, #{_applicationScope,jdbcType=VARCHAR}, #{expiresAt}, #{now}, #{now}
            )
            </script>
            """)
    int insert(
            @Param("leaseId") String leaseId,
            @Param("applicationId") String applicationId,
            @Param("expiresAt") LocalDateTime expiresAt,
            @Param("now") LocalDateTime now);

    /** 只释放应用级租约，不能误删其他业务范围中碰巧传来的租约 ID。 */
    @Delete("""
            DELETE FROM integration_api_request_lease
             WHERE lease_id = #{leaseId}
               AND
            """ + APPLICATION_SCOPE)
    int release(@Param("leaseId") String leaseId);

    @DeleteProvider(type = MaintenanceSql.class, method = "expired")
    int deleteExpired(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit);
    /** 清理按到期时间和完整主键稳定分批，不读取或修改尚未过期的租约。 */
    class MaintenanceSql {
        public static String expired(ProviderContext context) {
            return DatabaseDialects.mutationsForDatabaseId(context.getDatabaseId()).deleteLimited(
                    "integration_api_request_lease", "expires_at <= #{now}",
                    List.of(new DatabaseSort("expires_at", false), new DatabaseSort("lease_id", false)),
                    List.of("lease_id"), "#{limit}");
        }
    }
}
