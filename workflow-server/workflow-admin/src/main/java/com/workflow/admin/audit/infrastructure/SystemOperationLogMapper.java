package com.workflow.admin.audit.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.admin.audit.domain.SystemOperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统操作日志持久化适配器。
 */
@Mapper
public interface SystemOperationLogMapper extends BaseMapper<SystemOperationLog> {

    /** 先按时间和主键选取有界 ID，再删除；仅查询主键，避免读取日志中的大 JSON。 */
    default int deleteExpiredBatch(java.time.LocalDateTime cutoff, int limit) {
        if (limit < 1 || limit > 1_000) throw new IllegalArgumentException("审计清理批次必须在 1～1000 之间");
        var ids = selectList(new com.workflow.core.database.OffsetPage<SystemOperationLog>(0, limit),
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<SystemOperationLog>lambdaQuery()
                        .select(SystemOperationLog::getId)
                        .lt(SystemOperationLog::getCreateTime, cutoff)
                        .orderByAsc(SystemOperationLog::getCreateTime, SystemOperationLog::getId))
                .stream().map(SystemOperationLog::getId).toList();
        if (ids.isEmpty()) return 0;
        return delete(com.baomidou.mybatisplus.core.toolkit.Wrappers.<SystemOperationLog>lambdaQuery()
                .in(SystemOperationLog::getId, ids).lt(SystemOperationLog::getCreateTime, cutoff));
    }
}
