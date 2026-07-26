package com.workflow.system.audit.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.common.PageResult;
import com.workflow.system.audit.api.SystemAuditQuery;
import com.workflow.system.audit.domain.SystemOperationLog;
import com.workflow.system.audit.infrastructure.SystemOperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 系统审计日志只读查询用例。
 */
@Service
@RequiredArgsConstructor
public class SystemAuditQueryService {

    private final SystemOperationLogMapper operationLogMapper;

    public PageResult<SystemOperationLog> page(SystemAuditQuery query) {
        int pageNum = Math.max(1, query.getPageNum());
        int pageSize = Math.min(200, Math.max(1, query.getPageSize()));
        Page<SystemOperationLog> page = operationLogMapper.selectPage(
                new Page<>(pageNum, pageSize), wrapper(query));
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public SystemOperationLog getRequired(String id) {
        SystemOperationLog value = operationLogMapper.selectById(id);
        if (value == null) {
            throw new IllegalArgumentException("系统日志不存在");
        }
        return value;
    }

    public List<SystemOperationLog> export(SystemAuditQuery query) {
        return operationLogMapper.selectList(
                wrapper(query).last("LIMIT 10000"));
    }

    private LambdaQueryWrapper<SystemOperationLog> wrapper(SystemAuditQuery query) {
        LambdaQueryWrapper<SystemOperationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(query.getStartTime() != null, SystemOperationLog::getCreateTime, query.getStartTime());
        wrapper.le(query.getEndTime() != null, SystemOperationLog::getCreateTime, query.getEndTime());
        wrapper.eq(StringUtils.hasText(query.getModule()),
                SystemOperationLog::getModuleCode, upper(query.getModule()));
        wrapper.eq(StringUtils.hasText(query.getOperation()),
                SystemOperationLog::getOperationCode, upper(query.getOperation()));
        wrapper.and(StringUtils.hasText(query.getOperator()), nested -> nested
                .eq(SystemOperationLog::getOperatorId, query.getOperator())
                .or()
                .like(SystemOperationLog::getOperatorName, query.getOperator()));
        wrapper.eq(StringUtils.hasText(query.getResult()),
                SystemOperationLog::getResult, upper(query.getResult()));
        wrapper.eq(StringUtils.hasText(query.getRiskLevel()),
                SystemOperationLog::getRiskLevel, upper(query.getRiskLevel()));
        wrapper.eq(StringUtils.hasText(query.getTargetType()),
                SystemOperationLog::getTargetType, query.getTargetType());
        wrapper.eq(StringUtils.hasText(query.getTargetId()),
                SystemOperationLog::getTargetId, query.getTargetId());
        wrapper.eq(StringUtils.hasText(query.getTraceId()),
                SystemOperationLog::getTraceId, query.getTraceId());
        return wrapper.orderByDesc(SystemOperationLog::getCreateTime);
    }

    private String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
