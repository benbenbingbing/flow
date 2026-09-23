package com.workflow.admin.externalsystem.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.admin.externalsystem.api.ExternalSystemErrorCode;
import com.workflow.admin.externalsystem.api.ExternalSystemManagementException;
import com.workflow.admin.externalsystem.api.request.ExternalSystemRequests;
import com.workflow.admin.externalsystem.api.response.ExternalSystemViews;
import com.workflow.admin.externalsystem.infrastructure.persistence.mapper.ExternalSystemMapper;
import com.workflow.admin.externalsystem.infrastructure.persistence.mapper.ExternalSystemParameterMapper;
import com.workflow.admin.externalsystem.infrastructure.persistence.record.ExternalSystemParameterRecord;
import com.workflow.admin.externalsystem.infrastructure.persistence.record.ExternalSystemRecord;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.audit.model.AuditAction;
import com.workflow.contracts.audit.model.AuditModule;
import com.workflow.contracts.audit.model.AuditRiskLevel;
import com.workflow.contracts.audit.annotation.SystemAudit;
import com.workflow.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 外部系统基本信息及自定义参数集合的管理服务。
 *
 * <p>外部系统与参数采用聚合方式保存：每次创建或更新都先完整校验请求，
 * 再在同一事务内写入父记录并替换全部活动参数。系统编码是永久业务标识，
 * 创建后既不可修改，也不能在逻辑删除后复用。</p>
 */
