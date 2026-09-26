package com.workflow.entity.data.application;

import com.workflow.entity.data.application.model.EntityExportBatch;

import com.workflow.core.logging.LogValue;
import com.workflow.entity.list.application.EntityDataListConfigService;
import com.workflow.entity.list.application.EntityListPublishedRuntimeService;

import com.workflow.core.error.ForbiddenException;
import com.workflow.contracts.audit.model.AuditAction;
import com.workflow.contracts.audit.model.AuditModule;
import com.workflow.contracts.audit.model.AuditRiskLevel;
import com.workflow.contracts.audit.annotation.SystemAudit;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.api.request.EntityDataExportRequest;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListFieldMapper;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityPermissionAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletResponse;
import java.io.OutputStreamWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 实体数据导出服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityDataExportService {

    private static final int MAX_SELECTED_EXPORT_ROWS = 5_000;

    private final EntityDataListConfigService listConfigService;
    private final EntityListPublishedRuntimeService publishedRuntimeService;
    private final EntityListFieldMapper fieldMapper;
    private final EntityActionCapabilityService actionCapabilityService;

    /**
     * 导出实体数据为 CSV。
     *
     * <p>根据导出类型（SELECTED 或全部）服务端决定所需权限，禁止信任客户端传入的权限码，
     * 选中导出时逐条校验行内按钮权限，任一不可导出则整体拒绝。</p>
     *
     * @param entityCode 实体编码
     * @param request    导出请求
     * @param response   HTTP 响应，用于写入 CSV 流
     * @throws ForbiddenException 缺少导出权限或存在不可导出数据时抛出
     */
    @SystemAudit(
            module = AuditModule.ENTITY,
            action = AuditAction.EXPORT,
            operation = "导出实体数据",
            risk = AuditRiskLevel.HIGH,
            targetType = "ENTITY_RECORD",
            targetIdArg = 0)
    public void export(String entityCode, EntityDataExportRequest request, HttpServletResponse response) {
        if (request == null) {
            throw new IllegalArgumentException("导出请求不能为空");
        }
        boolean exportSelected = "SELECTED".equalsIgnoreCase(
                request.getExportType());
        if (exportSelected) {
            List<String> selectedIds = request.getIds() == null
                    ? List.of()
                    : request.getIds().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .toList();
            if (selectedIds.isEmpty()) {
                throw new IllegalArgumentException("请先选择需要导出的数据");
            }
            if (selectedIds.size() > MAX_SELECTED_EXPORT_ROWS) {
                throw new IllegalArgumentException(
                        "单次最多导出 " + MAX_SELECTED_EXPORT_ROWS + " 条选中数据");
            }
            request.setIds(selectedIds);
        }
        EntityListConfig config = listConfigService.findListConfig(
                entityCode,
                request.getListKey(),
                request.getReleaseId(),
                request.getReleaseVersion(),
                request.getReleaseResolutionToken());
        if (config == null
                && (StringUtils.hasText(request.getListKey())
                || StringUtils.hasText(request.getReleaseId())
                || request.getReleaseVersion() != null
                || StringUtils.hasText(
                        request.getReleaseResolutionToken()))) {
            throw new IllegalArgumentException(
                    "列表不存在或尚未发布: "
                            + request.getListKey());
        }
        // 1. 服务端根据导出类型决定权限，禁止信任客户端传入的权限码
        actionCapabilityService.requireStandardPermission(
                entityCode,
                exportSelected ? EntityPermissionAction.EXPORT : EntityPermissionAction.EXPORT_ALL);
        if (!exportSelected) {
            actionCapabilityService.requireToolbarActionForConfig(
                    entityCode,
                    config,
                    "exportAll");
        }

        // 2. 加载列表配置和字段
        List<EntityListField> listFields;
        if (config != null) {
            listFields = publishedRuntimeService.resolveFields(
                            config,
                            fieldMapper.findByListConfigId(config.getId()))
                    .stream()
                    .filter(f -> f.getShowInList() != null && f.getShowInList())
                    .sorted(Comparator.comparingInt(f -> f.getSortOrder() == null ? 0 : f.getSortOrder()))
                    .collect(Collectors.toList());
        } else {
            listFields = new ArrayList<>();
        }

        // 选中导出最多 5000 行，先读取并校验全部选中结果，拒绝时不向响应写入任何数据。
        // 全量导出只预读第一批，让配置/权限错误在写 CSV 之前返回，后续逐批写出并释放引用。
        EntityExportBatch firstBatch = exportSelected ? null : readBatch(entityCode, request, config, null);
        List<EntityDataDTO> records = exportSelected ? querySelectedData(entityCode, request, config) : List.of();
        if (exportSelected) {
            List<String> denied = records.stream()
                    .filter(record -> {
                        var capability = actionCapabilityService.evaluateRowActionForConfig(
                                entityCode,
                                config,
                                "exportSelected",
                                record);
                        return !capability.isVisible() || !capability.isEnabled();
                    })
                    .map(record -> StringUtils.hasText(record.getCode()) ? record.getCode() : record.getId())
                    .toList();
            if (!denied.isEmpty()) {
                throw new ForbiddenException("以下数据不允许导出：" + String.join("、", denied));
            }
        }

        // 4. 设置响应头
        String fileName = entityCode + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".csv";
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20"));

        // 5. 写入 CSV（UTF-8 BOM，方便 Excel 打开中文）
        try (OutputStreamWriter writer = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8)) {
            writer.write('\ufeff');

            // 表头
            List<String> headers = listFields.stream().map(EntityListField::getFieldName).collect(Collectors.toList());
            if (headers.isEmpty()) {
                headers = List.of("数据名称", "数据编码", "状态");
            }
            writer.write(joinCsvLine(headers));
            writer.write("\n");

            if (exportSelected) {
                writeRecords(writer, listFields, records);
            } else {
                EntityExportBatch batch = firstBatch;
                while (!batch.records().isEmpty()) {
                    writeRecords(writer, listFields, batch.records());
                    writer.flush();
                    if (batch.nextCursor() == null) break;
                    batch = readBatch(entityCode, request, config, batch.nextCursor());
                }
            }
            writer.flush();
        } catch (Exception e) {
            log.error("导出实体数据失败：entityCode={}, failureType={}",
                    LogValue.safe(entityCode), LogValue.failureType(e));
            throw new RuntimeException("导出失败：" + e.getMessage());
        }
    }

    /** 有界选中导出也通过 SQL 限制 ID，原有条件和权限保持 AND 关系。 */
    private List<EntityDataDTO> querySelectedData(String entityCode, EntityDataExportRequest request, EntityListConfig config) {
        List<EntityDataDTO> records = new ArrayList<>();
        EntityExportBatch.Cursor cursor = null;
        do {
            EntityExportBatch batch = readBatch(entityCode, request, config, cursor);
            if (batch.records().isEmpty()) break;
            records.addAll(batch.records());
            if (records.size() > MAX_SELECTED_EXPORT_ROWS) {
                throw new IllegalStateException("选中导出超过允许数量，请重试");
            }
            cursor = batch.nextCursor();
        } while (cursor != null);
        return records;
    }

    /** 每次只读取一批；游标没有前进时显式失败，避免异常 Provider 导致无限循环。 */
    private EntityExportBatch readBatch(String entityCode, EntityDataExportRequest request,
            EntityListConfig config, EntityExportBatch.Cursor cursor) {
        EntityExportBatch batch = listConfigService.findExportBatchWithResolvedConfig(entityCode,
                request.getListKey(), config, request.getCondition(),
                "SELECTED".equalsIgnoreCase(request.getExportType()) ? request.getIds() : null, cursor);
        if (!batch.records().isEmpty() && cursor != null && cursor.equals(batch.nextCursor())) {
            throw new IllegalStateException("导出游标未前进，请重试");
        }
        return batch;
    }

    /** 写入当前批次而不累积全部导出行，CSV 转义规则与原实现一致。 */
    private void writeRecords(OutputStreamWriter writer, List<EntityListField> listFields,
            List<EntityDataDTO> records) throws java.io.IOException {
        for (EntityDataDTO record : records) {
            List<String> values = new ArrayList<>();
            for (EntityListField field : listFields) {
                values.add(formatValue(getFieldValue(record, field.getFieldCode())));
            }
            if (listFields.isEmpty()) {
                values.add(formatValue(record.getName()));
                values.add(formatValue(record.getCode()));
                values.add(formatValue(record.getStatus()));
            }
            writer.write(joinCsvLine(values));
            writer.write("\n");
        }
    }

    /**
     * 读取字段值；查询结果供调用方展示或继续处理。
     *
     * @param record 记录，作为 {@code dtoField.get} 的输入影响后续处理
     * @param fieldCode 字段编码，后续用于读取字段值时定位或关联目标
     * @return 符合条件的实体数据导出结果，供调用方继续处理
     */
    private Object getFieldValue(EntityDataDTO record, String fieldCode) {
        if (record == null || !StringUtils.hasText(fieldCode)) {
            return null;
        }
        // 审计字段的外部编码就是物理列名，不能用编码反射 Java 驼峰属性。
        switch (fieldCode) {
            case "create_time": return record.getCreateTime();
            case "update_time": return record.getUpdateTime();
            case "create_by": return record.getCreateBy();
            case "update_by": return record.getUpdateBy();
            default: break;
        }
        // 1. 优先从 DTO 属性取值
        try {
            java.lang.reflect.Field dtoField = EntityDataDTO.class.getDeclaredField(fieldCode);
            dtoField.setAccessible(true);
            Object value = dtoField.get(record);
            if (value != null) {
                return value;
            }
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
        }
        // 2. 从 data 或 extData 取值
        Map<String, Object> data = record.getData();
        if (data != null && data.containsKey(fieldCode)) {
            return data.get(fieldCode);
        }
        Map<String, Object> extData = record.getExtData();
        if (extData != null && extData.containsKey(fieldCode)) {
            return extData.get(fieldCode);
        }
        return null;
    }

    /**
     * 格式化值；输出作为后续校验或处理的输入。
     *
     * @param value 待格式化值的原始输入，结果供调用方继续使用
     * @return 格式化后的值文本，供调用方比较或展示
     */
    private String formatValue(Object value) {
        if (value == null) {
            return "";
        }
        return value.toString();
    }

    /**
     * 生成{@code join}CSV{@code line}文本，供后续匹配或展示。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的{@code join}CSV{@code line}文本，供调用方比较或展示
     */
    private String joinCsvLine(List<String> values) {
        return values.stream()
                .map(v -> {
                    String s = v == null ? "" : v;
                    if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
                        s = s.replace("\"", "\"\"");
                        return "\"" + s + "\"";
                    }
                    return s;
                })
                .collect(Collectors.joining(","));
    }
}
