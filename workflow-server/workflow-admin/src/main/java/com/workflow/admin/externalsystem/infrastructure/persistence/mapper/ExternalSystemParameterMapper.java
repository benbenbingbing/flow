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

        /**
         * 读取外部系统ID；查询结果供调用方展示或继续处理。
         *
         * @return 读取后的外部系统ID文本，供调用方比较或展示
         */
        public String getExternalSystemId() {
            return externalSystemId;
        }

        /**
         * 设置外部系统ID；后续读取或执行将使用更新后的状态。
         *
         * @param externalSystemId 外部系统ID，后续用于设置外部系统ID时定位或关联目标
         */
        public void setExternalSystemId(String externalSystemId) {
            this.externalSystemId = externalSystemId;
        }

        /**
         * 读取参数数量；查询结果供调用方展示或继续处理。
         *
         * @return 符合条件的外部系统参数数量结果，供调用方继续处理
         */
        public long getParameterCount() {
            return parameterCount;
        }

        /**
         * 设置参数数量；后续读取或执行将使用更新后的状态。
         *
         * @param parameterCount 参数数量，供本方法设置参数数量时使用
         */
        public void setParameterCount(long parameterCount) {
            this.parameterCount = parameterCount;
        }
    }
}
