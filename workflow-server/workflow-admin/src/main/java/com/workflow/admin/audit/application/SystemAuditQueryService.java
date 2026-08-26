package com.workflow.admin.audit.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.core.result.PageResult;
import com.workflow.admin.audit.api.SystemAuditQuery;
import com.workflow.admin.audit.api.UnifiedAuditEventView;
import com.workflow.admin.audit.api.UnifiedAuditQuery;
import com.workflow.admin.audit.domain.SystemOperationLog;
import com.workflow.admin.audit.infrastructure.SystemOperationLogMapper;
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

    /**
     * 查询统一审计投影。返回前固定转换为敏感字段收敛视图，禁止直接把持久化
     * 对象交给统一时间线接口。
     */
    public PageResult<UnifiedAuditEventView> unifiedPage(
            UnifiedAuditQuery query) {
        int pageNum = Math.max(1, query.getPageNum());
        int pageSize = Math.min(200, Math.max(1, query.getPageSize()));
        Page<SystemOperationLog> page = operationLogMapper.selectPage(
                new Page<>(pageNum, pageSize), unifiedWrapper(query));
        return new PageResult<>(
                page.getRecords().stream()
                        .map(this::toUnifiedView)
                        .toList(),
                page.getTotal(),
                page.getCurrent(),
                page.getSize());
    }

    /**
     * 按 operationId 读取同一业务操作的时间线。历史记录未可靠携带 operationId
     * 时迁移会以 eventId 单独成组，不会根据 traceId 猜测合并。
     */
    public List<UnifiedAuditEventView> operationTimeline(
            String operationId) {
        String normalized = requiredIdentifier(
                operationId, "operationId", 128);
        LambdaQueryWrapper<SystemOperationLog> wrapper =
                new LambdaQueryWrapper<>();
        applyOperationIdFilter(wrapper, normalized);
        return operationLogMapper.selectList(wrapper
                        .orderByAsc(SystemOperationLog::getCreateTime)
                        .orderByAsc(SystemOperationLog::getId)
                        .last("LIMIT 500"))
                .stream()
                .map(this::toUnifiedView)
                .toList();
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

    private LambdaQueryWrapper<SystemOperationLog> unifiedWrapper(
            UnifiedAuditQuery query) {
        LambdaQueryWrapper<SystemOperationLog> wrapper =
                new LambdaQueryWrapper<>();
        wrapper.ge(query.getStartTime() != null,
                SystemOperationLog::getCreateTime, query.getStartTime());
        wrapper.le(query.getEndTime() != null,
                SystemOperationLog::getCreateTime, query.getEndTime());
        if (StringUtils.hasText(query.getOperationId())) {
            applyOperationIdFilter(
                    wrapper,
                    optionalIdentifier(query.getOperationId(),
                            "operationId", 128));
        }
        wrapper.eq(StringUtils.hasText(query.getTraceId()),
                SystemOperationLog::getTraceId,
                optionalIdentifier(query.getTraceId(), "traceId", 64));
        wrapper.eq(StringUtils.hasText(query.getModule()),
                SystemOperationLog::getModuleCode, upper(query.getModule()));
        wrapper.eq(StringUtils.hasText(query.getResult()),
                SystemOperationLog::getResult, upper(query.getResult()));
        wrapper.eq(StringUtils.hasText(query.getTargetType()),
                SystemOperationLog::getTargetType,
                optionalIdentifier(query.getTargetType(),
                        "targetType", 64));
        wrapper.eq(StringUtils.hasText(query.getTargetId()),
                SystemOperationLog::getTargetId,
                optionalIdentifier(query.getTargetId(), "targetId", 128));
        wrapper.eq(StringUtils.hasText(query.getSourceType()),
                SystemOperationLog::getSourceType,
                optionalIdentifier(query.getSourceType(),
                        "sourceType", 64));
        wrapper.eq(StringUtils.hasText(query.getSourceId()),
                SystemOperationLog::getSourceId,
                optionalIdentifier(query.getSourceId(), "sourceId", 128));
        return wrapper.orderByDesc(SystemOperationLog::getCreateTime)
                .orderByDesc(SystemOperationLog::getId);
    }

    private UnifiedAuditEventView toUnifiedView(
            SystemOperationLog value) {
        UnifiedAuditEventView.SourcePointer source =
                !StringUtils.hasText(value.getSourceSystem())
                        && !StringUtils.hasText(value.getSourceType())
                        && !StringUtils.hasText(value.getSourceId())
                        && !StringUtils.hasText(value.getSourceEventId())
                        ? null
                        : new UnifiedAuditEventView.SourcePointer(
                                value.getSourceSystem(),
                                value.getSourceType(),
                                value.getSourceId(),
                                value.getSourceEventId());
        return new UnifiedAuditEventView(
                value.getId(),
                value.getEventId(),
                // V064 是滚动升级的 Expand 迁移，旧 Pod 可能暂时写入 NULL。
                // 此类记录只能以自身 eventId 单独成组，不能借 traceId 合并。
                StringUtils.hasText(value.getOperationId())
                        ? value.getOperationId()
                        : value.getEventId(),
                value.getParentOperationId(),
                value.getTraceId(),
                value.getModuleCode(),
                value.getOperationCode(),
                value.getOperationName(),
                value.getResult(),
                value.getRiskLevel(),
                value.getOperatorId(),
                value.getOperatorName(),
                value.getTargetType(),
                value.getTargetId(),
                value.getTargetName(),
                value.getSummary(),
                value.getErrorCode(),
                value.getDurationMs(),
                source,
                true,
                value.getCreateTime());
    }

    /**
     * 精确匹配 operationId，同时兼容滚动升级期间旧 Pod 写入的空值。空值记录
     * 只允许通过自身 eventId 命中，避免把相同 traceId 的独立操作错误串联。
     */
    private void applyOperationIdFilter(
            LambdaQueryWrapper<SystemOperationLog> wrapper,
            String operationId) {
        wrapper.and(group -> group
                .eq(SystemOperationLog::getOperationId, operationId)
                .or(legacy -> legacy
                        .isNull(SystemOperationLog::getOperationId)
                        .eq(SystemOperationLog::getEventId, operationId))
                .or(legacy -> legacy
                        .eq(SystemOperationLog::getOperationId, "")
                        .eq(SystemOperationLog::getEventId, operationId)));
    }

    private String optionalIdentifier(
            String value,
            String name,
            int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return requiredIdentifier(value, name, maxLength);
    }

    private String requiredIdentifier(
            String value,
            String name,
            int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    name + " 长度不能超过 " + maxLength);
        }
        return normalized;
    }

    private String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
