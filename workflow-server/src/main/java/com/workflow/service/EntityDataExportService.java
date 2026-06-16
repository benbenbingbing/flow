package com.workflow.service;

import com.workflow.common.PermissionUtil;
import com.workflow.dto.EntityDataDTO;
import com.workflow.dto.EntityDataExportRequest;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityListConfig;
import com.workflow.entity.EntityListField;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityListConfigMapper;
import com.workflow.mapper.EntityListFieldMapper;
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

    private final EntityDataListConfigService listConfigService;
    private final EntityListConfigMapper listConfigMapper;
    private final EntityListFieldMapper fieldMapper;
    private final EntityDefinitionMapper definitionMapper;

    /**
     * 导出实体数据
     */
    public void export(String entityCode, EntityDataExportRequest request, HttpServletResponse response) {
        // 1. 权限校验（仅当按钮配置了权限码时才校验）
        String perm = request.getPerm();
        if (StringUtils.hasText(perm)) {
            PermissionUtil.checkPermission(perm);
        }

        // 2. 加载列表配置和字段
        EntityDefinition definition = definitionMapper.findByEntityCode(entityCode).orElse(null);
        if (definition == null) {
            throw new RuntimeException("实体不存在：" + entityCode);
        }

        EntityListConfig config = findListConfig(definition.getId(), request.getListKey());
        List<EntityListField> listFields;
        if (config != null) {
            listFields = fieldMapper.findByListConfigId(config.getId()).stream()
                    .filter(f -> f.getShowInList() != null && f.getShowInList())
                    .sorted(Comparator.comparingInt(f -> f.getSortOrder() == null ? 0 : f.getSortOrder()))
                    .collect(Collectors.toList());
        } else {
            listFields = new ArrayList<>();
        }

        // 3. 查询数据
        List<EntityDataDTO> records = queryData(entityCode, request);

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

            // 数据行
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
            writer.flush();
        } catch (Exception e) {
            log.error("导出实体数据失败：entityCode={}", entityCode, e);
            throw new RuntimeException("导出失败：" + e.getMessage());
        }
    }

    private List<EntityDataDTO> queryData(String entityCode, EntityDataExportRequest request) {
        List<EntityDataDTO> allRecords = listConfigService.findListWithConfig(entityCode, request.getListKey(), request.getCondition());
        if ("SELECTED".equalsIgnoreCase(request.getExportType()) && request.getIds() != null && !request.getIds().isEmpty()) {
            return allRecords.stream()
                    .filter(r -> request.getIds().contains(r.getId()))
                    .collect(Collectors.toList());
        }
        return allRecords;
    }

    private EntityListConfig findListConfig(String entityId, String listKey) {
        if (StringUtils.hasText(listKey)) {
            return listConfigMapper.findByEntityIdAndListKey(entityId, listKey);
        }
        List<EntityListConfig> configs = listConfigMapper.findByEntityId(entityId);
        return configs.stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsDefault()))
                .findFirst()
                .orElse(configs.isEmpty() ? null : configs.get(0));
    }

    private Object getFieldValue(EntityDataDTO record, String fieldCode) {
        if (record == null || !StringUtils.hasText(fieldCode)) {
            return null;
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

    private String formatValue(Object value) {
        if (value == null) {
            return "";
        }
        return value.toString();
    }

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
