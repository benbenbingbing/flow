package com.workflow.admin.audit.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.admin.audit.domain.SystemOperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统操作日志持久化适配器。
 */
@Mapper
public interface SystemOperationLogMapper extends BaseMapper<SystemOperationLog> {
}
