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

/**
 * 定义集成应用的调用契约；实现层按此提供能力，调用方无需依赖具体实现。
 */
@Mapper
public interface IntegrationApplicationMapper
        extends BaseMapper<IntegrationApplicationRecord> {

    /**
     * 管理列表最多读取最近 200 个应用，限制和排序均通过框架查询生成。
     *
     * @return 集成应用集合，供调用方遍历或展示
     */
    default List<IntegrationApplicationRecord> findRecent() {
        return selectPage(new Page<IntegrationApplicationRecord>(1, 200, false),
                Wrappers.<IntegrationApplicationRecord>lambdaQuery()
                        .orderByDesc(IntegrationApplicationRecord::getCreateTime, IntegrationApplicationRecord::getId))
                .getRecords();
    }

    /**
     * 锁定ID；避免后续并发处理覆盖状态。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 锁定后的ID结果，供调用方继续处理
     */
    @Select("""
            SELECT *
              FROM integration_application
             WHERE id = #{id}
             FOR UPDATE
            """)
    IntegrationApplicationRecord lockById(@Param("id") String id);

    /**
     * 按客户端标识读取首条应用，沿用原有取首行及不存在返回 null 的行为。
     *
     * @param clientId 客户端ID，后续用于查询客户端ID时定位或关联目标
     * @return 符合条件的集成应用结果，供调用方继续处理
     */
    default IntegrationApplicationRecord findByClientId(String clientId) {
        return selectPage(new Page<IntegrationApplicationRecord>(1, 1, false),
                Wrappers.<IntegrationApplicationRecord>lambdaQuery()
                        .eq(IntegrationApplicationRecord::getClientId, clientId))
                .getRecords().stream().findFirst().orElse(null);
    }

    /**
     * 原子校验并递增版本后变更状态；显式 set 允许按原语义写入 null 审计字段。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param status 目标状态，写入记录后供流程分支或列表查询使用
     * @param expectedVersion 预期版本，作为 {@code eq} 的输入影响后续处理
     * @param operatorId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param now 当前时间，供本方法更新状态时使用
     * @return 更新后的状态结果，供调用方继续处理
     */
    default int updateStatus(String id, String status, long expectedVersion, String operatorId, LocalDateTime now) {
        return update(null, Wrappers.<IntegrationApplicationRecord>lambdaUpdate()
                .set(IntegrationApplicationRecord::getStatus, status)
                .setIncrBy(IntegrationApplicationRecord::getVersion, 1)
                .set(IntegrationApplicationRecord::getUpdatedBy, operatorId)
                .set(IntegrationApplicationRecord::getUpdateTime, now)
                .eq(IntegrationApplicationRecord::getId, id)
                .eq(IntegrationApplicationRecord::getVersion, expectedVersion));
    }

    /**
     * 只推进指定应用的期望版本，返回 0 继续表示版本冲突，不覆盖状态等业务字段。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param expectedVersion 预期版本，供本方法处理{@code advance}版本时使用
     * @param operatorId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param now 当前时间，作为 {@code set} 的输入影响后续处理
     * @return 处理后的{@code advance}版本结果，供调用方继续处理
     */
    default int advanceVersion(String id, long expectedVersion, String operatorId, LocalDateTime now) {
        return update(null, Wrappers.<IntegrationApplicationRecord>lambdaUpdate()
                .setIncrBy(IntegrationApplicationRecord::getVersion, 1)
                .set(IntegrationApplicationRecord::getUpdatedBy, operatorId)
                .set(IntegrationApplicationRecord::getUpdateTime, now)
                .eq(IntegrationApplicationRecord::getId, id)
                .eq(IntegrationApplicationRecord::getVersion, expectedVersion));
    }
}
