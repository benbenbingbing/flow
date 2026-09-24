package com.workflow.entity.version.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import org.springframework.util.StringUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 冻结范围与采集校验共用的持久化指纹协议。
 * 字段集合、空值、数组顺序和序列化方式必须保持兼容；修改协议需另做版本化。
 */
final class EntityVersionFingerprint {
    private EntityVersionFingerprint() {}

    /** 使用调用方已有的 ObjectMapper，保留既有模块配置及摘要字节。 */
    static String hash(ObjectMapper mapper, Object material) throws Exception {
        byte[] bytes = mapper.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .writeValueAsString(material).getBytes(StandardCharsets.UTF_8);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    /** 固定参与摘要的字段及 null 列表归一化规则，保持已入库 entitySchemaHash 可验证。 */
    static Map<String, Object> entitySchemaMaterial(EntityPublishedSnapshot snapshot) {
        Map<String, Object> material = new LinkedHashMap<>();
        material.put("historyId", snapshot.getHistoryId());
        material.put("entityId", snapshot.getEntityId());
        material.put("entityCode", snapshot.getEntityCode());
        material.put("version", snapshot.getVersion());
        material.put("fields", snapshot.getFields() == null ? List.of() : snapshot.getFields());
        material.put("relationsSnapshotAvailable",
                snapshot.isRelationsSnapshotAvailable());
        material.put("relations", snapshot.getRelations() == null ? List.of() : snapshot.getRelations());
        return material;
    }

    /** 只纳入决定遍历、归属和基数的服务端字段；不得加入展示属性而使旧范围失效。 */
    static Map<String, Object> relationDefinitionMaterial(
            EntityPublishedSnapshot parent,
            EntityPublishedSnapshot child,
            EntityRelation relation) {
        Map<String, Object> material = new LinkedHashMap<>();
        material.put("sourceEntityCode", parent.getEntityCode());
        material.put("sourceReleaseId", parent.getHistoryId());
        material.put("targetEntityCode", child.getEntityCode());
        material.put("targetReleaseId", child.getHistoryId());
        material.put("relationCode", relation.getRelationCode());
        material.put("dataKey", firstText(
                relation.getDataKey(), relation.getParentFieldCode(),
                relation.getRelationCode()));
        material.put("childRefFieldCode", relation.getChildRefFieldCode());
        material.put("relationType", relation.getRelationType() == null
                ? null : relation.getRelationType().name());
        material.put("ownershipType", relation.getOwnershipType() == null
                ? null : relation.getOwnershipType().name());
        material.put("cascadeDelete", relation.getCascadeDelete());
        material.put("required", relation.getRequired());
        material.put("enabled", relation.getEnabled());
        return material;
    }

    /** 按 dataKey、父字段、关系编码顺序回退，并沿用历史摘要中的 trim 规则。 */
    private static String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