@Service
@RequiredArgsConstructor
public class ExternalSystemService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_SYSTEM_CODE_LENGTH = 100;
    private static final int MAX_SYSTEM_NAME_LENGTH = 100;
    private static final int MAX_ADDRESS_LENGTH = 500;
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final int MAX_PARAMETER_NAME_LENGTH = 100;
    private static final int MAX_PARAMETER_VALUE_LENGTH = 65535;
    private static final int MAX_PARAMETER_COUNT = 200;
    private static final String STATUS_ENABLED = "0";
    private static final String STATUS_DISABLED = "1";

    private final ExternalSystemMapper externalSystemMapper;
    private final ExternalSystemParameterMapper parameterMapper;

    /**
     * 分页查询外部系统摘要；名称和编码支持模糊查询，状态使用精确匹配。
     * 父记录分页和参数数量统计处于同一个只读事务快照中。
     *
     * @param pageNum 页码，小于 1 时按 1 处理
     * @param pageSize 每页条数，限制在 1 到 100
     * @param systemName 系统名称筛选，可空
     * @param systemCode 系统编码筛选，可空
     * @param status 状态筛选，可空，仅支持 0/1
     * @return 不包含参数值的分页摘要
     */
    @Transactional(readOnly = true)
    public PageResult<ExternalSystemViews.ExternalSystemSummary> page(
            int pageNum,
            int pageSize,
            String systemName,
            String systemCode,
            String status) {
        int safePage = Math.max(pageNum, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        String normalizedStatus = optionalStatus(status);
        LambdaQueryWrapper<ExternalSystemRecord> query =
                new LambdaQueryWrapper<ExternalSystemRecord>()
                        .like(StringUtils.hasText(systemName),
                                ExternalSystemRecord::getSystemName,
                                trimToNull(systemName))
                        .like(StringUtils.hasText(systemCode),
                                ExternalSystemRecord::getSystemCode,
                                trimToNull(systemCode))
                        .eq(StringUtils.hasText(normalizedStatus),
                                ExternalSystemRecord::getStatus,
                                normalizedStatus)
                        .orderByDesc(ExternalSystemRecord::getUpdateTime)
                        .orderByAsc(ExternalSystemRecord::getSystemCode);
        Page<ExternalSystemRecord> result = externalSystemMapper.selectPage(
                new Page<>(safePage, safeSize), query);

        Map<String, Long> parameterCounts = parameterCounts(
                result.getRecords());
        List<ExternalSystemViews.ExternalSystemSummary> records =
                result.getRecords().stream()
                        .map(item -> toSummary(
                                item,
                                parameterCounts.getOrDefault(
                                        item.getId(), 0L)))
                        .toList();
        return new PageResult<>(records, result.getTotal(),
                result.getCurrent(), result.getSize());
    }

    /**
     * 查询单个外部系统及其当前活动参数，父子两次查询共享只读事务快照。
     *
     * @param id 外部系统 ID
     * @return 外部系统详情
     * @throws ExternalSystemManagementException 记录不存在时抛出 404
     */
    @Transactional(readOnly = true)
    public ExternalSystemViews.ExternalSystemDetail get(String id) {
        ExternalSystemRecord externalSystem = StringUtils.hasText(id)
                ? externalSystemMapper.selectById(id)
                : null;
        if (externalSystem == null) {
            throw notFound();
        }
        return toDetail(
                externalSystem,
                parameterMapper.selectActiveByExternalSystemId(id));
    }

    /**
     * 创建外部系统并原子写入参数集合。
     *
     * <p>参数值只写数据库，不写业务日志，也不通过审计注解采集请求或结果。</p>
     *
     * @param request 创建请求
     * @return 创建后的完整详情及新参数 ID
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.SYSTEM,
            action = AuditAction.CREATE,
            operation = "创建外部系统配置",
            risk = AuditRiskLevel.HIGH,
            required = true,
            targetType = "SYS_EXTERNAL_SYSTEM",
            captureArguments = false,
            captureResult = false)
    public ExternalSystemViews.ExternalSystemDetail create(
            ExternalSystemRequests.CreateExternalSystem request) {
        if (request == null) {
            throw invalid("请求不能为空");
        }
        String systemCode = normalizeSystemCode(request.systemCode());
        String systemName = requiredTrimmed(
                request.systemName(), "系统名称", MAX_SYSTEM_NAME_LENGTH);
        String address = requiredTrimmed(
                request.address(), "系统地址", MAX_ADDRESS_LENGTH);
        String description = optionalTrimmed(
                request.description(), "描述", MAX_DESCRIPTION_LENGTH);
        String status = defaultCreateStatus(request.status());
        List<PreparedParameter> parameters = prepareParameters(
                request.parameters());
        if (externalSystemMapper.selectAnyByCode(systemCode) != null) {
            throw duplicatedCode(systemCode);
        }

        LocalDateTime now = now();
        String actor = requireActor();
        ExternalSystemRecord record = new ExternalSystemRecord();
        record.setSystemCode(systemCode);
        record.setSystemName(systemName);
        record.setStatus(status);
        record.setAddress(address);
        record.setDescription(description);
        record.setVersion(0L);
        record.setCreatedBy(actor);
        record.setUpdatedBy(actor);
        record.setCreateTime(now);
        record.setUpdateTime(now);
        record.setDeleted(0);
        try {
            externalSystemMapper.insert(record);
        } catch (DuplicateKeyException exception) {
            // 数据库唯一约束兜住并发创建，仍对外返回稳定的编码重复语义。
            throw duplicatedCode(systemCode);
        }
        List<ExternalSystemParameterRecord> savedParameters =
                insertParameters(record.getId(), parameters, actor, now);
        return toDetail(record, savedParameters);
    }

    /**
     * 更新可变基本信息并在同一事务内完整替换参数集合。
     *
     * <p>请求契约不包含系统编码，持久化 SQL 也不会更新 system_code。
     * 参数行先统一逻辑删除再重建，从而支持同一次保存中交换英文名；调用方
     * 不应依赖参数 ID 在两次聚合保存之间保持稳定。</p>
     *
     * @param id 外部系统 ID
     * @param request 更新请求，必须携带当前 expectedVersion
     * @return 更新后的完整详情及新参数 ID
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.SYSTEM,
            action = AuditAction.UPDATE,
            operation = "更新外部系统配置",
            risk = AuditRiskLevel.HIGH,
            required = true,
            targetType = "SYS_EXTERNAL_SYSTEM",
            targetIdArg = 0,
            captureArguments = false,
            captureResult = false)
    public ExternalSystemViews.ExternalSystemDetail update(
            String id,
            ExternalSystemRequests.UpdateExternalSystem request) {
        if (request == null) {
            throw invalid("请求不能为空");
        }
        String systemName = requiredTrimmed(
                request.systemName(), "系统名称", MAX_SYSTEM_NAME_LENGTH);
        String address = requiredTrimmed(
                request.address(), "系统地址", MAX_ADDRESS_LENGTH);
        String description = optionalTrimmed(
                request.description(), "描述", MAX_DESCRIPTION_LENGTH);
        String status = requiredStatus(request.status());
        List<PreparedParameter> parameters = prepareParameters(
                request.parameters());
        ExternalSystemRecord current = requireLocked(id);
        long expectedVersion = requireExpectedVersion(
                current, request.expectedVersion());
        LocalDateTime now = now();
        String actor = requireActor();

        int changed = externalSystemMapper.updateMutableFields(
                current.getId(), systemName, status, address, description,
                expectedVersion, actor, now);
        if (changed != 1) {
            throw versionConflict();
        }
        current.setSystemName(systemName);
        current.setStatus(status);
        current.setAddress(address);
        current.setDescription(description);
        current.setVersion(expectedVersion + 1);
        current.setUpdatedBy(actor);
        current.setUpdateTime(now);

        parameterMapper.softDeleteByExternalSystemId(
                current.getId(), actor, now);
        List<ExternalSystemParameterRecord> savedParameters =
                insertParameters(current.getId(), parameters, actor, now);
        return toDetail(current, savedParameters);
    }

    /**
     * 启用或禁用外部系统，不改变其参数集合。
     *
     * @param id 外部系统 ID
     * @param request 状态请求，仅支持 0/1，并携带当前 expectedVersion
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.SYSTEM,
            action = AuditAction.CONFIGURE,
            operation = "更新外部系统状态",
            risk = AuditRiskLevel.MEDIUM,
            required = true,
            targetType = "SYS_EXTERNAL_SYSTEM",
            targetIdArg = 0,
            captureArguments = false,
            captureResult = false)
    public void changeStatus(
            String id,
            ExternalSystemRequests.ChangeExternalSystemStatus request) {
        if (request == null) {
            throw invalid("请求不能为空");
        }
        ExternalSystemRecord current = requireLocked(id);
        long expectedVersion = requireExpectedVersion(
                current, request.expectedVersion());
        String status = requiredStatus(request.status());
        LocalDateTime now = now();
        if (externalSystemMapper.updateStatus(
                current.getId(), status, expectedVersion,
                requireActor(), now) != 1) {
            throw versionConflict();
        }
    }

    /**
     * 逻辑删除外部系统，并在删除父记录前逻辑删除其全部活动参数。
     *
     * @param id 外部系统 ID
     * @param request 删除请求，必须携带当前 expectedVersion
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.SYSTEM,
            action = AuditAction.DELETE,
            operation = "删除外部系统配置",
            risk = AuditRiskLevel.HIGH,
            required = true,
            targetType = "SYS_EXTERNAL_SYSTEM",
            targetIdArg = 0,
            captureArguments = false,
            captureResult = false)
    public void delete(
            String id,
            ExternalSystemRequests.DeleteExternalSystem request) {
        if (request == null) {
            throw invalid("请求不能为空");
        }
        ExternalSystemRecord current = requireLocked(id);
        long expectedVersion = requireExpectedVersion(
                current, request.expectedVersion());
        LocalDateTime now = now();
        String actor = requireActor();
        // 先释放活动参数唯一槽位，再删除父记录；异常时由事务整体回滚。
        parameterMapper.softDeleteByExternalSystemId(
                current.getId(), actor, now);
        if (externalSystemMapper.softDelete(
                current.getId(), expectedVersion, actor, now) != 1) {
            throw versionConflict();
        }
    }

    /**
     * 完整校验参数集合并构建不可变的待保存对象。
     *
     * <p>英文名按小写归一后判重，与数据库活动态唯一约束保持一致；参数值
     * 只用 hasText 判断非空，但保存原字符串，避免破坏模板、签名片段或空白。</p>
     *
     * @param inputs {@code inputs}，供本方法准备参数集合时使用
     * @return 已准备参数集合，供调用方遍历或展示
     */
    private List<PreparedParameter> prepareParameters(
            List<ExternalSystemRequests.ParameterInput> inputs) {
        List<ExternalSystemRequests.ParameterInput> safeInputs =
                inputs == null ? List.of() : inputs;
        if (safeInputs.size() > MAX_PARAMETER_COUNT) {
            throw invalid("单个外部系统最多配置 "
                    + MAX_PARAMETER_COUNT + " 个参数");
        }
        Set<String> normalizedNames = new HashSet<>();
        java.util.ArrayList<PreparedParameter> result =
                new java.util.ArrayList<>(safeInputs.size());
        for (int index = 0; index < safeInputs.size(); index++) {
            ExternalSystemRequests.ParameterInput input = safeInputs.get(index);
            if (input == null) {
                throw invalid("第 " + (index + 1) + " 个参数不能为空");
            }
            String nameZh = requiredTrimmed(
                    input.nameZh(), "参数中文名", MAX_PARAMETER_NAME_LENGTH);
            String nameEn = requiredTrimmed(
                    input.nameEn(), "参数英文名", MAX_PARAMETER_NAME_LENGTH);
            if (!nameEn.matches("[A-Za-z][A-Za-z0-9_.-]{0,99}")) {
                throw invalid("参数英文名必须以字母开头，且只能包含字母、数字、下划线、点或连字符");
            }
            String normalizedName = nameEn.toLowerCase(Locale.ROOT);
            if (!normalizedNames.add(normalizedName)) {
                throw new ExternalSystemManagementException(
                        409,
                        ExternalSystemErrorCode
                                .EXTERNAL_SYSTEM_PARAMETER_NAME_DUPLICATED,
                        "同一外部系统的参数英文名不能重复: " + nameEn);
            }
            String value = input.value();
            if (!StringUtils.hasText(value)) {
                throw invalid("参数值不能为空: " + nameEn);
            }
            if (value.length() > MAX_PARAMETER_VALUE_LENGTH) {
                throw invalid("参数值长度不能超过 "
                        + MAX_PARAMETER_VALUE_LENGTH + " 个字符: " + nameEn);
            }
            int sortOrder = input.sortOrder() == null
                    ? (index + 1) * 10
                    : input.sortOrder();
            if (sortOrder < 0) {
                throw invalid("参数排序不能小于 0: " + nameEn);
            }
            result.add(new PreparedParameter(
                    nameZh, nameEn, value, sortOrder));
        }
        return List.copyOf(result);
    }

    /**
     * 将校验后的参数逐行写入；不记录或转换 parameterValue。
     *
     * @param externalSystemId 外部系统ID，后续用于插入参数集合时定位或关联目标
     * @param parameters 参数集合，供本方法插入参数集合时使用
     * @param actor 操作人，作为 {@code record.setCreatedBy} 的输入影响后续处理
     * @param now 当前时间，作为 {@code record.setCreateTime} 的输入影响后续处理
     * @return 外部系统参数集合，供调用方遍历或展示
     */
    private List<ExternalSystemParameterRecord> insertParameters(
            String externalSystemId,
            List<PreparedParameter> parameters,
            String actor,
            LocalDateTime now) {
        java.util.ArrayList<ExternalSystemParameterRecord> result =
                new java.util.ArrayList<>(parameters.size());
        for (PreparedParameter parameter : parameters) {
            ExternalSystemParameterRecord record =
                    new ExternalSystemParameterRecord();
            record.setExternalSystemId(externalSystemId);
            record.setParameterNameZh(parameter.nameZh());
            record.setParameterNameEn(parameter.nameEn());
            record.setParameterValue(parameter.value());
            record.setSortOrder(parameter.sortOrder());
            record.setCreatedBy(actor);
            record.setUpdatedBy(actor);
            record.setCreateTime(now);
            record.setUpdateTime(now);
            record.setDeleted(0);
            try {
                parameterMapper.insert(record);
            } catch (DuplicateKeyException exception) {
                // 数据库排序规则比 Java 小写判重覆盖更广，由唯一约束统一兜住等价名称。
                throw duplicatedParameterName(parameter.nameEn());
            }
            result.add(record);
        }
        return List.copyOf(result);
    }

    /**
     * 批量读取参数数量；空分页不调用带 IN 条件的 Mapper。
     *
     * @param records 记录集合，供本方法处理参数{@code counts}时使用
     * @return 参数{@code counts}键值结果，供调用方继续处理
     */
    private Map<String, Long> parameterCounts(
            List<ExternalSystemRecord> records) {
        if (records == null || records.isEmpty()) {
            return Map.of();
        }
        List<String> ids = records.stream()
                .map(ExternalSystemRecord::getId)
                .filter(StringUtils::hasText)
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> counts = new HashMap<>();
        for (ExternalSystemParameterMapper.ExternalSystemParameterCount row
                : parameterMapper.countActiveByExternalSystemIds(ids)) {
            if (row != null && StringUtils.hasText(row.getExternalSystemId())) {
                counts.put(row.getExternalSystemId(), row.getParameterCount());
            }
        }
        return counts;
    }

    /**
     * 校验并获取已锁定；不满足约束时阻止后续处理。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 校验并获取后的已锁定结果，供调用方继续处理
     */
    private ExternalSystemRecord requireLocked(String id) {
        ExternalSystemRecord current = StringUtils.hasText(id)
                ? externalSystemMapper.selectForUpdate(id)
                : null;
        if (current == null) {
            throw notFound();
        }
        return current;
    }

    /**
     * 转换为摘要；输出作为后续校验或处理的输入。
     *
     * @param record 记录，作为 {@code ExternalSystemViews.ExternalSystemSummary} 的输入影响后续处理
     * @param parameterCount 参数数量，供本方法转换为摘要时使用
     * @return 转换为后的摘要结果，供调用方继续处理
     */
    private ExternalSystemViews.ExternalSystemSummary toSummary(
            ExternalSystemRecord record,
            long parameterCount) {
        return new ExternalSystemViews.ExternalSystemSummary(
                record.getId(),
                record.getSystemCode(),
                record.getSystemName(),
                record.getStatus(),
                record.getAddress(),
                record.getDescription(),
                record.getVersion(),
                Math.toIntExact(parameterCount),
                record.getCreatedBy(),
                record.getUpdatedBy(),
                toInstant(record.getCreateTime()),
                toInstant(record.getUpdateTime()));
    }

    /**
     * 转换为详情；输出作为后续校验或处理的输入。
     *
     * @param record 记录，作为 {@code ExternalSystemViews.ExternalSystemDetail} 的输入影响后续处理
     * @param parameters 参数集合，供本方法转换为详情时使用
     * @return 转换为后的详情结果，供调用方继续处理
     */
    private ExternalSystemViews.ExternalSystemDetail toDetail(
            ExternalSystemRecord record,
            List<ExternalSystemParameterRecord> parameters) {
        List<ExternalSystemViews.ExternalSystemParameterView> views =
                parameters == null ? List.of() : parameters.stream()
                        .map(this::toParameterView)
                        .toList();
        return new ExternalSystemViews.ExternalSystemDetail(
                record.getId(),
                record.getSystemCode(),
                record.getSystemName(),
                record.getStatus(),
                record.getAddress(),
                record.getDescription(),
                record.getVersion(),
                views,
                record.getCreatedBy(),
                record.getUpdatedBy(),
                toInstant(record.getCreateTime()),
                toInstant(record.getUpdateTime()));
    }

    /**
     * 转换为参数视图；输出作为后续校验或处理的输入。
     *
     * @param record 记录，作为 {@code ExternalSystemViews.ExternalSystemParameterView} 的输入影响后续处理
     * @return 转换为后的参数视图结果，供调用方继续处理
     */
    private ExternalSystemViews.ExternalSystemParameterView toParameterView(
            ExternalSystemParameterRecord record) {
        return new ExternalSystemViews.ExternalSystemParameterView(
                record.getId(),
                record.getParameterNameZh(),
                record.getParameterNameEn(),
                record.getParameterValue(),
                record.getSortOrder() == null ? 0 : record.getSortOrder());
    }

    /**
     * 规范化系统编码；输出作为后续校验或处理的输入。
     *
     * @param value 待规范化系统编码的原始输入，结果供调用方继续使用
     * @return 规范化后的系统编码文本，供调用方比较或展示
     */
    private String normalizeSystemCode(String value) {
        String code = requiredTrimmed(
                value, "系统编码", MAX_SYSTEM_CODE_LENGTH);
        if (!code.matches("[A-Za-z][A-Za-z0-9_.-]{0,99}")) {
            throw invalid("系统编码必须以字母开头，且只能包含字母、数字、下划线、点或连字符");
        }
        return code;
    }

    /**
     * 生成默认创建状态文本，供后续匹配或展示。
     *
     * @param status 状态标识，决定后续默认创建状态采用的处理分支
     * @return 处理后的默认创建状态文本，供调用方比较或展示
     */
    private String defaultCreateStatus(String status) {
        return StringUtils.hasText(status)
                ? requiredStatus(status)
                : STATUS_ENABLED;
    }

    /**
     * 生成可选状态文本，供后续匹配或展示。
     *
     * @param status 状态标识，决定后续可选状态采用的处理分支
     * @return 处理后的可选状态文本，供调用方比较或展示
     */
    private String optionalStatus(String status) {
        return StringUtils.hasText(status) ? requiredStatus(status) : null;
    }

    /**
     * 生成必填状态文本，供后续匹配或展示。
     *
     * @param status 状态标识，决定后续必填状态采用的处理分支
     * @return 处理后的必填状态文本，供调用方比较或展示
     */
    private String requiredStatus(String status) {
        String normalized = requiredTrimmed(status, "状态", 1);
        if (!STATUS_ENABLED.equals(normalized)
                && !STATUS_DISABLED.equals(normalized)) {
            throw invalid("状态仅支持 0（启用）或 1（禁用）");
        }
        return normalized;
    }

    /**
     * 生成必填{@code trimmed}文本，供后续匹配或展示。
     *
     * @param value 待处理必填{@code trimmed}的原始输入，结果供调用方继续使用
     * @param label 标签，后续用于处理必填{@code trimmed}时匹配或展示
     * @param maxLength 最大长度，作为 {@code invalid} 的输入影响后续处理
     * @return 处理后的必填{@code trimmed}文本，供调用方比较或展示
     */
    private String requiredTrimmed(
            String value,
            String label,
            int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw invalid(label + "不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw invalid(label + "长度不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    /**
     * 生成可选{@code trimmed}文本，供后续匹配或展示。
     *
     * @param value 待处理可选{@code trimmed}的原始输入，结果供调用方继续使用
     * @param label 标签，后续用于处理可选{@code trimmed}时匹配或展示
     * @param maxLength 最大长度，作为 {@code invalid} 的输入影响后续处理
     * @return 处理后的可选{@code trimmed}文本，供调用方比较或展示
     */
    private String optionalTrimmed(
            String value,
            String label,
            int maxLength) {
        String normalized = trimToNull(value);
        if (normalized != null && normalized.length() > maxLength) {
            throw invalid(label + "长度不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    /**
     * 去除文本首尾空白，并将空白结果转为 null 供后续缺失值判断。
     *
     * @param value 待清理截止空值的原始输入，结果供调用方继续使用
     * @return 清理后的截止空值文本，供调用方比较或展示
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 校验并获取操作人；不满足约束时阻止后续处理。
     *
     * @return 校验并获取后的操作人文本，供调用方比较或展示
     */
    private String requireActor() {
        String actor = UserContext.getUserId();
        if (!StringUtils.hasText(actor)) {
            throw new ExternalSystemManagementException(
                    403,
                    ExternalSystemErrorCode.EXTERNAL_SYSTEM_NOT_AUTHENTICATED,
                    "用户未登录");
        }
        return actor;
    }

    /**
     * 处理当前时间，并将结果传给后续步骤。
     *
     * @return 处理后的当前时间结果，供调用方继续处理
     */
    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /**
     * 转换为绝对时间；输出作为后续校验或处理的输入。
     *
     * @param value 待转换为绝对时间的原始输入，结果供调用方继续使用
     * @return 转换为后的绝对时间结果，供调用方继续处理
     */
    private java.time.Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    /**
     * 构造无效输入异常，阻止后续业务处理。
     *
     * @param message 消息，作为 {@code ExternalSystemManagementException} 的输入影响后续处理
     * @return 处理后的无效结果，供调用方继续处理
     */
    private ExternalSystemManagementException invalid(String message) {
        return new ExternalSystemManagementException(
                400,
                ExternalSystemErrorCode.EXTERNAL_SYSTEM_INVALID_REQUEST,
                message);
    }

    /**
     * 构造目标不存在异常，供调用方终止后续处理。
     *
     * @return 处理后的非已找到结果，供调用方继续处理
     */
    private ExternalSystemManagementException notFound() {
        return new ExternalSystemManagementException(
                404,
                ExternalSystemErrorCode.EXTERNAL_SYSTEM_NOT_FOUND,
                "外部系统不存在");
    }

    /**
     * 构造{@code duplicated}编码异常，供调用方区分失败原因。
     *
     * @param systemCode 系统编码，后续用于处理{@code duplicated}编码时定位或关联目标
     * @return 处理后的{@code duplicated}编码结果，供调用方继续处理
     */
    private ExternalSystemManagementException duplicatedCode(
            String systemCode) {
        return new ExternalSystemManagementException(
                409,
                ExternalSystemErrorCode.EXTERNAL_SYSTEM_CODE_DUPLICATED,
                "系统编码已存在且不可复用: " + systemCode);
    }

    /**
     * 构造{@code duplicated}参数名称异常，供调用方区分失败原因。
     *
     * @param nameEn 名称{@code en}，供本方法处理{@code duplicated}参数名称时使用
     * @return 处理后的{@code duplicated}参数名称结果，供调用方继续处理
     */
    private ExternalSystemManagementException duplicatedParameterName(
            String nameEn) {
        return new ExternalSystemManagementException(
                409,
                ExternalSystemErrorCode
                        .EXTERNAL_SYSTEM_PARAMETER_NAME_DUPLICATED,
                "同一外部系统的参数英文名不能重复: " + nameEn);
    }

    /**
     * 锁定父记录后校验调用方看到的版本，避免旧页面覆盖新配置。
     *
     * @param current 已通过 SELECT FOR UPDATE 锁定的父记录
     * @param expectedVersion 调用方读取详情或列表时获得的版本
     * @return 经校验的期望版本
     * @throws ExternalSystemManagementException 版本缺失或不匹配时抛出 409
     */
    private long requireExpectedVersion(
            ExternalSystemRecord current,
            Long expectedVersion) {
        if (expectedVersion == null
                || expectedVersion < 0
                || current.getVersion() == null
                || current.getVersion().longValue()
                != expectedVersion.longValue()) {
            throw versionConflict();
        }
        return expectedVersion;
    }

    /**
     * 构造版本冲突异常，供调用方区分失败原因。
     *
     * @return 处理后的版本冲突结果，供调用方继续处理
     */
    private ExternalSystemManagementException versionConflict() {
        return new ExternalSystemManagementException(
                409,
                ExternalSystemErrorCode.EXTERNAL_SYSTEM_VERSION_CONFLICT,
                "外部系统已被其他管理员修改，请刷新后重试");
    }

    /**
     * 封装已准备参数的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param nameZh 名称{@code zh}，保存在对象中供后续校验、查询或展示
     * @param nameEn 名称{@code en}，保存在对象中供后续校验、查询或展示
     * @param value 待处理已准备参数的原始输入，结果供调用方继续使用
     * @param sortOrder 排序权重，后续用于稳定展示顺序
     */
    private record PreparedParameter(
            String nameZh,
            String nameEn,
            String value,
            int sortOrder) {
    }
}
