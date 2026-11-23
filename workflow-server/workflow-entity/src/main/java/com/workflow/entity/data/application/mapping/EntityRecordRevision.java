package com.workflow.entity.data.application.mapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 详情读取和写锁后的预检使用同一聚合指纹，兼容仅精确到秒的历史表列。 */
public final class EntityRecordRevision {
    private static final List<String> VERSION_FIELDS = List.of("id", "entityCode", "name", "code", "status",
            "processStatus", "processInstanceId", "deptId", "update_time", "update_by", "data");

    private EntityRecordRevision() { }

    public static String calculate(ObjectMapper json, Map<String, Object> record) {
        var material = new LinkedHashMap<String, Object>();
        VERSION_FIELDS.forEach(key -> material.put(key, record.get(key)));
        try {
            byte[] canonical = json.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writeValueAsBytes(material);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (JsonProcessingException | NoSuchAlgorithmException error) {
            throw new IllegalStateException("无法生成实体记录修订标记", error);
        }
    }
}
