package com.workflow.admin.audit.application;

import com.workflow.integration.database.api.query.DatabaseQueryDialect;
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
    // 保留现有构造器依赖；普通查询的分页统一交由 MyBatis-Plus 处理。
    private final DatabaseQueryDialect queryDialect;

    /**
     * 分页查询系统审计查询；查询结果供调用方展示或继续处理。
     *
     * @param query 查询，作为 {@code Math.max} 的输入影响后续处理
     * @return 符合条件的系统操作日志结果，供调用方继续处理
     */
    public PageResult<SystemOperationLog> page(SystemAuditQuery query) {
        int pageNum = Math.max(1, query.getPageNum());
        int pageSize = Math.min(200, Math.max(1, query.getPageSize()));
        Page<SystemOperationLog> page = operationLogMapper.selectPage(
                new Page<>(pageNum, pageSize), wrapper(query));
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 读取必填；查询结果供调用方展示或继续处理。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 符合条件的系统操作日志结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    public SystemOperationLog getRequired(String id) {
        SystemOperationLog value = operationLogMapper.selectById(id);
        if (value == null) {
            throw new IllegalArgumentException("系统日志不存在");
        }
        return value;
    }

    /**
     * 导出仍最多读取一万条，使用数据库分页避免把全部日志载入内存。
     *
     * @param query 查询，供本方法处理导出时使用
     * @return 系统操作日志集合，供调用方遍历或展示
     */
    public List<SystemOperationLog> export(SystemAuditQuery query) {
        return operationLogMapper.selectPage(
                new Page<SystemOperationLog>(1, 10000, false), wrapper(query)).getRecords();
    }

    /**
     * 查询统一审计投影。返回前固定转换为敏感字段收敛视图，禁止直接把持久化
     * 对象交给统一时间线接口。
     *
     * @param query 查询，作为 {@code Math.max} 的输入影响后续处理
     * @return 处理后的统一分页结果，供调用方继续处理
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
     *
     * @param operationId 操作ID，后续用于处理操作{@code timeline}时定位或关联目标
     * @return 统一审计事件视图集合，供调用方遍历或展示
     */
    public List<UnifiedAuditEventView> operationTimeline(
            String operationId) {
        String normalized = requiredIdentifier(
                operationId, "operationId", 128);
        LambdaQueryWrapper<SystemOperationLog> wrapper =
                new LambdaQueryWrapper<>();
        applyOperationIdFilter(wrapper, normalized);
        return operationLogMapper.selectPage(new Page<SystemOperationLog>(1, 500, false), wrapper
                        .orderByAsc(SystemOperationLog::getCreateTime)
                        .orderByAsc(SystemOperationLog::getId))
                .getRecords().stream()
                .map(this::toUnifiedView)
                .toList();
    }

    /**
     * 处理{@code wrapper}，并将结果传给后续步骤。
     *
     * @param query 查询，作为 {@code wrapper.ge} 的输入影响后续处理
     * @return 处理后的{@code wrapper}结果，供调用方继续处理
     */
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
        return wrapper.orderByDesc(SystemOperationLog::getCreateTime).orderByDesc(SystemOperationLog::getId);
    }

    /**
     * 处理统一{@code wrapper}，并将结果传给后续步骤。
     *
     * @param query 查询，作为 {@code wrapper.ge} 的输入影响后续处理
     * @return 处理后的统一{@code wrapper}结果，供调用方继续处理
     */
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

    /**
     * 转换为统一视图；输出作为后续校验或处理的输入。
     *
     * @param value 待转换为统一视图的原始输入，结果供调用方继续使用
     * @return 转换为后的统一视图结果，供调用方继续处理
     */
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
     *
     * @param wrapper {@code wrapper}，供本方法应用操作ID过滤时使用
     * @param operationId 操作ID，后续用于应用操作ID过滤时定位或关联目标
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

    /**
     * 生成可选标识符文本，供后续匹配或展示。
     *
     * @param value 待处理可选标识符的原始输入，结果供调用方继续使用
     * @param name 名称，后续用于处理可选标识符时匹配或展示
     * @param maxLength 最大长度，作为 {@code requiredIdentifier} 的输入影响后续处理
     * @return 处理后的可选标识符文本，供调用方比较或展示
     */
    private String optionalIdentifier(
            String value,
            String name,
            int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return requiredIdentifier(value, name, maxLength);
    }

    /**
     * 生成必填标识符文本，供后续匹配或展示。
     *
     * @param value 待处理必填标识符的原始输入，结果供调用方继续使用
     * @param name 名称，后续用于处理必填标识符时匹配或展示
     * @param maxLength 最大长度，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @return 处理后的必填标识符文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 生成{@code upper}文本，供后续匹配或展示。
     *
     * @param value 待处理{@code upper}的原始输入，结果供调用方继续使用
     * @return 处理后的{@code upper}文本，供调用方比较或展示
     */
    private String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
