package com.workflow.entity.excel.infrastructure.persistence.mapper;

import com.workflow.integration.database.api.query.DatabaseQueryDialects;
import org.apache.ibatis.annotations.*;
import org.apache.ibatis.builder.annotation.ProviderContext;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.session.ResultHandler;
import org.springframework.util.LinkedCaseInsensitiveMap;
import java.time.LocalDateTime;
import java.util.List;

/** Excel 作业状态 SQL；锁定与终态更新参加调用方业务事务，认领事务由仓储单独管理。 */
@Mapper
public interface EntityExcelJobMapper {
    @Insert("""
            INSERT INTO entity_excel_job(id,entity_id,entity_code,operation,status,user_id,origin_key,
              request_id,payload_hash,config_json,created_at,updated_at)
            VALUES (#{id},#{entityId},#{entityCode},#{operation},'RUNNING',#{userId},#{originKey},
              #{requestId},#{payloadHash},#{config},#{now},#{now})
            """)
    int insert(@Param("id") String id, @Param("entityId") String entityId, @Param("entityCode") String entityCode,
            @Param("operation") String operation, @Param("userId") String userId, @Param("originKey") String originKey,
            @Param("requestId") String requestId, @Param("payloadHash") String payloadHash, @Param("config") String config,
            @Param("now") LocalDateTime now);

    /** 显式文本映射阻止 CLOB 驱动对象逸出；大小写无关 Map 保持现有 snake_case 访问契约。 */
    @Select("SELECT * FROM entity_excel_job WHERE user_id=#{userId} AND origin_key=#{originKey} AND request_id=#{requestId}")
    @Results(id = "job", value = {
        @Result(column = "config_json", property = "config_json", javaType = String.class),
        @Result(column = "counters_json", property = "counters_json", javaType = String.class)
    })
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<LinkedCaseInsensitiveMap<Object>> findClaim(@Param("userId") String userId, @Param("originKey") String originKey,
            @Param("requestId") String requestId);

    @Select("SELECT * FROM entity_excel_job WHERE id=#{id}")
    @ResultMap("job")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    LinkedCaseInsensitiveMap<Object> find(@Param("id") String id);

    @Select("SELECT * FROM entity_excel_job WHERE id=#{id} FOR UPDATE")
    @ResultMap("job")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    LinkedCaseInsensitiveMap<Object> lock(@Param("id") String id);

    @Select("SELECT status FROM entity_excel_job WHERE id=#{id} FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    String lockStatus(@Param("id") String id);

    @Update("UPDATE entity_excel_job SET input_file_id=#{fileId,jdbcType=VARCHAR},updated_at=#{now} WHERE id=#{id} AND status='RUNNING'")
    int inputFile(@Param("id") String id, @Param("fileId") String fileId, @Param("now") LocalDateTime now);

    @Update("UPDATE entity_excel_job SET counters_json=#{counters},updated_at=#{now} WHERE id=#{id} AND status='RUNNING'")
    int progress(@Param("id") String id, @Param("counters") String counters, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE entity_excel_job SET status=#{status},counters_json=#{counters},output_file_id=#{fileId,jdbcType=VARCHAR},
              file_name=#{fileName,jdbcType=VARCHAR},content_type=#{contentType},error_file_id=#{errorFileId,jdbcType=VARCHAR},
              error_message=#{errorMessage,jdbcType=VARCHAR},updated_at=#{now} WHERE id=#{id} AND status='RUNNING'
            """)
    int finish(@Param("id") String id, @Param("status") String status, @Param("counters") String counters,
            @Param("fileId") String fileId, @Param("fileName") String fileName, @Param("contentType") String contentType,
            @Param("errorFileId") String errorFileId, @Param("errorMessage") String errorMessage, @Param("now") LocalDateTime now);

    @Insert("INSERT INTO entity_excel_job_row(id,job_id,sheet_no,excel_row_number,errors_json) "
            + "VALUES(#{id},#{jobId},#{sheetNo},#{rowNumber},#{errors})")
    int insertErrors(@Param("id") String id, @Param("jobId") String jobId, @Param("sheetNo") int sheetNo,
            @Param("rowNumber") int rowNumber, @Param("errors") String errors);

    /** 在 Mapper 调用期间逐行消费错误文档，避免 Cursor 逸出已关闭的 SqlSession。 */
    @Select("SELECT errors_json FROM entity_excel_job_row WHERE job_id=#{id} ORDER BY sheet_no,excel_row_number")
    @ResultType(String.class)
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE, resultSetType = ResultSetType.FORWARD_ONLY, fetchSize = 100)
    void streamErrors(@Param("id") String id, ResultHandler<String> handler);

    @SelectProvider(type = Sql.class, method = "expired")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<String> expired(@Param("cutoff") LocalDateTime cutoff);

    @Update("UPDATE entity_excel_job SET status='FAILED',error_message=#{message},"
            + "counters_json=COALESCE(counters_json,#{counters}),updated_at=#{now} WHERE id=#{id}")
    int failInterrupted(@Param("id") String id, @Param("message") String message, @Param("counters") String counters,
            @Param("now") LocalDateTime now);

    @Delete("DELETE FROM entity_excel_job_row WHERE job_id=#{id}")
    int deleteErrors(@Param("id") String id);

    @Update("UPDATE entity_excel_job SET input_file_id=NULL,output_file_id=NULL,error_file_id=NULL WHERE id=#{id}")
    int clearFiles(@Param("id") String id);

    final class Sql {
        private Sql() { }
        /** 只对稳定排序的候选查询分页；真正清理时另行锁行。UTC LocalDateTime 由 MyBatis setObject 绑定。 */
        public static String expired(ProviderContext context) {
            return DatabaseQueryDialects.forDatabaseId(context.getDatabaseId()).paginate("""
                    SELECT id FROM entity_excel_job WHERE created_at < #{cutoff}
                    AND (status='RUNNING' OR input_file_id IS NOT NULL OR output_file_id IS NOT NULL OR error_file_id IS NOT NULL
                      OR EXISTS (SELECT 1 FROM entity_excel_job_row r WHERE r.job_id=entity_excel_job.id))
                    ORDER BY created_at,id
                    """, "0", "100");
        }
    }
}
