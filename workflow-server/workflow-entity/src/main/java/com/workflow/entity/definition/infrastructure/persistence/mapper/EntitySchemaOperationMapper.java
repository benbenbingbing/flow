package com.workflow.entity.definition.infrastructure.persistence.mapper;

import com.workflow.entity.definition.infrastructure.persistence.record.EntitySchemaOperationRow;
import com.workflow.integration.database.api.query.DatabaseQueryDialects;
import org.apache.ibatis.annotations.*;
import org.apache.ibatis.builder.annotation.ProviderContext;

/** 结构发布台账的业务读写；DDL 与底层初始化行锁仍由专用基础设施负责。 */
@Mapper
public interface EntitySchemaOperationMapper {
    // 显式投影避免 Oracle 不接受“* 与其他表达式混用”，同时不把创建人等内部列暴露给响应。
    String COLUMNS = "id,entity_id,entity_code,status,plan_hash,idempotency_key,plan_json,"
            + "target_fingerprint,actual_fingerprint,drift_json,unique_conflict_json,risk_level,risk_reason,"
            + "estimated_rows,lock_risk,release_window,attempt_count,error_message,started_at,finished_at,"
            + "create_time AS created_at,update_time AS updated_at";

    /** 查询不可变计划；JSON 损坏由服务层显式报错，不能降级为空计划。 */
    @Select("SELECT " + COLUMNS + " FROM entity_schema_operation WHERE entity_id=#{entityId} AND plan_hash=#{planHash}")
    EntitySchemaOperationRow findByPlan(@Param("entityId") String entityId, @Param("planHash") String planHash);

    /** 初始化锁完成后必须当前读，并清除会话缓存，不能看到等待之前的旧状态。 */
    @Select("SELECT " + COLUMNS + " FROM entity_schema_operation WHERE entity_id=#{entityId} AND plan_hash=#{planHash} FOR UPDATE")
    @Options(useCache=false, flushCache=Options.FlushCachePolicy.TRUE)
    EntitySchemaOperationRow findByPlanForUpdate(@Param("entityId") String entityId, @Param("planHash") String planHash);

    @Select("SELECT " + COLUMNS + " FROM entity_schema_operation WHERE id=#{id}")
    EntitySchemaOperationRow findById(@Param("id") String id);

    /** 返回最新一项，分页由数据库产品决定，避免将全表历史加载进内存。 */
    @SelectProvider(type=Sql.class, method="latest")
    EntitySchemaOperationRow latest(@Param("entityId") String entityId);

    @Update("UPDATE entity_schema_operation SET status='DDL_RUNNING',attempt_count=attempt_count+1,started_at=CURRENT_TIMESTAMP,finished_at=NULL,error_message=NULL WHERE id=#{id}")
    int markRunning(@Param("id") String id);

    @Update("UPDATE entity_schema_operation SET status='SCHEMA_CONSISTENT',actual_fingerprint=#{fingerprint},drift_json=#{drift},error_message=NULL,finished_at=CURRENT_TIMESTAMP WHERE id=#{id}")
    int complete(@Param("id") String id, @Param("fingerprint") String fingerprint, @Param("drift") String drift);

    /** 不提供新指纹/诊断时保留原证据，失败状态在调用方 REQUIRES_NEW 事务中落库。 */
    @Update("UPDATE entity_schema_operation SET status='DDL_FAILED',actual_fingerprint=COALESCE(#{fingerprint},actual_fingerprint),drift_json=COALESCE(#{drift},drift_json),error_message=#{message},finished_at=CURRENT_TIMESTAMP WHERE id=#{id}")
    int markFailed(@Param("id") String id, @Param("fingerprint") String fingerprint, @Param("drift") String drift, @Param("message") String message);

    @Update("UPDATE entity_schema_operation SET status=#{status},error_message=CASE WHEN #{status,jdbcType=VARCHAR}='DDL_PENDING' THEN NULL ELSE error_message END WHERE id=#{id}")
    int transition(@Param("id") String id, @Param("status") String status);

    @Insert("INSERT INTO entity_schema_operation_event(id,operation_id,from_status,to_status,message) VALUES(#{id},#{operationId},#{from},#{to},#{message})")
    int recordEvent(@Param("id") String id, @Param("operationId") String operationId, @Param("from") String from, @Param("to") String to, @Param("message") String message);

    class Sql {
        /** 方言只负责分页模板；实体身份始终由 MyBatis 参数绑定。 */
        public static String latest(ProviderContext context) {
            return DatabaseQueryDialects.forDatabaseId(context.getDatabaseId()).paginate(
                    "SELECT " + COLUMNS + " FROM entity_schema_operation WHERE entity_id=#{entityId} ORDER BY create_time DESC,id DESC", "0", "1");
        }
    }
}
