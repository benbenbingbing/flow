package com.workflow.entity.ui.application;

import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiDataSourceDefinitionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * Validates data-source references embedded in publish snapshots.
 */
@Component
@RequiredArgsConstructor
public class UiConfigDataSourceReferenceValidator {

    private final UiDataSourceDefinitionMapper dataSourceMapper;
    private final JsonDocumentCodec codec;

    public void validate(Map<String, Object> snapshot) {
        String document = codec.write(
                snapshot,
                "待发布UI配置");
        if (document.contains("\"sourceType\":\"SQL\"")
                || document.contains("\"sourceType\":\"SCRIPT\"")
                || document.contains("\"sourceType\":\"URL\"")
                || document.contains("\"sql\":")
                || document.contains("\"script\":")
                || document.contains("\"url\":")) {
            throw new IllegalArgumentException(
                    "发布配置禁止包含任意 SQL、脚本或外网 URL 数据源");
        }
        validateValue(snapshot, "$");
    }

    private void validateValue(Object value, String path) {
        if (value instanceof Map<?, ?> map) {
            Object sourceId = map.get("sourceId");
            if (sourceId != null
                    && StringUtils.hasText(
                            String.valueOf(sourceId))) {
                String id = String.valueOf(sourceId);
                var definition =
                        dataSourceMapper.selectById(id);
                if (definition == null
                        || !Boolean.TRUE.equals(
                                definition.getEnabled())
                        || Integer.valueOf(1).equals(
                                definition.getDeleted())) {
                    throw new IllegalArgumentException(
                            "发布配置引用的数据源不存在或未启用: "
                                    + path
                                    + ".sourceId="
                                    + id);
                }
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                validateValue(
                        entry.getValue(),
                        path + "." + entry.getKey());
            }
        } else if (value instanceof List<?> list) {
            for (int index = 0;
                    index < list.size();
                    index++) {
                validateValue(
                        list.get(index),
                        path + "[" + index + "]");
            }
        }
    }
}
