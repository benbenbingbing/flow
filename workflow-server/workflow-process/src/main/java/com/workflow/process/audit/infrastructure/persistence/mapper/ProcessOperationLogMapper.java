package com.workflow.process.audit.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 流程操作日志 Mapper
 */
@Mapper
public interface ProcessOperationLogMapper extends BaseMapper<ProcessOperationLog> {
}
