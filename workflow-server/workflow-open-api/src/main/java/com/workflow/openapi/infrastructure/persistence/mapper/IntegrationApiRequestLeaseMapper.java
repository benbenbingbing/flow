package com.workflow.openapi.infrastructure.persistence.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.DeleteProvider;
import org.apache.ibatis.builder.annotation.ProviderContext;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.query.DatabaseSort;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 定义集成API请求租约的调用契约；实现层按此提供能力，调用方无需依赖具体实现。
 */
@Mapper
public interface IntegrationApiRequestLeaseMapper {

    /** 开放接口的应用级保留范围；与 Embed 的带前缀 Grant 范围互斥，兼容历史空范围。 */
    String APPLICATION_SCOPE = "(scope_key = 'open-api-application-v1' OR NULLIF(scope_key, '') IS NULL)";

    /**
     * 删除过期应用；后续读取或执行将使用更新后的状态。
     *
     * @param applicationId 应用ID，后续用于删除过期应用时定位或关联目标
     * @param now 当前时间，供本方法删除过期应用时使用
     * @return 删除后的过期应用结果，供调用方继续处理
     */
    @Delete("""
            DELETE FROM integration_api_request_lease
             WHERE application_id = #{applicationId}
               AND expires_at <= #{now}
               AND
            """ + APPLICATION_SCOPE)
    int deleteExpiredForApplication(
            @Param("applicationId") String applicationId,
            @Param("now") LocalDateTime now);

    /**
     * 统计活动；结果供后续判断或展示使用。
     *
     * @param applicationId 应用ID，后续用于统计活动时定位或关联目标
     * @param now 当前时间，供本方法统计活动时使用
     * @return 符合条件的活动数量
     */
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

    /**
     * 空范围的存储值由纯类型规则选择；MySQL 保留原空串，避免滚动期间旧节点漏计新租约。
     *
     * @param leaseId 租约ID，后续用于插入集成API请求租约时定位或关联目标
     * @param applicationId 应用ID，后续用于插入集成API请求租约时定位或关联目标
     * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
     * @param now 当前时间，供本方法插入集成API请求租约时使用
     * @return 插入后的集成API请求租约结果，供调用方继续处理
     */
    @Insert("""
            <script>
            <bind name="_applicationScope" value="@com.workflow.integration.database.api.sql.DatabaseScalarValues@nonNullEmptyText(_databaseId, 'open-api-application-v1')"/>
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

    /**
     * 只释放应用级租约，不能误删其他业务范围中碰巧传来的租约 ID。
     *
     * @param leaseId 租约ID，后续用于处理发布版本时定位或关联目标
     * @return 处理后的发布版本结果，供调用方继续处理
     */
    @Delete("""
            DELETE FROM integration_api_request_lease
             WHERE lease_id = #{leaseId}
               AND
            """ + APPLICATION_SCOPE)
    int release(@Param("leaseId") String leaseId);

    /**
     * 删除过期；后续读取或执行将使用更新后的状态。
     *
     * @param now 当前时间，供本方法删除过期时使用
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 删除后的过期结果，供调用方继续处理
     */
    @DeleteProvider(type = MaintenanceSql.class, method = "expired")
    int deleteExpired(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit);
    /** 清理按到期时间和完整主键稳定分批，不读取或修改尚未过期的租约。 */
    class MaintenanceSql {
        /**
         * 生成过期文本，供后续匹配或展示。
         *
         * @param context 执行上下文，向后续过期步骤传递身份、配置或状态
         * @return 处理后的过期文本，供调用方比较或展示
         */
        public static String expired(ProviderContext context) {
            return DatabaseDialects.mutationsForDatabaseId(context.getDatabaseId()).deleteLimited(
                    "integration_api_request_lease", "expires_at <= #{now}",
                    List.of(new DatabaseSort("expires_at", false), new DatabaseSort("lease_id", false)),
                    List.of("lease_id"), "#{limit}");
        }
    }
}
