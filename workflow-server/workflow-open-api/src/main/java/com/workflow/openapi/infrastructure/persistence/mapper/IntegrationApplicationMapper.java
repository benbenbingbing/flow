package com.workflow.openapi.infrastructure.persistence.mapper;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationApplicationRecord;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface IntegrationApplicationMapper
        extends BaseMapper<IntegrationApplicationRecord> {

    /** 管理列表最多读取最近 200 个应用，限制和排序均通过框架查询生成。 */
    default List<IntegrationApplicationRecord> findRecent() {
        return selectPage(new Page<IntegrationApplicationRecord>(1, 200, false),
                Wrappers.<IntegrationApplicationRecord>lambdaQuery()
                        .orderByDesc(IntegrationApplicationRecord::getCreateTime, IntegrationApplicationRecord::getId))
                .getRecords();
    }

    @Select("""
            SELECT *
              FROM integration_application
             WHERE id = #{id}
             FOR UPDATE
            """)
    IntegrationApplicationRecord lockById(@Param("id") String id);

    /** 按客户端标识读取首条应用，沿用原有取首行及不存在返回 null 的行为。 */
    default IntegrationApplicationRecord findByClientId(String clientId) {
        return selectPage(new Page<IntegrationApplicationRecord>(1, 1, false),
                Wrappers.<IntegrationApplicationRecord>lambdaQuery()
                        .eq(IntegrationApplicationRecord::getClientId, clientId))
                .getRecords().stream().findFirst().orElse(null);
    }

    /** 原子校验并递增版本后变更状态；显式 set 允许按原语义写入 null 审计字段。 */
    default int updateStatus(String id, String status, long expectedVersion, String operatorId, LocalDateTime now) {
        return update(null, Wrappers.<IntegrationApplicationRecord>lambdaUpdate()
                .set(IntegrationApplicationRecord::getStatus, status)
                .setIncrBy(IntegrationApplicationRecord::getVersion, 1)
                .set(IntegrationApplicationRecord::getUpdatedBy, operatorId)
                .set(IntegrationApplicationRecord::getUpdateTime, now)
                .eq(IntegrationApplicationRecord::getId, id)
                .eq(IntegrationApplicationRecord::getVersion, expectedVersion));
    }

    /** 只推进指定应用的期望版本，返回 0 继续表示版本冲突，不覆盖状态等业务字段。 */
    default int advanceVersion(String id, long expectedVersion, String operatorId, LocalDateTime now) {
        return update(null, Wrappers.<IntegrationApplicationRecord>lambdaUpdate()
                .setIncrBy(IntegrationApplicationRecord::getVersion, 1)
                .set(IntegrationApplicationRecord::getUpdatedBy, operatorId)
                .set(IntegrationApplicationRecord::getUpdateTime, now)
                .eq(IntegrationApplicationRecord::getId, id)
                .eq(IntegrationApplicationRecord::getVersion, expectedVersion));
    }
}
