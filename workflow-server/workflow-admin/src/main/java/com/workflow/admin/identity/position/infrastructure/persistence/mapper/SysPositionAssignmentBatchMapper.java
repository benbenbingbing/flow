package com.workflow.admin.identity.position.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.admin.identity.position.infrastructure.persistence.record.SysPositionAssignmentBatch;
import org.apache.ibatis.annotations.Mapper;

/**
 * 批量任命幂等结果 Mapper。
 */
@Mapper
public interface SysPositionAssignmentBatchMapper
        extends BaseMapper<SysPositionAssignmentBatch> {

    /**
     * 按操作人和幂等键读取首条批次，由分页插件限制返回数量。
     *
     * @param actorId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @return 查询后的键结果，供调用方继续处理
     */
    default SysPositionAssignmentBatch selectByKey(String actorId, String key) {
        return selectPage(new Page<SysPositionAssignmentBatch>(1, 1, false),
                Wrappers.<SysPositionAssignmentBatch>lambdaQuery()
                        .eq(SysPositionAssignmentBatch::getCreatedBy, actorId)
                        .eq(SysPositionAssignmentBatch::getIdempotencyKey, key))
                .getRecords().stream().findFirst().orElse(null);
    }
}
