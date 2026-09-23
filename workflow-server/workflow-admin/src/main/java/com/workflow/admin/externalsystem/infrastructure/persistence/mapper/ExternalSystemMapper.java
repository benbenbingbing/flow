package com.workflow.admin.externalsystem.infrastructure.persistence.mapper;

import java.util.List;
import com.workflow.core.database.OffsetPage;
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
    /**
     * 原查询仅取首行；保留数据库中的过滤语义，返回数量由分页插件限制。
     *
     * @param systemCode 系统编码，后续用于查询{@code any}编码时定位或关联目标
     * @return 查询后的{@code any}编码结果，供调用方继续处理
     */
    default ExternalSystemRecord selectAnyByCode(String systemCode) {
        return selectAnyByCodePage(new OffsetPage<>(0, 1), systemCode).stream().findFirst().orElse(null);
    }

    /**
     * 保留原投影和连接，仅将外层首行限制交给 MyBatis-Plus。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param systemCode 系统编码，后续用于查询{@code any}编码分页时定位或关联目标
     * @return 外部系统集合，供调用方遍历或展示
     */
    @Select("""
            <script>
            SELECT id, system_name, system_code, status, address, description,
                   version,
                   created_by, updated_by, create_time, update_time, deleted
            FROM sys_external_system
            WHERE system_code = #{systemCode}

            </script>
            """)
    List<ExternalSystemRecord> selectAnyByCodePage(
            @Param("page") OffsetPage<ExternalSystemRecord> page,
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
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param systemName 系统名称，后续用于更新可变字段时匹配或展示
     * @param status 目标状态，写入记录后供流程分支或列表查询使用
     * @param address 地址，供本方法更新可变字段时使用
     * @param description 描述，供本方法更新可变字段时使用
     * @param expectedVersion 预期版本，供本方法更新可变字段时使用
     * @param updatedBy {@code updated}，供本方法更新可变字段时使用
     * @param updateTime 更新时间，后续用于判断有效期或展示该事件的发生时间
     * @return 更新后的可变字段结果，供调用方继续处理
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
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param status 目标状态，写入记录后供流程分支或列表查询使用
     * @param expectedVersion 预期版本，供本方法更新状态时使用
     * @param updatedBy {@code updated}，供本方法更新状态时使用
     * @param updateTime 更新时间，后续用于判断有效期或展示该事件的发生时间
     * @return 更新后的状态结果，供调用方继续处理
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
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param expectedVersion 预期版本，供本方法处理{@code soft}删除时使用
     * @param updatedBy {@code updated}，供本方法处理{@code soft}删除时使用
     * @param updateTime 更新时间，后续用于判断有效期或展示该事件的发生时间
     * @return 处理后的{@code soft}删除结果，供调用方继续处理
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
