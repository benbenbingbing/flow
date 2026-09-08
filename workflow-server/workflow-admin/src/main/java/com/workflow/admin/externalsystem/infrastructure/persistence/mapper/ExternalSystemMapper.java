package com.workflow.admin.externalsystem.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.admin.externalsystem.infrastructure.persistence.record.ExternalSystemRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 外部系统基本信息持久化入口。
 */
@Mapper
public interface ExternalSystemMapper extends BaseMapper<ExternalSystemRecord> {

    /**
     * 跨活动和已删除数据查询编码，确保系统编码永久不可复用。
     *
     * @param systemCode 系统编码
     * @return 已占用该编码的记录，不存在时返回 null
     */
    @Select("""
            SELECT id, system_name, system_code, status, address, description,
                   version,
                   created_by, updated_by, create_time, update_time, deleted
            FROM sys_external_system
            WHERE system_code = #{systemCode}
            LIMIT 1
            """)
    ExternalSystemRecord selectAnyByCode(
            @Param("systemCode") String systemCode);

    /**
     * 锁定活动外部系统，保证父记录和参数集合在同一事务中原子更新。
     *
     * @param id 外部系统 ID
     * @return 已锁定记录，不存在时返回 null
     */
    @Select("""
            SELECT id, system_name, system_code, status, address, description,
                   version,
                   created_by, updated_by, create_time, update_time, deleted
            FROM sys_external_system
            WHERE id = #{id} AND deleted = 0
            FOR UPDATE
            """)
    ExternalSystemRecord selectForUpdate(@Param("id") String id);

    /**
     * 只更新可变基本信息，SQL 中不包含 system_code，并以期望版本作为并发条件。
     */
    @Update("""
            UPDATE sys_external_system
            SET system_name = #{systemName},
                status = #{status},
                address = #{address},
                description = #{description},
                version = version + 1,
                updated_by = #{updatedBy},
                update_time = #{updateTime}
            WHERE id = #{id} AND deleted = 0
              AND version = #{expectedVersion}
            """)
    int updateMutableFields(
            @Param("id") String id,
            @Param("systemName") String systemName,
            @Param("status") String status,
            @Param("address") String address,
            @Param("description") String description,
            @Param("expectedVersion") long expectedVersion,
            @Param("updatedBy") String updatedBy,
            @Param("updateTime") LocalDateTime updateTime);

    /**
     * 更新外部系统启停状态，同时校验并推进乐观版本。
     */
    @Update("""
            UPDATE sys_external_system
            SET status = #{status}, version = version + 1,
                updated_by = #{updatedBy},
                update_time = #{updateTime}
            WHERE id = #{id} AND deleted = 0
              AND version = #{expectedVersion}
            """)
    int updateStatus(
            @Param("id") String id,
            @Param("status") String status,
            @Param("expectedVersion") long expectedVersion,
            @Param("updatedBy") String updatedBy,
            @Param("updateTime") LocalDateTime updateTime);

    /**
     * 逻辑删除外部系统并同步将其状态置为禁用，同时推进乐观版本。
     */
    @Update("""
            UPDATE sys_external_system
            SET deleted = 1, status = '1', version = version + 1,
                updated_by = #{updatedBy},
                update_time = #{updateTime}
            WHERE id = #{id} AND deleted = 0
              AND version = #{expectedVersion}
            """)
    int softDelete(
            @Param("id") String id,
            @Param("expectedVersion") long expectedVersion,
            @Param("updatedBy") String updatedBy,
            @Param("updateTime") LocalDateTime updateTime);
}
