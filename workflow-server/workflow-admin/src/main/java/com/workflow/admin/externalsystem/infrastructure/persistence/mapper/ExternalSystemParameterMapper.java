package com.workflow.admin.externalsystem.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.admin.externalsystem.infrastructure.persistence.record.ExternalSystemParameterRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
    default List<ExternalSystemParameterRecord> selectActiveByExternalSystemId(String externalSystemId) {
        return selectList(Wrappers.<ExternalSystemParameterRecord>lambdaQuery()
                .eq(ExternalSystemParameterRecord::getExternalSystemId, externalSystemId)
                .orderByAsc(ExternalSystemParameterRecord::getSortOrder,
                        ExternalSystemParameterRecord::getParameterNameEn, ExternalSystemParameterRecord::getId));
    }

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
    default int softDeleteByExternalSystemId(
            String externalSystemId, String updatedBy, LocalDateTime updateTime) {
        // 显式设置删除标识及审计字段，统一逻辑删除条件保证已删除参数不会再次变更。
        return update(null, Wrappers.<ExternalSystemParameterRecord>lambdaUpdate()
                .set(ExternalSystemParameterRecord::getDeleted, 1)
                .set(ExternalSystemParameterRecord::getUpdatedBy, updatedBy)
                .set(ExternalSystemParameterRecord::getUpdateTime, updateTime)
                .eq(ExternalSystemParameterRecord::getExternalSystemId, externalSystemId));
    }

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
