package com.workflow.admin.externalsystem.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.admin.externalsystem.infrastructure.persistence.record.ExternalSystemParameterRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 外部系统参数集合持久化入口。
 */
@Mapper
public interface ExternalSystemParameterMapper
        extends BaseMapper<ExternalSystemParameterRecord> {

    /**
     * 查询外部系统当前活动参数，按显式排序和英文名稳定返回。
     *
     * @param externalSystemId 外部系统 ID
     * @return 当前参数集合
     */
    @Select("""
            SELECT id, external_system_id, parameter_name_zh,
                   parameter_name_en, parameter_value, sort_order,
                   created_by, updated_by, create_time, update_time, deleted
            FROM sys_external_system_parameter
            WHERE external_system_id = #{externalSystemId} AND deleted = 0
            ORDER BY sort_order, parameter_name_en, id
            """)
    List<ExternalSystemParameterRecord> selectActiveByExternalSystemId(
            @Param("externalSystemId") String externalSystemId);

    /**
     * 批量统计分页结果中每个外部系统的活动参数数，避免列表页逐行查询。
     *
     * @param externalSystemIds 外部系统 ID 列表
     * @return 外部系统 ID 与参数数量投影
     */
    @Select({
            "<script>",
            "SELECT external_system_id AS externalSystemId,",
            "       COUNT(*) AS parameterCount",
            "FROM sys_external_system_parameter",
            "WHERE deleted = 0 AND external_system_id IN",
            "<foreach collection='externalSystemIds' item='id' open='(' separator=',' close=')'>",
            "#{id}",
            "</foreach>",
            "GROUP BY external_system_id",
            "</script>"
    })
    List<ExternalSystemParameterCount> countActiveByExternalSystemIds(
            @Param("externalSystemIds") List<String> externalSystemIds);

    /**
     * 原子替换或删除父系统时批量逻辑删除当前参数。
     *
     * @param externalSystemId 外部系统 ID
     * @param updatedBy 操作人
     * @param updateTime 操作时间
     * @return 受影响行数
     */
    @Update("""
            UPDATE sys_external_system_parameter
            SET deleted = 1, updated_by = #{updatedBy},
                update_time = #{updateTime}
            WHERE external_system_id = #{externalSystemId} AND deleted = 0
            """)
    int softDeleteByExternalSystemId(
            @Param("externalSystemId") String externalSystemId,
            @Param("updatedBy") String updatedBy,
            @Param("updateTime") LocalDateTime updateTime);

    /**
     * 参数数量查询投影。
     */
    class ExternalSystemParameterCount {
        private String externalSystemId;
        private long parameterCount;

        public String getExternalSystemId() {
            return externalSystemId;
        }

        public void setExternalSystemId(String externalSystemId) {
            this.externalSystemId = externalSystemId;
        }

        public long getParameterCount() {
            return parameterCount;
        }

        public void setParameterCount(long parameterCount) {
            this.parameterCount = parameterCount;
        }
    }
}
