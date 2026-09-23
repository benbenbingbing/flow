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

    /** 按操作人和幂等键读取首条批次，由分页插件限制返回数量。 */
    default SysPositionAssignmentBatch selectByKey(String actorId, String key) {
        return selectPage(new Page<SysPositionAssignmentBatch>(1, 1, false),
                Wrappers.<SysPositionAssignmentBatch>lambdaQuery()
                        .eq(SysPositionAssignmentBatch::getCreatedBy, actorId)
                        .eq(SysPositionAssignmentBatch::getIdempotencyKey, key))
                .getRecords().stream().findFirst().orElse(null);
    }
}
